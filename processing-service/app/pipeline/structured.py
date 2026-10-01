"""STRUCTURED 模态处理器（csv / json / xlsx）。

性能约定：5 万行 CSV 全链路目标 <60s（384MB 内存限额），
读取用 pandas 一次性 read_csv，全部行级计算向量化，禁止逐行 DataFrame.append。
"""
import json
import logging
import os
import re
import time
from collections import Counter

import numpy as np
import pandas as pd
from psycopg2.extras import Json

from app import config, db
from app.errors import ApiError, ERR_BAD_STATUS, UnrecoverableError
from app.pipeline import masker
from app.pipeline.io_utils import sniff_encoding

logger = logging.getLogger("pipeline.structured")

# ---------- 内置映射表 ----------
# 同义列名归一（键小写匹配；映射后保持原列顺序）
COLUMN_ALIASES = {
    "lon": "longitude", "lng": "longitude", "long": "longitude", "经度": "longitude",
    "lat": "latitude", "纬度": "latitude",
    "plate": "plate_no", "车牌号": "plate_no", "车牌": "plate_no", "车牌号码": "plate_no",
    "speed_kmh": "speed", "车速": "speed", "速度": "speed",
    "手机号": "phone", "电话": "phone", "tel": "phone",
    "身份证号": "idcard", "证件号": "idcard",
    "邮箱": "email", "邮件": "email",
    "姓名": "name", "司机姓名": "name",
    "时间": "event_time", "日期时间": "event_time", "timestamp": "event_time",
    "event_type": "event_code", "事件类型": "event_code", "报警类型": "event_code",
    "金额": "amount", "费用": "amount", "总价": "amount",
}

# 厂商私有事件码 → 国标码（1 前向碰撞预警 / 2 车道偏离预警 / 3 车距过近预警 / 4 侧翻预警 / 5 急加速 / 6 急减速）
EVENT_CODE_MAP = {
    "0X1A": "1", "FCW": "1", "FCW_ON": "1",
    "0X1B": "2", "LDW": "2", "LDW_ON": "2",
    "0X1C": "3", "HMW": "3", "HMW_ON": "3",
    "0X1D": "4", "URW": "4", "URW_ON": "4",
    "0X2A": "5", "HARD_ACC": "5",
    "0X2B": "6", "HARD_BRAKE": "6",
}

LON_ALIASES = {"longitude", "lon", "lng", "long", "经度"}
LAT_ALIASES = {"latitude", "lat", "纬度"}
TIME_COL_RE = re.compile(r"time|date|时间|日期", re.I)
AMOUNT_COL_RE = re.compile(r"amount|price|fee|cost|金额|费用|价格", re.I)
DATE_VALUE_RE = re.compile(r"\d{4}-\d{2}-\d{2}( \d{2}:\d{2}:\d{2})?")
INT_VALUE_RE = re.compile(r"-?\d+")
FLOAT_VALUE_RE = re.compile(r"-?\d+(\.\d+)?")

_KIND_RE = {
    "phone": masker.PHONE_RE,
    "email": masker.EMAIL_RE,
    "idcard": masker.IDCARD_RE,
    "bankcard": masker.BANKCARD_RE,
}


# ---------- 读取 ----------
def read_dataset(path: str, fmt: str) -> pd.DataFrame:
    if fmt in ("xlsx", "xls"):
        try:
            sheets = pd.read_excel(path, sheet_name=None, engine="openpyxl")
        except ImportError as e:
            # 依赖中未含 xlrd（老 .xls 引擎），统一引导另存为 xlsx
            raise UnrecoverableError("暂不支持 .xls 老格式，请另存为 .xlsx 后重新上传") from e
        except ValueError as e:
            if fmt == "xls":
                raise UnrecoverableError(
                    f".xls 老格式无法以 openpyxl 引擎解析，请另存为 .xlsx 后重新上传") from e
            raise
        frames = [df for df in sheets.values() if not df.empty]
        if not frames:
            raise UnrecoverableError("Excel 所有工作表均为空")
        df = pd.concat(frames, ignore_index=True) if len(frames) > 1 else frames[0].copy()
    elif fmt == "json":
        with open(path, "r", encoding=sniff_encoding(path)) as fh:
            data = json.load(fh)
        if isinstance(data, dict):
            records = data.get("events")
            if not isinstance(records, list):
                records = next((v for v in data.values() if isinstance(v, list)), None)
            if records is None:
                records = [data]
        elif isinstance(data, list):
            records = data
        else:
            raise UnrecoverableError("JSON 结构不支持：需为对象 {\"events\":[...]} 或数组")
        if not records:
            raise UnrecoverableError("JSON 事件列表为空")
        df = pd.json_normalize(records)
    else:  # csv
        df = pd.read_csv(path, encoding=sniff_encoding(path), low_memory=False)
    df.columns = [str(c).strip().lstrip("\ufeff") for c in df.columns]
    return df


def _find_col(df: pd.DataFrame, aliases: set) -> str | None:
    for c in df.columns:
        if str(c).strip().lower() in aliases:
            return c
    return None


# ---------- 阶段实现 ----------
def run_stage(stage: str, ctx: dict) -> dict:
    if stage == "COLLECT_VALIDATE":
        return _stage_collect(ctx)
    if stage == "CLEAN":
        return _stage_clean(ctx)
    if stage == "STANDARDIZE":
        return _stage_standardize(ctx)
    if stage == "MASK":
        return _stage_mask(ctx)
    raise UnrecoverableError(f"STRUCTURED 未知阶段: {stage}")


def _stage_collect(ctx: dict) -> dict:
    t0 = time.monotonic()
    f = ctx["file"]
    raw_abs = config.abs_path(f["raw_path"])
    if not os.path.isfile(raw_abs):
        raise UnrecoverableError(f"原始文件缺失: {f['raw_path']}")
    df = read_dataset(raw_abs, f["format"])
    if df.empty:
        raise UnrecoverableError("文件解析结果为空表")
    ctx["df"] = df

    rows, cols = df.shape
    missing_rate = round(float(df.isna().mean().mean()), 6)
    dup_mask = df.duplicated(keep="first")
    dup_rate = round(float(dup_mask.mean()), 6)
    sensitive_cols = [str(c) for c in df.columns if masker.detect_column_strategy(str(c))]

    # 坏行标记（全部向量化）
    reasons = pd.Series("", index=df.index, dtype=object)
    bad_any = pd.Series(False, index=df.index)

    def mark(mask: pd.Series, reason: str) -> None:
        nonlocal reasons, bad_any
        reasons = reasons + pd.Series(np.where(mask.fillna(False), "|" + reason, ""), index=df.index)
        bad_any = bad_any | mask.fillna(False)

    mark(df.isna().all(axis=1), "EMPTY_ROW")
    mark(dup_mask, "DUP_ROW")

    lon_col, lat_col = _find_col(df, LON_ALIASES), _find_col(df, LAT_ALIASES)
    if lon_col and lat_col:
        lon = pd.to_numeric(df[lon_col], errors="coerce")
        lat = pd.to_numeric(df[lat_col], errors="coerce")
        out_of_range = ((lon.notna() & ~lon.between(73, 136))
                        | (lat.notna() & ~lat.between(3, 54)))
        jump = (((lon - lon.shift(1)).abs() > 1) | ((lat - lat.shift(1)).abs() > 1))
        if len(jump):
            jump.iloc[0] = False
        mark(out_of_range | jump, "GEO_DRIFT")

    for tc in [c for c in df.columns if TIME_COL_RE.search(str(c))][:2]:
        ts = pd.to_datetime(df[tc], errors="coerce")
        reversed_ = ts.notna() & ts.shift(1).notna() & (ts < ts.shift(1))
        mark(reversed_, "TIME_REVERSED")

    error_rows = int(bad_any.sum())
    if error_rows > 0:
        bad = df.loc[bad_any].copy()
        bad["bad_reason"] = reasons.loc[bad_any].str.lstrip("|")
        os.makedirs(config.quarantine_dir(), exist_ok=True)
        bad.to_csv(os.path.join(config.quarantine_dir(), f"{f['id']}_badrows.csv"),
                   index=False, encoding="utf-8")

    meta = ctx.setdefault("meta", {})
    meta.update({
        "rowCount": int(rows), "columnCount": int(cols),
        "missingRate": missing_rate, "duplicateRate": dup_rate,
        "errorRowCount": error_rows, "quarantineCount": error_rows,
        "sensitiveCols": sensitive_cols,
    })
    return {
        "ok": True, "ms": int((time.monotonic() - t0) * 1000),
        "rows": int(rows), "cols": int(cols),
        "missingRate": missing_rate, "dupRate": dup_rate,
        "sensitiveCols": sensitive_cols, "errorRows": error_rows,
    }


def _fill_missing(df: pd.DataFrame) -> int:
    """数值列填中位数、分类列填众数（无众数填 UNKNOWN），返回填充单元格数。"""
    filled = 0
    for c in df.select_dtypes(include="number").columns:
        n = int(df[c].isna().sum())
        if n:
            df[c] = df[c].fillna(df[c].median())
            filled += n
    for c in df.select_dtypes(include="object").columns:
        n = int(df[c].isna().sum())
        if not n:
            continue
        mode = df[c].mode(dropna=True)
        df[c] = df[c].fillna(mode.iloc[0] if not mode.empty else "UNKNOWN")
        filled += n
    return filled


def _normalize_formats(df: pd.DataFrame) -> float:
    """时间列统一 YYYY-MM-DD HH:mm:ss、金额列保留两位小数；返回时间列解析一致率。"""
    rates = []
    for c in df.columns:
        name = str(c)
        if TIME_COL_RE.search(name):
            ts = pd.to_datetime(df[c], errors="coerce")
            rates.append(float(ts.notna().mean()) if len(df) else 1.0)
            df[c] = ts.dt.strftime("%Y-%m-%d %H:%M:%S").fillna("")
        elif AMOUNT_COL_RE.search(name):
            df[c] = pd.to_numeric(df[c], errors="coerce").round(2)
    return round(sum(rates) / len(rates), 6) if rates else 1.0


def _stage_clean(ctx: dict) -> dict:
    t0 = time.monotonic()
    df = ctx["df"]
    rows_before = len(df)
    dup_removed = int(df.duplicated(keep="first").sum())
    df = df.drop_duplicates(keep="first").reset_index(drop=True)
    filled = _fill_missing(df)
    fmt_rate = _normalize_formats(df)

    meta = ctx["meta"]
    meta.update({
        "rowCount": int(len(df)),
        "missingRate": round(float(df.isna().mean().mean()), 6),
        "duplicateRate": 0.0,
        "formatConsistencyRate": fmt_rate,
    })
    ctx["df"] = df
    return {
        "ok": True, "ms": int((time.monotonic() - t0) * 1000),
        "rowsBefore": int(rows_before), "rowsAfter": int(len(df)),
        "dupRemoved": dup_removed, "filledCells": filled,
    }


def _stage_standardize(ctx: dict) -> dict:
    t0 = time.monotonic()
    df = ctx["df"]

    # 列名映射（保持输出列顺序稳定）
    mapping: dict = {}
    for c in df.columns:
        target = COLUMN_ALIASES.get(str(c).strip().lower())
        if target and target != c and target not in df.columns and target not in mapping.values():
            mapping[c] = target
    if mapping:
        df = df.rename(columns=mapping)
    ctx["df"] = df

    # 事件码映射：厂商私有码 → 国标码，未识别保留原值
    mapped = unmapped = 0

    def conv(v):
        nonlocal mapped, unmapped
        if pd.isna(v) or str(v).strip() == "":
            return v
        hit = EVENT_CODE_MAP.get(str(v).strip().upper())
        if hit:
            mapped += 1
            return hit
        unmapped += 1
        return v

    for ec in ("event_code", "event_type"):
        if ec in df.columns:
            df[ec] = df[ec].map(conv)

    meta = ctx["meta"]
    meta["renameMap"] = mapping
    meta["vendorCodeMapped"] = mapped
    meta["vendorCodeUnmapped"] = unmapped
    return {
        "ok": True, "ms": int((time.monotonic() - t0) * 1000),
        "renamedCols": len(mapping),
        "renameDetail": [f"{k}->{v}" for k, v in list(mapping.items())[:20]],
        "eventMapped": mapped, "eventUnmapped": unmapped,
    }


def _sniff_content_kind(series: pd.Series) -> str | None:
    """对 object 列抽样识别内容类型（≥50% 命中同类型才采用）。"""
    vals = [str(v) for v in series.dropna().head(100)]
    if not vals:
        return None
    kinds = [k for k in (masker.detect_content_kind(v) for v in vals) if k]
    if len(kinds) >= max(1, int(len(vals) * 0.5)):
        return Counter(kinds).most_common(1)[0][0]
    return None


def _apply_mask(df: pd.DataFrame, col: str, kind: str) -> int:
    """应用掩码，返回命中数。数值列先转安全字符串（防科学计数法）。"""
    if pd.api.types.is_numeric_dtype(df[col]):
        df[col] = df[col].map(
            lambda v: "" if pd.isna(v) else (str(int(v)) if float(v).is_integer() else str(v)))
    s = df[col].astype(str)
    if kind == "name":
        valid = df[col].notna() & s.str.strip().ne("")
        hits = int(valid.sum())
        if hits:
            df.loc[valid, col] = df.loc[valid, col].map(lambda v: masker.mask_value("name", v))
        return hits
    rex = _KIND_RE[kind]
    hit_mask = df[col].notna() & s.str.strip().map(lambda v: bool(rex.fullmatch(v)))
    hits = int(hit_mask.sum())
    if hits:
        df.loc[hit_mask, col] = df.loc[hit_mask, col].map(lambda v: masker.mask_value(kind, v))
    return hits


def _infer_dtype(series: pd.Series) -> str:
    if pd.api.types.is_bool_dtype(series):
        return "BOOLEAN"
    if pd.api.types.is_integer_dtype(series):
        return "INT"
    if pd.api.types.is_float_dtype(series):
        return "FLOAT"
    if pd.api.types.is_datetime64_any_dtype(series):
        return "DATE"
    sample = [str(v) for v in series.dropna().head(50)]
    if not sample:
        return "STRING"
    if all(DATE_VALUE_RE.fullmatch(v) for v in sample):
        return "DATE"
    if all(INT_VALUE_RE.fullmatch(v) for v in sample):
        return "INT"
    if all(FLOAT_VALUE_RE.fullmatch(v) for v in sample):
        return "FLOAT"
    if all(v.lower() in ("true", "false") for v in sample):
        return "BOOLEAN"
    return "STRING"


def _stage_mask(ctx: dict) -> dict:
    t0 = time.monotonic()
    df = ctx["df"]
    mask_columns = []
    for c in df.columns:
        kind = masker.detect_column_strategy(str(c))
        if not kind and df[c].dtype == object:
            kind = _sniff_content_kind(df[c])
        if not kind:
            continue
        hits = _apply_mask(df, c, kind)
        if hits:
            mask_columns.append({"col": str(c), "strategy": masker.strategy_of(kind), "hits": hits})

    columns_meta = [{
        "colName": str(c),
        "dataType": _infer_dtype(df[c]),
        "sensitive": any(m["col"] == str(c) for m in mask_columns),
        "maskStrategy": next((m["strategy"] for m in mask_columns if m["col"] == str(c)), None),
    } for c in df.columns]

    # 写 processed 文件（CSV 统一 utf-8）
    f = ctx["file"]
    dt = f.get("created_at")
    rel = (f"processed/{dt:%Y}/{dt:%m}/{dt:%d}/{f['id']}_processed.csv"
           if dt else f"processed/{f['id']}_processed.csv")
    out_abs = config.abs_path(rel)
    os.makedirs(os.path.dirname(out_abs), exist_ok=True)
    df.to_csv(out_abs, index=False, encoding="utf-8")

    meta = ctx["meta"]
    meta.update({
        "sensitiveColumnCount": len(mask_columns),
        "maskColumns": mask_columns,
        "columns": columns_meta,
    })
    db.execute(
        "UPDATE proc.data_files SET processed_path=%s, meta=%s, update_by=%s, updated_at=now() WHERE id=%s",
        (rel, Json(meta), ctx["actor"], f["id"]),
    )
    ctx["file"]["processed_path"] = rel
    return {
        "ok": True, "ms": int((time.monotonic() - t0) * 1000),
        "maskColumns": mask_columns, "totalHits": sum(m["hits"] for m in mask_columns),
    }


# ---------- 一键修复（quarantine 坏行 → 修复合并 → 重登记） ----------
def repair(file_row: dict, actor: str) -> int:
    fid = file_row["id"]
    meta = file_row.get("meta") or {}
    if file_row.get("modality") != "STRUCTURED":
        raise ApiError(ERR_BAD_STATUS, "仅结构化文件支持一键修复")
    processed_abs = config.abs_path(file_row["processed_path"]) if file_row.get("processed_path") else None
    quarantine_abs = os.path.join(config.quarantine_dir(), f"{fid}_badrows.csv")
    if not processed_abs or not os.path.isfile(processed_abs) or not os.path.isfile(quarantine_abs):
        raise ApiError(ERR_BAD_STATUS, "无可修复内容（不存在坏行隔离文件）")

    good = pd.read_csv(processed_abs, encoding="utf-8", low_memory=False)
    bad = pd.read_csv(quarantine_abs, encoding="utf-8", low_memory=False)
    bad = bad.drop(columns=["bad_reason"], errors="ignore")
    if bad.empty:
        raise ApiError(ERR_BAD_STATUS, "无可修复内容（坏行隔离文件为空）")

    # 隔离行列名仍是标准化前名称，应用同一映射后对齐到 processed 列
    bad = bad.rename(columns=meta.get("renameMap") or {})
    bad = bad.reindex(columns=list(good.columns))
    before_dedup = len(bad)
    bad = bad.drop_duplicates(keep="first")
    dup_dropped = before_dedup - len(bad)
    bad = bad.dropna(how="all")
    _fill_missing(bad)
    _normalize_formats(bad)

    merged = pd.concat([good, bad], ignore_index=True).drop_duplicates(keep="first")
    merged.to_csv(processed_abs, index=False, encoding="utf-8")

    new_meta = dict(meta)
    new_meta.update({
        "rowCount": int(len(merged)), "columnCount": int(merged.shape[1]),
        "missingRate": round(float(merged.isna().mean().mean()), 6),
        "duplicateRate": 0.0,
        "quarantineCount": 0,
        "repairedRows": int(len(bad)), "droppedDuplicates": int(dup_dropped),
    })
    db.execute(
        "UPDATE proc.data_files SET meta=%s, update_by=%s, updated_at=now() WHERE id=%s",
        (Json(new_meta), actor, fid),
    )
    try:
        os.remove(quarantine_abs)  # 修复完成后清空隔离区
    except OSError:
        logger.warning("隔离文件删除失败: %s", quarantine_abs)

    job = db.execute_returning(
        "INSERT INTO proc.job_queue (file_id, stage, status, create_by, update_by) "
        "VALUES (%s, 'ANNOTATE_REGISTER', 'PENDING', %s, %s) RETURNING id",
        (fid, actor, actor),
    )
    logger.info("file=%s 修复完成: 修复 %s 行(去重 %s)，重登记 job=%s", fid, len(bad), dup_dropped, job["id"])
    return int(job["id"])
