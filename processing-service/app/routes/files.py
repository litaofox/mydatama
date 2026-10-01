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
    return ok({"total": total, "list": rows})


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
    job = db.query_one(
        "SELECT id AS job_id, stage, status, metrics, mask_columns, error_msg, retry_count, updated_at "
        "FROM proc.job_queue WHERE file_id=%s AND deleted=0 ORDER BY id DESC LIMIT 1",
        (file_id,),
    )
    data = {**f, "meta": f.get("meta"), "latestJob": job, "quarantineCount": _quarantine_count(f)}
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
    job = db.query_one(
        "SELECT id, stage, status, retry_count, metrics, mask_columns, error_msg, updated_at "
        "FROM proc.job_queue WHERE file_id=%s AND deleted=0 ORDER BY id DESC LIMIT 1",
        (file_id,),
    )
    if not job:
        raise ApiError(ERR_NOT_FOUND, "该文件暂无处理任务")
    return ok({
        "jobId": job["id"], "stage": job["stage"], "status": job["status"],
        "retryCount": job["retry_count"], "metrics": job.get("metrics"),
        "maskColumns": job.get("mask_columns"), "error": job.get("error_msg"),
        "updatedAt": job.get("updated_at"),
    })


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
