"""VIDEO 模态处理器（mp4 / mov / webm）。

阶段拆分：COLLECT_VALIDATE 用 ffprobe 读取元信息；STANDARDIZE 转 mp4（mp4 直接
remux -c copy，mov/webm 用 libx264 -preset veryfast 转码，均 -map_metadata -1 剥离
元数据）；MASK 阶段提取首帧缩略图（元数据剥离已在转码时完成）。
subprocess 避免高内存 preset 以适配 384MB 容器限额。
"""
import json
import logging
import os
import subprocess
import time

from psycopg2.extras import Json

from app import config, db
from app.errors import UnrecoverableError

logger = logging.getLogger("pipeline.video")

FFPROBE_TIMEOUT = 30
TRANSCODE_TIMEOUT = 600


def _run(cmd: list, timeout: int) -> subprocess.CompletedProcess:
    try:
        proc = subprocess.run(cmd, capture_output=True, timeout=timeout)
    except FileNotFoundError as e:
        raise UnrecoverableError(f"ffmpeg/ffprobe 不可用: {e}") from e
    except subprocess.TimeoutExpired as e:
        raise UnrecoverableError(f"ffmpeg 处理超时({timeout}s)") from e
    if proc.returncode != 0:
        stderr = proc.stderr.decode("utf-8", "replace")[-500:]
        raise UnrecoverableError(f"ffmpeg 执行失败: {stderr}")
    return proc


def run_stage(stage: str, ctx: dict) -> dict:
    if stage == "COLLECT_VALIDATE":
        return _probe(ctx)
    if stage == "CLEAN":
        return {"ok": True, "ms": 0, "note": "视频无需清洗"}
    if stage == "STANDARDIZE":
        return _transcode(ctx)
    if stage == "MASK":
        return _thumbnail(ctx)
    raise UnrecoverableError(f"VIDEO 未知阶段: {stage}")


def _probe(ctx: dict) -> dict:
    t0 = time.monotonic()
    f = ctx["file"]
    raw_abs = config.abs_path(f["raw_path"])
    if not os.path.isfile(raw_abs):
        raise UnrecoverableError(f"原始文件缺失: {f['raw_path']}")
    proc = _run(
        ["ffprobe", "-v", "error", "-print_format", "json", "-show_format", "-show_streams", raw_abs],
        FFPROBE_TIMEOUT,
    )
    try:
        info = json.loads(proc.stdout.decode("utf-8", "replace"))
    except json.JSONDecodeError as e:
        raise UnrecoverableError(f"ffprobe 输出解析失败: {e}") from e
    fmt = info.get("format") or {}
    vstream = next((s for s in info.get("streams") or [] if s.get("codec_type") == "video"), None)
    if vstream is None:
        raise UnrecoverableError("文件中未找到视频流")

    duration = float(fmt.get("duration") or vstream.get("duration") or 0)
    width = int(vstream.get("width") or 0)
    height = int(vstream.get("height") or 0)
    bitrate = int(float(fmt.get("bit_rate") or vstream.get("bit_rate") or 0))

    meta = ctx.setdefault("meta", {})
    meta.update({
        "width": width, "height": height,
        "durationSec": round(duration, 2),
        "bitrateKbps": bitrate // 1000,
        "codec": vstream.get("codec_name"),
    })
    return {
        "ok": True, "ms": int((time.monotonic() - t0) * 1000),
        "durationSec": meta["durationSec"], "resolution": f"{width}x{height}",
        "codec": meta["codec"], "bitrateKbps": meta["bitrateKbps"],
    }


def _transcode(ctx: dict) -> dict:
    t0 = time.monotonic()
    f = ctx["file"]
    raw_abs = config.abs_path(f["raw_path"])
    dt = f.get("created_at")
    rel = (f"processed/{dt:%Y}/{dt:%m}/{dt:%d}/{f['id']}_processed.mp4"
           if dt else f"processed/{f['id']}_processed.mp4")
    out_abs = config.abs_path(rel)
    os.makedirs(os.path.dirname(out_abs), exist_ok=True)

    if f["format"] == "mp4":
        # mp4 直接 remux（流复制），仅剥离元数据，速度快且内存占用低
        cmd = ["ffmpeg", "-y", "-i", raw_abs, "-map", "0", "-map_metadata", "-1", "-c", "copy", out_abs]
    else:
        # mov/webm 转码为 mp4：veryfast 预设控制内存与 CPU
        cmd = ["ffmpeg", "-y", "-i", raw_abs, "-map", "0:v:0", "-map", "0:a:0?", "-map_metadata", "-1",
               "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-c:a", "aac", out_abs]
    _run(cmd, TRANSCODE_TIMEOUT)

    db.execute(
        "UPDATE proc.data_files SET processed_path=%s, meta=%s, update_by=%s, updated_at=now() WHERE id=%s",
        (rel, Json(ctx["meta"]), ctx["actor"], f["id"]),
    )
    ctx["file"]["processed_path"] = rel
    return {
        "ok": True, "ms": int((time.monotonic() - t0) * 1000),
        "processed": rel, "metadataStripped": True,
    }


def _thumbnail(ctx: dict) -> dict:
    t0 = time.monotonic()
    processed_abs = config.abs_path(ctx["file"]["processed_path"])
    rel = f"thumb/{ctx['file']['id']}_thumb.jpg"
    thumb_abs = config.abs_path(rel)
    os.makedirs(os.path.dirname(thumb_abs), exist_ok=True)
    _run(["ffmpeg", "-y", "-ss", "0", "-i", processed_abs, "-frames:v", "1", "-q:v", "3", thumb_abs], 60)
    db.execute(
        "UPDATE proc.data_files SET thumb_path=%s, updated_at=now() WHERE id=%s",
        (rel, ctx["file"]["id"]),
    )
    ctx["file"]["thumb_path"] = rel
    return {"ok": True, "ms": int((time.monotonic() - t0) * 1000), "thumb": rel}
