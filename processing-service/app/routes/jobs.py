"""任务路由：失败任务重试。"""
import logging

from fastapi import APIRouter, Depends

from app import db
from app.callbacks import notify_audit
from app.errors import ApiError, ERR_BAD_STATUS, ERR_NOT_FOUND, ok
from app.security import actor_from_claims, require_auth

logger = logging.getLogger("routes.jobs")
router = APIRouter()


@router.post("/api/processing/jobs/{job_id}/retry")
def retry_job(job_id: int, claims: dict = Depends(require_auth)) -> dict:
    actor = actor_from_claims(claims)
    job = db.query_one("SELECT id, file_id, status, deleted FROM proc.job_queue WHERE id=%s", (job_id,))
    if not job or job.get("deleted"):
        raise ApiError(ERR_NOT_FOUND, "任务不存在")
    if job["status"] != "FAILED":
        raise ApiError(ERR_BAD_STATUS, "仅 FAILED 状态的任务可重试")

    # 原地重试：retry_count 清零、状态回 PENDING
    db.execute(
        "UPDATE proc.job_queue SET status='PENDING', retry_count=0, error_msg=NULL, "
        "locked_at=NULL, locked_by=NULL, updated_at=now(), update_by=%s WHERE id=%s",
        (actor, job_id),
    )
    # 关联文件由 FAILED 恢复为 UPLOADED，等待 worker 重新领取
    if job.get("file_id"):
        db.execute(
            "UPDATE proc.data_files SET status='UPLOADED', updated_at=now(), update_by=%s "
            "WHERE id=%s AND status='FAILED'",
            (actor, job["file_id"]),
        )
    notify_audit(actor, "RETRY", resource=f"job:{job_id}")
    logger.info("任务重试 job=%s 操作人=%s", job_id, actor)
    return ok({"jobId": job_id, "status": "PENDING"})
