package com.mydatama.gov.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mydatama.common.security.UserContext;
import com.mydatama.common.security.UserInfo;
import com.mydatama.gov.api.GovApi;
import com.mydatama.gov.entity.Asset;
import com.mydatama.gov.entity.LineageEdge;
import com.mydatama.gov.entity.MetadataColumn;
import com.mydatama.gov.entity.MetadataTable;
import com.mydatama.gov.mapper.AssetMapper;
import com.mydatama.gov.mapper.AssetUsageStatMapper;
import com.mydatama.gov.mapper.LineageEdgeMapper;
import com.mydatama.gov.mapper.MetadataColumnMapper;
import com.mydatama.gov.mapper.MetadataTableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GovApiImpl implements GovApi {

    private final AssetMapper assetMapper;
    private final MetadataTableMapper metadataTableMapper;
    private final MetadataColumnMapper metadataColumnMapper;
    private final LineageEdgeMapper lineageEdgeMapper;
    private final AssetUsageStatMapper assetUsageStatMapper;

    @Override
    public Asset getAssetById(Long id) {
        return id == null ? null : assetMapper.selectById(id);
    }

    @Override
    public List<Asset> listAssetsByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return assetMapper.selectBatchIds(ids);
    }

    @Override
    public List<Asset> listAssetsByFilter(Map<String, Object> filterCond) {
        LambdaQueryWrapper<Asset> qw = new LambdaQueryWrapper<>();
        qw.eq(Asset::getStatus, "ACTIVE");
        if (filterCond != null) {
            Object modality = filterCond.get("modality");
            if (modality != null) {
                qw.eq(Asset::getModality, modality.toString());
            }
            Object bizDomain = filterCond.get("bizDomain");
            if (bizDomain != null) {
                qw.eq(Asset::getBizDomain, bizDomain.toString());
            }
            Object assetType = filterCond.get("assetType");
            if (assetType != null) {
                qw.eq(Asset::getAssetType, assetType.toString());
            }
            Object minQualityScore = filterCond.get("minQualityScore");
            if (minQualityScore instanceof Number n) {
                qw.ge(Asset::getQualityScore, BigDecimal.valueOf(n.doubleValue()));
            }
            Object secretLevelMax = filterCond.get("secretLevelMax");
            if (secretLevelMax instanceof Number n) {
                qw.le(Asset::getSecretLevel, n.intValue());
            }
        }
        qw.orderByAsc(Asset::getId);
        qw.last("LIMIT 500");
        return assetMapper.selectList(qw);
    }

    @Override
    public long countAssets() {
        return assetMapper.selectCount(null);
    }

    @Override
    public List<MetadataColumn> listColumnsByAssetIds(Collection<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<MetadataTable> tables = metadataTableMapper.selectList(
                new LambdaQueryWrapper<MetadataTable>().in(MetadataTable::getAssetId, assetIds));
        if (tables.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> tableIds = tables.stream().map(MetadataTable::getId).toList();
        return metadataColumnMapper.selectList(
                new LambdaQueryWrapper<MetadataColumn>()
                        .in(MetadataColumn::getTableId, tableIds)
                        .orderByAsc(MetadataColumn::getTableId)
                        .orderByAsc(MetadataColumn::getOrdinal));
    }

    @Override
    public Long registerProductAsset(String name, String bizDomain, Integer secretLevel,
                                     String storageRef, String extJson) {
        Asset asset = new Asset();
        asset.setName(name);
        asset.setAssetType("PRODUCT");
        asset.setBizDomain(bizDomain);
        asset.setSecretLevel(secretLevel);
        asset.setStorageRef(storageRef);
        asset.setExt(extJson);
        asset.setStatus("ACTIVE");
        UserInfo user = UserContext.get();
        if (user != null) {
            asset.setOwnerDept(user.getDeptCode());
        }
        assetMapper.insert(asset);
        return asset.getId();
    }

    @Override
    public void addLineageEdge(Long fromAssetId, Long toAssetId, String relType) {
        if (fromAssetId == null || toAssetId == null || relType == null) {
            return;
        }
        Long exists = lineageEdgeMapper.selectCount(new LambdaQueryWrapper<LineageEdge>()
                .eq(LineageEdge::getFromAssetId, fromAssetId)
                .eq(LineageEdge::getToAssetId, toAssetId)
                .eq(LineageEdge::getRelType, relType));
        if (exists != null && exists > 0) {
            return;
        }
        LineageEdge edge = new LineageEdge();
        edge.setFromAssetId(fromAssetId);
        edge.setToAssetId(toAssetId);
        edge.setRelType(relType);
        lineageEdgeMapper.insert(edge);
    }

    @Override
    public void recordUsage(Long assetId) {
        if (assetId == null) {
            return;
        }
        Asset asset = assetMapper.selectById(assetId);
        if (asset == null) {
            return;
        }
        assetUsageStatMapper.upsertUsage(assetId, asset.getBizDomain());
    }
}
