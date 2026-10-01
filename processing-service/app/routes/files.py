"""文件相关路由：上传 / 列表 / 详情 / 修复 / 任务状态 / 下载 / 缩略图。

说明：列表/详情 SQL 的 WHERE 条件片段全部为代码内固定常量，用户输入一律走 %s 参数，
不存在字符串拼接用户值。
"""
import logging
import os
import re
import uuid
from datetime import datetime

from fastapi import APIRouter, Depends, File, Form, Query, Request, UploadFile
from fastapi.responses import FileResponse

from app import config, db
from app.callbacks import check_authz, notify_audit
from app.errors import (ApiError, ERR_BAD_STATUS, ERR_FILE_DELETED, ERR_FILE_TOO_LARGE,
                        ERR_FILE_TYPE, ERR_FORBIDDEN, ERR_INTERNAL, ERR_NOT_FOUND, ok)
from app.pipeline import structured
from app.security import actor_from_claims, require_auth

logger = logging.getLogger("routes.files")
router = APIRouter()

_SAFE_NAME_RE = re.compile(r'[\\/:*?"<>|\x00-\x1f]')
_FILE_SELECT = ("id, file_name, modality, format, raw_path, processed_path, thumb_path, "
                "size_bytes, meta, secret_level, biz_domain, status, created_at, updated_at, "
                "create_by, update_by, deleted")


def _sanitize_name(name: str) -> str:
    base = os.path.basename(name or "file")
    base = _SAFE_NAME_RE.sub("_", base).strip().strip(".")
    return base[:100] or "file"


def _client_ip(request: Request) -> str:
    xff = request.headers.get("x-forwarded-for", "")
    first = xff.split(",")[0].strip() if xff else ""
    return first or (request.client.host if request.client else "")


def _escape_like(kw: str) -> str:
    return kw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")


# 前端统一 camelCase 契约；DB 内任务成功态为 SUCCEEDED，前端使用 SUCCESS
_JOB_STATUS_OUT = {"SUCCEEDED": "SUCCESS"}


def _file_out(r: dict) -> dict:
    """ORM 行 → 前端 ProcFile/ProcFileDetail 契约（camelCase + 字段改名）。"""
    return {
        "id": r["id"],
        "name": r.get("file_name"),
        "modality": r.get("modality"),
        "format": r.get("format"),
        "bizDomain": r.get("biz_domain"),
        "sizeBytes": r.get("size_bytes"),
        "secretLevel": r.get("secret_level"),
        "status": r.get("status"),
        "createdAt": r.get("created_at"),
        "storagePath": r.get("raw_path"),
        "processedPath": r.get("processed_path"),
        "thumbPath": r.get("thumb_path"),
        "qualityMetrics": r.get("meta") or {},
    }


def _job_view(file_id: int) -> dict | None:
    """聚合该文件全部任务行：五阶段指标合并、脱敏列从 MASK 指标提取、状态名转前端契约。"""
    rows = db.query_all(
        "SELECT id, stage, status, metrics, mask_columns, error_msg, retry_count, updated_at "
        "FROM proc.job_queue WHERE file_id=%s AND deleted=0 ORDER BY id ASC",
        (file_id,),
    )
    if not rows:
        return None
    latest = rows[-1]
    metrics: dict = {}
    mask_cols: list[str] = []
    for row in rows:
        m = row.get("metrics") or {}
        metrics.update(m)
        mask_metric = m.get("MASK") if isinstance(m.get("MASK"), dict) else None
        if not mask_metric:
            mask_metric = m
        for item in mask_metric.get("maskColumns", []) or []:
            col = item.get("col") if isinstance(item, dict) else str(item)
            if col and col not in mask_cols:
                mask_cols.append(col)
        if row.get("mask_columns"):
            for col in row["mask_columns"]:
                if col not in mask_cols:
                    mask_cols.append(col)
    return {
        "jobId": latest["id"],
        "stage": latest.get("stage"),
        "status": _JOB_STATUS_OUT.get(latest.get("status"), latest.get("status")),
        "retryCount": latest.get("retry_count") or 0,
        "metrics": metrics,
        "maskColumns": mask_cols or None,
        "error": latest.get("error_msg"),
        "updatedAt": latest.get("updated_at"),
    }


def _get_file_or_raise(file_id: int) -> dict:
    f = db.query_one(f"SELECT {_FILE_SELECT} FROM proc.data_files WHERE id=%s", (file_id,))
    if not f:
        raise ApiError(ERR_NOT_FOUND, "文件不存在")
    if f.get("deleted"):
        raise ApiError(ERR_FILE_DELETED, "文件已删除")
    return f


# ---------- 1. 上传 ----------
@router.post("/api/processing/files")
def upload_file(request: Request,
                file: UploadFile = File(...),
                bizDomain: str = Form(...),
                secretLevel: int = Form(...),
                claims: dict = Depends(require_auth)) -> dict:
    actor = actor_from_claims(claims)
    biz_domain = (bizDomain or "").strip()
    if not biz_domain or len(biz_domain) > 64:
        raise ApiError(ERR_INTERNAL, "参数无效：bizDomain 必填且不超过 64 字符")
    if secretLevel not in (1, 2, 3, 4):
        raise ApiError(ERR_INTERNAL, "参数无效：secretLevel 取值 1~4")

    orig_name = file.filename or ""
    ext = orig_name.rsplit(".", 1)[-1].lower() if "." in orig_name else ""
    if ext not in config.ALLOWED_EXTS:
        raise ApiError(ERR_FILE_TYPE,
                       f"文件类型不允许: .{ext or '无扩展名'}，允许: "
                       "csv/json/xlsx/xls/txt/md/log/jpg/png/webp/mp4/mov/webm")
    modality = config.MODALITY_BY_EXT[ext]
    safe_name = _sanitize_name(orig_name)

    # 流式落盘（1MB 分块），边写边校验大小，超限立即终止
    os.makedirs(os.path.join(config.STORAGE_ROOT, "tmp"), exist_ok=True)
    tmp_path = os.path.join(config.STORAGE_ROOT, "tmp", f"upload_{uuid.uuid4().hex}.{ext}")
    size = 0
    try:
        with open(tmp_path, "wb") as out:
            while True:
                chunk = file.file.read(1 << 20)
                if not chunk:
                    break
                size += len(chunk)
                if size > config.MAX_FILE_SIZE_BYTES:
                    raise ApiError(ERR_FILE_TOO_LARGE,
                                   f"文件大小超出限制（最大 {config.MAX_FILE_SIZE_MB}MB）")
                out.write(chunk)

        row = db.execute_returning(
            "INSERT INTO proc.data_files "
            "(file_name, modality, format, size_bytes, secret_level, biz_domain, status, create_by, update_by) "
            "VALUES (%s, %s, %s, %s, %s, %s, 'UPLOADED', %s, %s) RETURNING id",
            (orig_name[:255], modality, ext, size, secretLevel, biz_domain, actor, actor),
        )
        file_id = row["id"]

        now = datetime.now()
        raw_rel = f"raw/{now:%Y}/{now:%m}/{now:%d}/{file_id}_{safe_name}"
        raw_abs = config.abs_path(raw_rel)
        os.makedirs(os.path.dirname(raw_abs), exist_ok=True)
        os.replace(tmp_path, raw_abs)
        db.execute("UPDATE proc.data_files SET raw_path=%s, size_bytes=%s, updated_at=now() WHERE id=%s",
                   (raw_rel, size, file_id))

        job = db.execute_returning(
            "INSERT INTO proc.job_queue (file_id, stage, status, create_by, update_by) "
            "VALUES (%s, 'COLLECT_VALIDATE', 'PENDING', %s, %s) RETURNING id",
            (file_id, actor, actor),
        )
        # 尽力回调审计，失败静默
        notify_audit(actor, "UPLOAD", resource=f"file:{file_id}",
                     detail={"fileId": file_id, "fileName": orig_name[:255],
                             "modality": modality, "fileSize": size},
                     ip=_client_ip(request))
        logger.info("上传成功 file=%s job=%s modality=%s size=%s", file_id, job["id"], modality, size)
        return ok({"fileId": file_id, "jobId": job["id"]})
    finally:
        if os.path.exists(tmp_path):
            try:
                os.remove(tmp_path)
            except OSError:
                pass


# ---------- 2. 分页列表 ----------
@router.get("/api/processing/files")
def list_files(page: int = Query(1, ge=1), size: int = Query(10, ge=1, le=100),
               modality: str = "", status: str = "", keyword: str = "",
               claims: dict = Depends(require_auth)) -> dict:
    where = ["deleted = 0"]
    params: list = []
    if modality:
        where.append("modality = %s")
        params.append(modality)
    if status:
        where.append("status = %s")
        params.append(status)
    if keyword:
        where.append("file_name ILIKE %s")
        params.append("%" + _escape_like(keyword) + "%")
    where_sql = " AND ".join(where)

    total = db.query_one(f"SELECT COUNT(*) AS n FROM proc.data_files WHERE {where_sql}",
                         tuple(params))["n"]
    rows = db.query_all(
        "SELECT id, file_name, modality, format, size_bytes, secret_level, biz_domain, "
        "status, created_at, create_by "
        f"FROM proc.data_files WHERE {where_sql} ORDER BY id DESC LIMIT %s OFFSET %s",
        tuple(params) + (size, (page - 1) * size),
    )
    return ok({"total": total, "list": [_file_out(r) for r in rows]})


# ---------- 3. 详情 ----------
def _quarantine_count(f: dict) -> int:
    path = os.path.join(config.quarantine_dir(), f"{f['id']}_badrows.csv")
    if os.path.isfile(path):
        try:
            with open(path, "r", encoding="utf-8") as fh:
                return max(0, sum(1 for _ in fh) - 1)  # 减去 header
        except OSError:
            pass
    meta = f.get("meta") or {}
    return int(meta.get("quarantineCount") or 0)


@router.get("/api/processing/files/{file_id}")
def get_file(file_id: int, claims: dict = Depends(require_auth)) -> dict:
    f = _get_file_or_raise(file_id)
    job_view = _job_view(file_id)
    data = _file_out(f)
    data["latestJob"] = job_view
    data["maskColumns"] = (job_view or {}).get("maskColumns") or []
    data["quarantineCount"] = _quarantine_count(f)
    return ok(data)


# ---------- 4. 一键修复 ----------
@router.post("/api/processing/files/{file_id}/repair")
def repair_file(file_id: int, claims: dict = Depends(require_auth)) -> dict:
    actor = actor_from_claims(claims)
    f = _get_file_or_raise(file_id)
    if f["status"] != "READY":
        raise ApiError(ERR_BAD_STATUS, "仅 READY 状态的文件支持一键修复")
    job_id = structured.repair(f, actor)
    notify_audit(actor, "RETRY", resource=f"file:{file_id}",
                 detail={"action": "repair", "jobId": job_id})
    return ok({"jobId": job_id})


# ---------- 5. 最新任务状态（前端轮询） ----------
@router.get("/api/processing/files/{file_id}/job")
def get_job(file_id: int, claims: dict = Depends(require_auth)) -> dict:
    _get_file_or_raise(file_id)
    view = _job_view(file_id)
    if not view:
        raise ApiError(ERR_NOT_FOUND, "该文件暂无处理任务")
    return ok(view)


# ---------- 7. 下载（ABAC 细粒度鉴权） ----------
@router.get("/api/processing/files/{file_id}/download")
def download_file(request: Request, file_id: int, variant: str = Query("raw"),
                  claims: dict = Depends(require_auth)):
    actor = actor_from_claims(claims)
    if variant not in ("raw", "processed"):
        raise ApiError(ERR_INTERNAL, "参数无效：variant 取值 raw|processed")
    f = _get_file_or_raise(file_id)
    rel = f["raw_path"] if variant == "raw" else f["processed_path"]
    if not rel:
        raise ApiError(ERR_NOT_FOUND, f"{variant} 文件尚未生成")

    allow, reason = check_authz(actor, "proc:file:download", {
        "asset.secretLevel": f["secret_level"],
        "asset.bizDomain": f.get("biz_domain"),
        "user.roles": claims.get("roles") or [],
        "env.clientIp": _client_ip(request),
        "env.requestTime": datetime.now().astimezone().isoformat(),
    })
    if not allow:
        raise ApiError(ERR_FORBIDDEN, f"无权限下载该文件: {reason}")

    abs_path = config.abs_path(rel)
    if not os.path.isfile(abs_path):
        raise ApiError(ERR_NOT_FOUND, "文件在存储中不存在")
    notify_audit(actor, "EXPORT", resource=f"file:{file_id}", detail={"variant": variant})
    if variant == "processed":
        stem, _ = os.path.splitext(f["file_name"])
        proc_ext = os.path.splitext(rel)[1].lstrip(".") or f["format"]
        return FileResponse(abs_path, filename=f"{stem}_processed.{proc_ext}")
    return FileResponse(abs_path, filename=f["file_name"])


# ---------- 8. 缩略图 ----------
@router.get("/api/processing/files/{file_id}/thumb")
def get_thumb(file_id: int, claims: dict = Depends(require_auth)):
    f = _get_file_or_raise(file_id)
    rel = f.get("thumb_path")
    if not rel:
        raise ApiError(ERR_NOT_FOUND, "该文件无缩略图（仅图像/视频支持）")
    abs_path = config.abs_path(rel)
    if not os.path.isfile(abs_path):
        raise ApiError(ERR_NOT_FOUND, "缩略图不存在")
    return FileResponse(abs_path, media_type="image/jpeg")
