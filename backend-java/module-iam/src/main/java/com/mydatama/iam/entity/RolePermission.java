package com.mydatama.iam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iam.role_permissions")
public class RolePermission extends BaseEntity {

    private Long roleId;
    private Long permissionId;
}
