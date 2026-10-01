package com.mydatama.common.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公共字段基类（SYS-001 公共约定）：created_at/updated_at/create_by/update_by/deleted。
 */
@Data
public abstract class BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String createBy;

    private String updateBy;

    @TableLogic
    private Integer deleted;
}
