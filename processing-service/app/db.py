"""PostgreSQL 连接池与查询辅助。所有 SQL 均使用 %s 参数化，禁止字符串拼接。"""
import logging
import threading

import psycopg2
import psycopg2.extras
import psycopg2.pool

from app import config

logger = logging.getLogger("db")

_pool: psycopg2.pool.ThreadedConnectionPool | None = None
_lock = threading.Lock()


def init_pool() -> None:
    global _pool
    with _lock:
        if _pool is not None:
            return
        _pool = psycopg2.pool.ThreadedConnectionPool(
            config.DB_POOL_MIN,
            config.DB_POOL_MAX,
            host=config.DB_HOST,
            port=config.DB_PORT,
            dbname=config.DB_NAME,
            user=config.DB_USER,
            password=config.DB_PASSWORD,
            connect_timeout=5,
            application_name="processing-service",
        )
        logger.info("数据库连接池已初始化 (max=%s)", config.DB_POOL_MAX)


def get_conn():
    global _pool
    if _pool is None:
        try:
            init_pool()
        except psycopg2.Error as e:
            raise RuntimeError(f"数据库连接池初始化失败: {e}") from e
    try:
        return _pool.getconn()
    except psycopg2.pool.PoolError as e:
        raise RuntimeError(f"获取数据库连接失败: {e}") from e


def put_conn(conn, close: bool = False) -> None:
    if _pool is not None and conn is not None:
        try:
            _pool.putconn(conn, close=close)
        except psycopg2.Error:
            pass


def query_all(sql: str, params: tuple = ()) -> list[dict]:
    conn = get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(sql, params)
            return [dict(r) for r in cur.fetchall()]
    finally:
        put_conn(conn)


def query_one(sql: str, params: tuple = ()) -> dict | None:
    conn = get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(sql, params)
            row = cur.fetchone()
            return dict(row) if row else None
    finally:
        put_conn(conn)


def execute(sql: str, params: tuple = ()) -> int:
    conn = get_conn()
    try:
        with conn.cursor() as cur:
            cur.execute(sql, params)
            rowcount = cur.rowcount
        conn.commit()
        return rowcount
    except Exception:
        conn.rollback()
        raise
    finally:
        put_conn(conn)


def execute_returning(sql: str, params: tuple = ()) -> dict | None:
    conn = get_conn()
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
            cur.execute(sql, params)
            row = cur.fetchone()
            conn.commit()
            return dict(row) if row else None
    except Exception:
        conn.rollback()
        raise
    finally:
        put_conn(conn)


def healthy() -> bool:
    try:
        query_one("SELECT 1 AS ok")
        return True
    except Exception as e:
        logger.warning("健康检查失败: %s", e)
        return False
