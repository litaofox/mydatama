package com.mydatama.gov.api;

import com.mydatama.gov.entity.Asset;
import com.mydatama.gov.entity.MetadataColumn;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * GOV 对外服务接口（跨模块调用仅允许走 api 包）。
 */
public interface GovApi {

    /** 按ID查资产，不存在返回 null。 */
    Asset getAssetById(Long id);

    /** 按ID集合批量查资产。 */
    List<Asset> listAssetsByIds(Collection<Long> ids);

    /**
     * 按数据集筛选条件查询资产（filterCond 键：modality/bizDomain/assetType/minQualityScore(BigDecimal)/secretLevelMax(Integer)），
     * 仅 status=ACTIVE，按 id 升序，最多 500 条。
     */
    List<Asset> listAssetsByFilter(Map<String, Object> filterCond);

    /** 资产总数。 */
    long countAssets();

    /** 查指定资产集合的全部元数据列（经 metadata_tables 关联）。 */
    List<MetadataColumn> listColumnsByAssetIds(Collection<Long> assetIds);

    /** 登记 PRODUCT 类型资产，返回资产ID；ownerDept 取当前用户 deptCode（无上下文则 null），status=ACTIVE。 */
    Long registerProductAsset(String name, String bizDomain, Integer secretLevel, String storageRef, String extJson);

    /** 写血缘边（已存在同 from/to/relType 则跳过）。 */
    void addLineageEdge(Long fromAssetId, Long toAssetId, String relType);

    /** 记录资产使用（asset_usage_stats 当日 cnt+1，资产不存在则跳过）。 */
    void recordUsage(Long assetId);
}
