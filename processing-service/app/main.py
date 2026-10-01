"""processing-service 入口：FastAPI 应用 + 后台 worker 生命周期。"""
import logging
import sys

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app import db, worker
from app.errors import ApiError, ERR_INTERNAL, fail
from app.routes import files, health, jobs

# 日志输出到 stdout，格式简洁
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
    stream=sys.stdout,
)
logger = logging.getLogger("main")

app = FastAPI(title="processing-service", version="1.0.0")


@app.on_event("startup")
def _startup() -> None:
    try:
        db.init_pool()
    except Exception as e:
        # 避免容器启动竞态：DB 未就绪时允许启动，接口/worker 惰性重连
        logger.warning("启动时数据库不可用，将惰性重连: %s", e)
    worker.start_workers()


@app.on_event("shutdown")
def _shutdown() -> None:
    worker.stop_workers()


@app.exception_handler(ApiError)
async def _api_error_handler(request: Request, exc: ApiError) -> JSONResponse:
    return JSONResponse(status_code=200, content=fail(exc.code, exc.message))


@app.exception_handler(RequestValidationError)
async def _validation_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    msg = ""
    try:
        msg = str(exc.errors()[0].get("msg") or "")
    except Exception:
        pass
    return JSONResponse(status_code=200, content=fail(ERR_INTERNAL, f"请求参数无效: {msg}"[:200]))


@app.exception_handler(Exception)
async def _unhandled_handler(request: Request, exc: Exception) -> JSONResponse:
    logger.exception("未处理异常: %s", exc)
    return JSONResponse(status_code=200, content=fail(ERR_INTERNAL, "内部异常"))


app.include_router(health.router)
app.include_router(files.router)
app.include_router(jobs.router)
