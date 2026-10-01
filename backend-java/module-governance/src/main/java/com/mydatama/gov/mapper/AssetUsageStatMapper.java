package com.mydatama.gov.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydatama.gov.entity.AssetUsageStat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AssetUsageStatMapper extends BaseMapper<AssetUsageStat> {

    @Insert("INSERT INTO gov.asset_usage_stats (asset_id, biz_domain, stat_date, cnt) " +
            "VALUES (#{assetId}, #{bizDomain}, CURRENT_DATE, 1) " +
            "ON CONFLICT (asset_id, stat_date) DO UPDATE SET cnt = gov.asset_usage_stats.cnt + 1")
    int upsertUsage(@Param("assetId") Long assetId, @Param("bizDomain") String bizDomain);
}
