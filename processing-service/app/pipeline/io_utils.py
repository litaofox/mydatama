"""通用 IO 辅助：文本编码嗅探（utf-8 → utf-8-sig → gbk）。"""


def sniff_encoding(path: str) -> str:
    for enc in ("utf-8", "utf-8-sig", "gbk"):
        try:
            with open(path, "r", encoding=enc, newline="") as f:
                while True:
                    chunk = f.read(1 << 20)
                    if not chunk:
                        return enc
        except (UnicodeDecodeError, LookupError):
            continue
    return "utf-8"  # 全部失败时兜底，由调用方以 errors="replace" 容错
