"""脱敏模块：列名识别 + 内容识别 + 掩码/哈希策略。

策略（对应错误码 200005 之外的内置策略）：
- phone    → MASK  138****5678
- email    → MASK  a**@domain.com
- idcard   → MASK  前6后4
- bankcard → MASK  前4后4
- name     → HASH  sha256 前 8 位
"""
import hashlib
import re

PHONE_RE = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")
IDCARD_RE = re.compile(r"(?<!\d)\d{17}[\dXx](?!\d)")
EMAIL_RE = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
BANKCARD_RE = re.compile(r"(?<!\d)\d{13,19}(?!\d)")

STRATEGY_MASK = "MASK"
STRATEGY_HASH = "HASH"

# 列名 → 策略。name 用 \b 边界避免误伤 file_name/user_name 等复合词。
_COLNAME_RULES = [
    (re.compile(r"phone|mobile|手机号", re.I), "phone"),
    (re.compile(r"idcard|id_card|身份证", re.I), "idcard"),
    (re.compile(r"bank_card|bankcard|银行卡", re.I), "bankcard"),
    (re.compile(r"email|邮箱", re.I), "email"),
    (re.compile(r"\bname\b|姓名", re.I), "name"),
]
_STRATEGY_OF_KIND = {
    "phone": STRATEGY_MASK,
    "email": STRATEGY_MASK,
    "idcard": STRATEGY_MASK,
    "bankcard": STRATEGY_MASK,
    "name": STRATEGY_HASH,
}


def detect_column_strategy(col_name: str) -> str | None:
    """按列名识别脱敏策略，返回 phone/email/idcard/bankcard/name 或 None。"""
    for pattern, kind in _COLNAME_RULES:
        if pattern.search(str(col_name)):
            return kind
    return None


def detect_content_kind(value: str) -> str | None:
    """按单元格内容识别敏感类型（完整匹配，优先级：身份证 > 手机号 > 邮箱 > 银行卡）。"""
    s = str(value).strip()
    if not s:
        return None
    if IDCARD_RE.fullmatch(s):
        return "idcard"
    if PHONE_RE.fullmatch(s):
        return "phone"
    if EMAIL_RE.fullmatch(s):
        return "email"
    if BANKCARD_RE.fullmatch(s):
        return "bankcard"
    return None


def mask_value(kind: str, value) -> str:
    """按类型应用掩码。"""
    s = str(value)
    if kind == "phone":
        return s[:3] + "****" + s[-4:] if len(s) >= 7 else "***"
    if kind == "email":
        if "@" not in s:
            return "***"
        local, domain = s.split("@", 1)
        return (local[:1] or "*") + "**@" + domain
    if kind == "idcard":
        return s[:6] + "*" * max(0, len(s) - 10) + s[-4:] if len(s) > 10 else "*" * len(s)
    if kind == "bankcard":
        return s[:4] + "*" * max(0, len(s) - 8) + s[-4:] if len(s) > 8 else "*" * len(s)
    if kind == "name":
        return hashlib.sha256(s.encode("utf-8")).hexdigest()[:8]
    raise ValueError(f"未知脱敏类型: {kind}")


def strategy_of(kind: str) -> str:
    return _STRATEGY_OF_KIND[kind]


def mask_text(text: str) -> tuple[str, dict]:
    """对自由文本行内所有敏感串做掩码替换（身份证/手机号/邮箱/银行卡）。

    返回 (替换后文本, {kind: 命中次数})。文本中无法可靠识别人名，不做 HASH。
    """
    hits: dict = {}

    def _sub(pattern: re.Pattern, kind: str, s: str) -> str:
        def repl(m):
            hits[kind] = hits.get(kind, 0) + 1
            return mask_value(kind, m.group(0))

        return pattern.sub(repl, s)

    out = _sub(IDCARD_RE, "idcard", text)
    out = _sub(PHONE_RE, "phone", out)
    out = _sub(EMAIL_RE, "email", out)
    out = _sub(BANKCARD_RE, "bankcard", out)
    return out, hits
