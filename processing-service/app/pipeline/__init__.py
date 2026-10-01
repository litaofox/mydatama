"""流水线分派：按模态执行阶段；ANNOTATE_REGISTER 为各模态公共实现（回调 Java 登记）。"""
import logging
import time

import psycopg2.extras as pgjson

from app import db
from app.callbacks import RegisterError, register_asset
from app.errors import UnrecoverableError
from app.pipeline import image, structured, text, video

logger = logging.getLogger("pipeline")

STAGES = ["COLLECT_VALIDATE", "CLEAN", "STANDARDIZE", "MASK", "ANNOTATE_REGISTER"]

_HANDLERS = {"STRUCTURED": structured, "TEXT": text, "IMAGE": image, "VIDEO": video}

# register metrics 允许携带的键（对齐 API-COM-001 内部接口规格）
_METRIC_KEYS = [
    "rowCount", "columnCount", "missingRate", "duplicateRate",
    "formatConsistencyRate", "sensitiveColumnCount", "errorRowCount",
    "charCount", "validLineRate",
]


def build_register_payload(file_row: dict) -> dict:
    meta = file_row.get("meta") or {}
    metrics = {k: meta[k] for k in _METRIC_KEYS if k in meta}
    if "sensitiveHits" in meta:
        metrics["sensitiveHitCount"] = meta["sensitiveHits"]
    if meta.get("width") and meta.get("height"):
        metrics["resolution"] = f"{meta['width']}x{meta['height']}"
    if "hadExif" in meta:
        metrics["hasExif"] = bool(meta["hadExif"])
    for k in ("durationSec", "bitrateKbps"):
        if k in meta:
            metrics[k] = meta[k]

    payload = {
        "sourceFileId": file_row["id"],
        "fileName": file_row["file_name"],
        "modality": file_row["modality"],
        "format": file_row["format"],
        "bizDomain": file_row["biz_domain"],
        "secretLevel": file_row["secret_level"],
        "sizeBytes": int(file_row.get("size_bytes") or 0),
        "rawPath": "/data/" + file_row["raw_path"] if file_row.get("raw_path") else None,
        "processedPath": "/data/" + file_row["processed_path"] if file_row.get("processed_path") else None,
        "thumbPath": "/data/" + file_row["thumb_path"] if file_row.get("thumb_path") else None,
        "metrics": metrics,
        "lineage": {
            "relType": "DERIVED_FROM",
            "description": f"由原始 {file_row['format']} 文件五阶段流水线处理生成",
        },
    }
    if meta.get("columns") is not None:
        payload["columns"] = meta["columns"]
    return payload


def run_register(file_row: dict, actor: str) -> int | None:
    """执行登记回调（内部已指数退避重试 5 次，失败抛 RegisterError → 任务终态 FAILED）。"""
    payload = build_register_payload(file_row)
    asset_id = register_asset(payload)
    if asset_id:
        meta = file_row.get("meta") or {}
        meta["assetId"] = asset_id
        db.execute(
            "UPDATE proc.data_files SET meta=%s, update_by=%s, updated_at=now() WHERE id=%s",
            (pgjson.Json(meta), actor, file_row["id"]),
        )
    return asset_id


def dispatch_stage(modality: str, stage: str, ctx: dict) -> dict:
    if stage == "ANNOTATE_REGISTER":
        t0 = time.monotonic()
        asset_id = run_register(ctx["file"], ctx["actor"])
        return {"ok": True, "ms": int((time.monotonic() - t0) * 1000), "assetId": asset_id}
    handler = _HANDLERS.get(modality)
    if handler is None:
        raise UnrecoverableError(f"未知模态: {modality}")
    return handler.run_stage(stage, ctx)


__all__ = ["STAGES", "dispatch_stage", "run_register", "build_register_payload", "RegisterError"]
