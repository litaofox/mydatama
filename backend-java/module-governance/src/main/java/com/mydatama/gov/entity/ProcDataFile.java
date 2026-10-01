package com.mydatama.gov.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mydatama.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * proc.data_files 只读映射（proc schema 归 Python 服务所有，Java 仅统计用）。
 * meta 为 jsonb 字符串。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("proc.data_files")
public class ProcDataFile extends BaseEntity {

    private String fileName;
    private String modality;
    private String format;
    private String processedPath;
    private Long sizeBytes;
    private String meta;
    private Integer secretLevel;
    private String bizDomain;
    private String status;
}
