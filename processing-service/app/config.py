"""应用配置：全部来自环境变量，均有默认值。"""
import os


def _int(name: str, default: int) -> int:
    try:
        return int(os.getenv(name, str(default)))
    except (TypeError, ValueError):
        return default


DB_HOST = os.getenv("DB_HOST", "postgres")
DB_PORT = _int("DB_PORT", 5432)
DB_NAME = os.getenv("DB_NAME", "mydatama")
DB_USER = os.getenv("DB_USER", "mydatama")
DB_PASSWORD = os.getenv("DB_PASSWORD", "")

JWT_HMAC_SECRET = os.getenv(
    "JWT_HMAC_SECRET",
    "change_me_to_32_bytes_min_length_secret_value_here",
)
SERVICE_TOKEN = os.getenv("SERVICE_TOKEN", "change_me_service_token_for_internal_api_calls")

JAVA_INTERNAL_BASE_URL = os.getenv("JAVA_INTERNAL_BASE_URL", "http://platform-app:8080").rstrip("/")
STORAGE_ROOT = os.getenv("STORAGE_ROOT", "/data")

WORKER_THREADS = max(1, _int("WORKER_THREADS", 2))
POLL_INTERVAL = max(1, _int("POLL_INTERVAL", 2))
JOB_LOCK_TIMEOUT_MINUTES = max(1, _int("JOB_LOCK_TIMEOUT_MINUTES", 10))
MAX_FILE_SIZE_MB = max(1, _int("MAX_FILE_SIZE_MB", 200))
MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024

JWT_ALGORITHM = "HS256"

DB_POOL_MIN = 1
DB_POOL_MAX = max(4, WORKER_THREADS + 4)

# 扩展名白名单 → 模态映射
MODALITY_BY_EXT = {
    "csv": "STRUCTURED",
    "json": "STRUCTURED",
    "xlsx": "STRUCTURED",
    "xls": "STRUCTURED",
    "txt": "TEXT",
    "md": "TEXT",
    "log": "TEXT",
    "jpg": "IMAGE",
    "png": "IMAGE",
    "webp": "IMAGE",
    "mp4": "VIDEO",
    "mov": "VIDEO",
    "webm": "VIDEO",
}
ALLOWED_EXTS = set(MODALITY_BY_EXT.keys())


def abs_path(rel: str) -> str:
    """把 /data 卷相对路径转为绝对路径。"""
    return os.path.join(STORAGE_ROOT, *rel.split("/"))


def raw_dir() -> str:
    return os.path.join(STORAGE_ROOT, "raw")


def quarantine_dir() -> str:
    return os.path.join(STORAGE_ROOT, "quarantine")
