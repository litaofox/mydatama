package com.mydatama.iam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iam.users")
public class User extends BaseEntity {

    private String username;
    private String passwordHash;
    private String realName;
    private String email;
    private String phone;
    private String deptCode;
    private Integer secretLevel;
    private Boolean enabled;
    private Integer failedLoginCount;
    private java.time.LocalDateTime lockedUntil;
    private java.time.LocalDateTime lastLoginAt;
    private String lastLoginIp;
}
