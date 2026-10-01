"""后台 worker：同进程线程池，轮询抢占任务并执行五阶段流水线。

抢占策略（指数退避采用"在抢占 SQL 中排除最近更新行"方案，注释说明）：
  PENDING 且 updated_at < now() - (2 ^ retry_count) 秒 才可被抢占
  （retry_count=0 立即可抢、1→2s、2→4s … 5→32s）。
  失败重试时置回 PENDING 并刷新 updated_at，即天然实现指数退避，无需额外列。
"""
import logging
import os
import threading
import time
import traceback

from psycopg2.extras import Json

from app import config, db
from app.errors import UnrecoverableError
from app.pipeline import RegisterError, STAGES, dispatch_stage

logger = logging.getLogger("worker")

CLAIM_SQL = (
    "UPDATE proc.job_queue SET status='RUNNING', locked_at=now(), locked_by=%s, updated_at=now() "
    "WHERE id = (SELECT id FROM proc.job_queue WHERE status='PENDING' AND deleted=0 "
    "AND updated_at < now() - ((2 ^ retry_count) * interval '1 second') "
    "ORDER BY id FOR UPDATE SKIP LOCKED LIMIT 1) RETURNING *"
)

# 启动时崩溃恢复：仅重置未完成文件（status != READY）的超时 RUNNING 任务
RECOVER_SQL = (
    "UPDATE proc.job_queue q SET status='PENDING', locked_at=NULL, locked_by=NULL, updated_at=now() "
    "FROM proc.data_files f "
    "WHERE q.file_id = f.id AND q.status='RUNNING' "
    "AND q.locked_at < now() - (%s * interval '1 minute') AND f.status <> 'READY'"
)


class Worker(threading.Thread):
    def __init__(self, worker_id: int):
        super().__init__(name=f"worker-{worker_id}", daemon=True)
        self.worker_id = worker_id
        self.stop_event = threading.Event()

    def stop(self) -> None:
        self.stop_event.set()

    def run(self) -> None:
        logger.info("Worker-%s 启动", self.worker_id)
        self._recover_orphans()
        while not self.stop_event.is_set():
            job = None
            try:
                job = self._claim()
            except Exception as e:
                logger.warning("Worker-%s 抢占任务失败: %s", self.worker_id, e)
            if job:
                try:
                    self._process(job)
                except Exception as e:  # 双保险：_process 内部已兜底
                    logger.error("Worker-%s 处理 job=%s 未预期异常: %s\n%s",
                                 self.worker_id, job.get("id"), e, traceback.format_exc())
            else:
                self.stop_event.wait(config.POLL_INTERVAL)
        logger.info("Worker-%s 退出", self.worker_id)

    def _recover_orphans(self) -> None:
        try:
            n = db.execute(RECOVER_SQL, (config.JOB_LOCK_TIMEOUT_MINUTES,))
            if n:
                logger.warning("崩溃恢复: 重置 %s 条超时 RUNNING 任务为 PENDING", n)
        except Exception as e:
            logger.warning("崩溃恢复失败(重启后自动重试): %s", e)

    def _claim(self) -> dict | None:
        return db.execute_returning(CLAIM_SQL, (self.worker_id,))

    def _process(self, job: dict) -> None:
        job_id = job["id"]
        file_id = job["file_id"]
        logger.info("Worker-%s 领取 job=%s file=%s 起始阶段=%s",
                    self.worker_id, job_id, file_id, job["stage"])
        f = db.query_one("SELECT * FROM proc.data_files WHERE id=%s", (file_id,))
        if not f or f.get("deleted"):
            self._fail(job, UnrecoverableError("关联文件不存在或已删除"), final=True)
            return
        # UPLOADED/FAILED → PROCESSING（repair 任务的 READY 保持不变）
        db.execute(
            "UPDATE proc.data_files SET status='PROCESSING', updated_at=now() "
            "WHERE id=%s AND status IN ('UPLOADED','FAILED')",
            (file_id,),
        )
        ctx = {
            "file": f, "job": job,
            "actor": job.get("create_by") or f.get("create_by") or "system",
            "df": None, "meta": {}, "mask_columns": [], "cached": {},
        }
        try:
            start = STAGES.index(job["stage"]) if job["stage"] in STAGES else 0
            metrics = job.get("metrics") or {}
            for stage in STAGES[start:]:
                db.execute("UPDATE proc.job_queue SET stage=%s, updated_at=now() WHERE id=%s",
                           (stage, job_id))
                t0 = time.monotonic()
                m = dispatch_stage(f["modality"], stage, ctx)
                m.setdefault("ok", True)
                m["ms"] = int((time.monotonic() - t0) * 1000)
                metrics[stage] = m
                db.execute("UPDATE proc.job_queue SET metrics=%s, updated_at=now() WHERE id=%s",
                           (Json(metrics), job_id))
            db.execute("UPDATE proc.job_queue SET status='SUCCEEDED', error_msg=NULL, updated_at=now() "
                       "WHERE id=%s", (job_id,))
            db.execute("UPDATE proc.data_files SET status='READY', updated_at=now() WHERE id=%s", (file_id,))
            logger.info("job=%s 五阶段流水线完成", job_id)
        except RegisterError as e:
            # 登记回调内部已重试 5 次，直接终态失败
            self._fail(job, UnrecoverableError(str(e)), final=True)
        except UnrecoverableError as e:
            self._fail(job, e, final=True)
        except Exception as e:
            self._fail(job, e)

    def _fail(self, job: dict, err: Exception, final: bool = False) -> None:
        job_id = job["id"]
        file_id = job.get("file_id")
        retry_count = int(job.get("retry_count") or 0)
        stage = job.get("stage") or "UNKNOWN"
        msg = f"{stage}: {type(err).__name__}: {str(err)[:500]}"
        if final or retry_count >= 5:
            db.execute("UPDATE proc.job_queue SET status='FAILED', error_msg=%s, updated_at=now() "
                       "WHERE id=%s", (msg, job_id))
            if file_id:
                # repair 场景 file 已 READY 时保持 READY 不回写 FAILED
                db.execute("UPDATE proc.data_files SET status='FAILED', updated_at=now() "
                           "WHERE id=%s AND status <> 'READY'", (file_id,))
            logger.error("job=%s 失败(终态): %s", job_id, msg)
        else:
            db.execute("UPDATE proc.job_queue SET status='PENDING', retry_count=%s, error_msg=%s, "
                       "locked_at=NULL, locked_by=NULL, updated_at=now() WHERE id=%s",
                       (retry_count + 1, msg, job_id))
            logger.warning("job=%s 在 %s 阶段失败，第 %s 次重试(指数退避): %s",
                           job_id, stage, retry_count + 1, msg)


_workers: list[Worker] = []


def start_workers() -> None:
    for i in range(config.WORKER_THREADS):
        w = Worker(worker_id=os.getpid() * 100 + i)
        w.start()
        _workers.append(w)
    logger.info("已启动 %s 个后台 worker", config.WORKER_THREADS)


def stop_workers() -> None:
    for w in _workers:
        w.stop()
    _workers.clear()
