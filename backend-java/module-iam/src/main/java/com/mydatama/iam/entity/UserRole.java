package com.mydatama.iam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iam.user_roles")
public class UserRole extends BaseEntity {

    private Long userId;
    private Long roleId;
}
