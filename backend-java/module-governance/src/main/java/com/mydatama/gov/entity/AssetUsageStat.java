package com.mydatama.gov.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 资产使用统计表（域×日 count，无 deleted/updated 列，不继承 BaseEntity）。
 */
@Data
@TableName("gov.asset_usage_stats")
public class AssetUsageStat {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assetId;
    private String bizDomain;
    private LocalDate statDate;
    private Integer cnt;
    private LocalDateTime createdAt;
}
