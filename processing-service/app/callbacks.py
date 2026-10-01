"""Java 平台内部接口回调：审计上报、ABAC 鉴权、资产登记。

所有外部 HTTP 调用统一 timeout=10s；除资产登记外失败均静默（不影响主流程）。
"""
import logging
import time

import httpx

from app import config

logger = logging.getLogger("callbacks")

# 模块级复用连接池；每次调用仍显式传 timeout=10
_client = httpx.Client(timeout=10.0)


def _headers() -> dict:
    return {"X-Service-Token": config.SERVICE_TOKEN, "Content-Type": "application/json"}


def notify_audit(username: str, action: str, resource: str | None = None,
                 detail: dict | None = None, ip: str | None = None) -> None:
    """审计日志上报（action: UPLOAD/RETRY/EXPORT...）。失败静默，仅记录日志。"""
    try:
        payload: dict = {"username": username, "action": action}
        if resource:
            payload["resource"] = resource
        if detail:
            payload["detail"] = detail
        if ip:
            payload["ip"] = ip
        resp = _client.post(
            f"{config.JAVA_INTERNAL_BASE_URL}/internal/audit",
            json=payload, headers=_headers(), timeout=10,
        )
        body = resp.json() if resp.status_code == 200 else {}
        if body.get("code") != 0:
            logger.warning("审计上报未成功: code=%s msg=%s", body.get("code"), body.get("message"))
    except Exception as e:  # 静默：审计失败不影响主流程
        logger.warning("审计上报异常(已忽略): %s", e)


def check_authz(username: str, permission: str, attributes: dict) -> tuple[bool, str]:
    """ABAC 细粒度鉴权回调。Java 不可达时 fail-open 放行（仅演示环境）。"""
    try:
        resp = _client.post(
            f"{config.JAVA_INTERNAL_BASE_URL}/internal/authz",
            json={"username": username, "permission": permission, "attributes": attributes},
            headers=_headers(), timeout=10,
        )
        body = resp.json() if resp.status_code == 200 else {}
        data = body.get("data") or {}
        allow = bool(data.get("allow"))
        reason = str(data.get("reason") or "")
        if body.get("code") != 0:
            return False, f"AUTHZ_ERROR_{body.get('code')}"
        return allow, reason
    except Exception as e:
        logger.warning("鉴权回调不可达，fail-open 放行: %s", e)
        return True, "FAIL_OPEN"


class RegisterError(Exception):
    """资产登记回调最终失败。"""


def register_asset(payload: dict) -> int | None:
    """资产登记（五阶段最后一步）。指数退避重试 5 次（1/2/4/8/16s），全部失败抛 RegisterError。

    Java 返回 409/10002（同 sourceFileId 已登记）视为幂等成功，返回 None。
    成功返回 assetId。
    """
    delays = [1, 2, 4, 8, 16]
    last_err = ""
    for attempt in range(5):
        try:
            resp = _client.post(
                f"{config.JAVA_INTERNAL_BASE_URL}/internal/assets/register",
                json=payload, headers=_headers(), timeout=10,
            )
            body = resp.json() if resp.status_code in (200, 409) else {}
            if resp.status_code == 409:
                logger.info("资产已登记(幂等冲突 10002)，sourceFileId=%s", payload.get("sourceFileId"))
                return None
            if resp.status_code == 200 and body.get("code") == 0:
                return (body.get("data") or {}).get("assetId")
            last_err = f"HTTP {resp.status_code} code={body.get('code')} message={body.get('message')}"
        except Exception as e:
            last_err = repr(e)
        if attempt < 4:
            time.sleep(delays[attempt])
    raise RegisterError(f"资产登记回调失败(已重试5次): {last_err}")
