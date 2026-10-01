package com.mydatama.iam.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("iam.permissions")
public class Permission extends BaseEntity {

    private String code;
    private String name;
    private String module;
    private String resource;
    private String action;
    private String description;
}
