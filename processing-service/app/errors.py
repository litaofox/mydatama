"""统一响应体与错误码。"""
from typing import Any

# 错误码
ERR_UNAUTHORIZED = 2          # 未认证
ERR_TOKEN_EXPIRED = 3         # Token 过期
ERR_FORBIDDEN = 6             # 无权限
ERR_NOT_FOUND = 7             # 资源不存在
ERR_FILE_TYPE = 200001        # 文件类型不允许
ERR_FILE_TOO_LARGE = 200002   # 文件大小超限
ERR_BAD_STATUS = 200003       # 任务状态不允许该操作
ERR_FILE_DELETED = 200004     # 文件已删除
ERR_MASK_POLICY = 200005      # 脱敏策略无效
ERR_PIPELINE = 200006         # 处理流水线异常
ERR_INTERNAL = 199999         # 内部异常

MSG = {
    ERR_UNAUTHORIZED: "未认证",
    ERR_TOKEN_EXPIRED: "Token 已过期",
    ERR_FORBIDDEN: "无权限访问该资源",
    ERR_NOT_FOUND: "资源不存在",
    ERR_FILE_TYPE: "文件类型不允许",
    ERR_FILE_TOO_LARGE: "文件大小超出限制",
    ERR_BAD_STATUS: "任务状态不允许该操作",
    ERR_FILE_DELETED: "文件已删除",
    ERR_MASK_POLICY: "脱敏策略无效",
    ERR_PIPELINE: "处理流水线异常",
    ERR_INTERNAL: "内部异常",
}


class ApiError(Exception):
    """业务异常：携带 6 位错误码与中文 message，由全局 handler 渲染统一响应体。"""

    def __init__(self, code: int, message: str | None = None):
        self.code = code
        self.message = message or MSG.get(code, "内部异常")
        super().__init__(self.message)


class UnrecoverableError(Exception):
    """不可恢复错误（文件缺失、数据无法解析等）：worker 直接置任务终态 FAILED，不做重试。"""


def ok(data: Any = None) -> dict:
    return {"code": 0, "message": "success", "data": data}


def fail(code: int, message: str | None = None) -> dict:
    return {"code": code, "message": message or MSG.get(code, "内部异常"), "data": None}
