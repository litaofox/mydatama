package com.mydatama.common.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码 6 位 MMMNNN（SYS-004）：000 公共 / 100 IAM / 200 PROC / 300 GOV / 400 DS / 500 SCR / 600 PROD / 700 TRAJ。
 * 作为 int 返回时前导 0 省略（如 000001 -> 1）。
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ===== 000 公共 =====
    PARAM_ERROR(1, "参数错误"),
    UNAUTHORIZED(2, "未认证或凭证已过期"),
    FORBIDDEN(3, "禁止访问"),
    NOT_FOUND(4, "资源不存在"),
    INTERNAL_ERROR(5, "系统内部错误"),
    CONFLICT(6, "资源冲突"),
    STATE_ERROR(7, "当前状态不允许该操作"),
    TOO_MANY_REQUESTS(8, "请求频率超限"),
    SERVICE_UNAVAILABLE(9, "下游服务不可用"),

    // ===== 100 IAM =====
    BAD_CREDENTIALS(100001, "用户名或密码错误"),
    ACCOUNT_LOCKED(100002, "账号已锁定，请10分钟后再试"),
    PERM_DENIED(100003, "权限不足"),
    ABAC_DENIED(100004, "数据访问策略拒绝"),
    USERNAME_EXISTS(100005, "用户名已存在"),
    TOKEN_INVALID(100006, "令牌无效"),

    // ===== 300 GOV =====
    ASSET_NOT_FOUND(300001, "资产不存在"),

    // ===== 400 DS =====
    DATASET_NOT_FOUND(400001, "数据集不存在"),
    VERSION_NOT_FOUND(400002, "数据集版本不存在"),
    NO_VISIBLE_ASSETS(400003, "筛选条件下无可见资产"),

    // ===== 600 PROD =====
    TEMPLATE_NOT_FOUND(600001, "产品模板不存在"),
    GENERATE_FAILED(600002, "产品生成失败"),
    PRODUCT_STATE_ERROR(600003, "产品状态不允许该操作"),
    MANUAL_FAILED(600004, "说明书生成失败"),
    EXPORT_NOT_FOUND(600005, "导出文件不存在"),
    COMPLIANCE_BLOCKED(600006, "合规校验未通过"),
    PRODUCT_CODE_EXISTS(600007, "产品编码已存在"),
    REGISTER_NOT_ALLOWED(600008, "登记条件不满足"),
    LISTING_NOT_ALLOWED(600009, "挂牌条件不满足"),

    // ===== 700 TRAJ =====
    TRAJ_NOT_READY(700001, "轨迹服务未启用");

    private final int code;
    private final String message;
}
