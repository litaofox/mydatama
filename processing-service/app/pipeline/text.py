"""TEXT 模态处理器（txt / md / log）。

简化决策（注释说明）：为保证大文件内存安全（384MB 限额），TEXT 在 COLLECT_VALIDATE
阶段以逐行流式一遍完成"统计 + 清洗 + 掩码 + 输出"，各子操作分别计时并缓存为
CLEAN / STANDARDIZE / MASK 阶段的 metrics，后续阶段直接返回缓存结果。
"""
import logging
import os
import time

from psycopg2.extras import Json

from app import config, db
from app.errors import UnrecoverableError
from app.pipeline import masker
from app.pipeline.io_utils import sniff_encoding

logger = logging.getLogger("pipeline.text")


def run_stage(stage: str, ctx: dict) -> dict:
    if stage == "COLLECT_VALIDATE":
        return _stream_process(ctx)
    cached = ctx.get("cached") or {}
    return cached.get(stage) or {"ok": True, "ms": 0, "note": "已在流式一遍中合并处理"}


def _stream_process(ctx: dict) -> dict:
    t_all = time.monotonic()
    f = ctx["file"]
    raw_abs = config.abs_path(f["raw_path"])
    if not os.path.isfile(raw_abs):
        raise UnrecoverableError(f"原始文件缺失: {f['raw_path']}")

    dt = f.get("created_at")
    out_rel = (f"processed/{dt:%Y}/{dt:%m}/{dt:%d}/{f['id']}_processed.txt"
               if dt else f"processed/{f['id']}_processed.txt")
    out_abs = config.abs_path(out_rel)
    os.makedirs(os.path.dirname(out_abs), exist_ok=True)

    t_clean = t_mask = 0.0
    total_lines = empty_removed = kept_lines = char_count = 0
    hits_total: dict = {}

    with open(raw_abs, "r", encoding=sniff_encoding(raw_abs), errors="replace") as fin, \
            open(out_abs, "w", encoding="utf-8", newline="\n") as fout:
        for line in fin:
            total_lines += 1
            t0 = time.monotonic()
            text = line.strip()  # CLEAN：去首尾空白（含 \r，STANDARDIZE 换行统一为 \n）
            if not text:
                empty_removed += 1
                t_clean += time.monotonic() - t0
                continue
            kept_lines += 1
            t_clean += time.monotonic() - t0

            t1 = time.monotonic()
            masked, hits = masker.mask_text(text)  # MASK：正则识别并掩码
            t_mask += time.monotonic() - t1
            for k, n in hits.items():
                hits_total[k] = hits_total.get(k, 0) + n
            char_count += len(masked)
            fout.write(masked + "\n")

    valid_line_rate = round(kept_lines / total_lines, 6) if total_lines else 0.0
    sensitive_hits = sum(hits_total.values())

    meta = ctx.setdefault("meta", {})
    meta.update({
        "charCount": char_count,
        "validLineRate": valid_line_rate,
        "sensitiveHits": sensitive_hits,
    })
    db.execute(
        "UPDATE proc.data_files SET processed_path=%s, meta=%s, update_by=%s, updated_at=now() WHERE id=%s",
        (out_rel, Json(meta), ctx["actor"], f["id"]),
    )
    ctx["file"]["processed_path"] = out_rel
    ctx["cached"] = {
        "CLEAN": {
            "ok": True, "ms": int(t_clean * 1000),
            "linesBefore": total_lines, "linesAfter": kept_lines,
            "removedEmptyLines": empty_removed,
        },
        "STANDARDIZE": {
            "ok": True, "ms": 0, "note": "换行统一为 \\n、编码统一 UTF-8（流式合并处理）",
        },
        "MASK": {
            "ok": True, "ms": int(t_mask * 1000),
            "sensitiveHits": sensitive_hits, "hitDetail": hits_total,
        },
    }
    return {
        "ok": True, "ms": int((time.monotonic() - t_all) * 1000),
        "rows": total_lines, "chars": char_count,
        "validLineRate": valid_line_rate, "sensitiveHits": sensitive_hits,
        "errorRows": 0,
    }
