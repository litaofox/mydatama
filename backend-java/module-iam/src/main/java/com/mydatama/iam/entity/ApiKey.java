package com.mydatama.iam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iam.api_keys")
public class ApiKey extends BaseEntity {

    private Long userId;
    private String name;
    private String keyPrefix;
    private String keyHash;
    private String scopes;
    private java.time.LocalDateTime expireAt;
    private java.time.LocalDateTime lastUsedAt;
    private String lastUsedIp;
    private Boolean enabled;
}
