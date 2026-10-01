"""IMAGE 模态处理器（jpg / png / webp）。

阶段拆分：COLLECT_VALIDATE 打开并检测 EXIF/GPS；STANDARDIZE 重编码为无 EXIF 的
JPEG(quality=85)（通过新建 Image 对象丢弃元数据）；MASK 阶段生成缩略图（元数据
剥离已在重编码时完成）；ANNOTATE_REGISTER 由公共层回调登记。
"""
import logging
import os
import time

from PIL import Image
from psycopg2.extras import Json

from app import config, db
from app.errors import UnrecoverableError

logger = logging.getLogger("pipeline.image")

THUMB_MAX_EDGE = 320


def run_stage(stage: str, ctx: dict) -> dict:
    if stage == "COLLECT_VALIDATE":
        return _collect(ctx)
    if stage == "CLEAN":
        return {"ok": True, "ms": 0, "note": "图像无需清洗"}
    if stage == "STANDARDIZE":
        return _reencode(ctx)
    if stage == "MASK":
        return _thumbnail(ctx)
    raise UnrecoverableError(f"IMAGE 未知阶段: {stage}")


def _collect(ctx: dict) -> dict:
    t0 = time.monotonic()
    f = ctx["file"]
    raw_abs = config.abs_path(f["raw_path"])
    if not os.path.isfile(raw_abs):
        raise UnrecoverableError(f"原始文件缺失: {f['raw_path']}")
    try:
        img = Image.open(raw_abs)
        img.load()
    except Exception as e:
        raise UnrecoverableError(f"图像无法解析: {e}") from e
    ctx["img"] = img

    exif = img.getexif()
    gps = exif.get_ifd(0x8825) if exif is not None else {}
    had_exif = bool(exif) or len(gps) > 0
    ctx["gps_present"] = len(gps) > 0

    meta = ctx.setdefault("meta", {})
    meta.update({"width": int(img.width), "height": int(img.height), "format": "jpg", "hadExif": had_exif})
    return {
        "ok": True, "ms": int((time.monotonic() - t0) * 1000),
        "width": int(img.width), "height": int(img.height),
        "hadExif": had_exif, "gpsPresent": ctx["gps_present"],
    }


def _reencode(ctx: dict) -> dict:
    t0 = time.monotonic()
    img = ctx.get("img")
    if img is None:
        raise UnrecoverableError("图像上下文丢失")
    f = ctx["file"]
    dt = f.get("created_at")
    rel = (f"processed/{dt:%Y}/{dt:%m}/{dt:%d}/{f['id']}_processed.jpg"
           if dt else f"processed/{f['id']}_processed.jpg")
    out_abs = config.abs_path(rel)
    os.makedirs(os.path.dirname(out_abs), exist_ok=True)

    # 透明通道铺白底，再转 RGB；新建 Image 对象天然丢弃 EXIF/GPS
    if img.mode in ("RGBA", "LA", "P"):
        rgba = img.convert("RGBA")
        out = Image.new("RGB", rgba.size, (255, 255, 255))
        out.paste(rgba, mask=rgba.split()[-1])
    else:
        out = img.convert("RGB")
    out.save(out_abs, "JPEG", quality=85)
    img.close()
    ctx["img"] = None

    meta = ctx["meta"]
    meta["gpsStripped"] = bool(ctx.get("gps_present"))
    db.execute(
        "UPDATE proc.data_files SET processed_path=%s, meta=%s, update_by=%s, updated_at=now() WHERE id=%s",
        (rel, Json(meta), ctx["actor"], f["id"]),
    )
    ctx["file"]["processed_path"] = rel
    return {"ok": True, "ms": int((time.monotonic() - t0) * 1000), "gpsStripped": meta["gpsStripped"]}


def _thumbnail(ctx: dict) -> dict:
    t0 = time.monotonic()
    processed_abs = config.abs_path(ctx["file"]["processed_path"])
    rel = f"thumb/{ctx['file']['id']}_thumb.jpg"
    thumb_abs = config.abs_path(rel)
    os.makedirs(os.path.dirname(thumb_abs), exist_ok=True)
    try:
        with Image.open(processed_abs) as im:
            im.thumbnail((THUMB_MAX_EDGE, THUMB_MAX_EDGE))
            im.convert("RGB").save(thumb_abs, "JPEG", quality=80)
    except Exception as e:
        raise UnrecoverableError(f"缩略图生成失败: {e}") from e
    db.execute(
        "UPDATE proc.data_files SET thumb_path=%s, updated_at=now() WHERE id=%s",
        (rel, ctx["file"]["id"]),
    )
    ctx["file"]["thumb_path"] = rel
    return {"ok": True, "ms": int((time.monotonic() - t0) * 1000), "thumb": rel}
