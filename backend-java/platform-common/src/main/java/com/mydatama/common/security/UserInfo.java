package com.mydatama.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 认证后用户上下文信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {

    private Long userId;
    private String username;
    private String realName;
    private List<String> roles;
    private Integer secretLevel;
    private String deptCode;
    private List<String> permissions;
}
