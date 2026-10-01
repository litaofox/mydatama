package com.mydatama.gov.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mydatama.common.api.ErrorCode;
import com.mydatama.common.exception.BizException;
import com.mydatama.common.security.UserContext;
import com.mydatama.common.security.UserInfo;
import com.mydatama.gov.entity.Asset;
import com.mydatama.gov.entity.AssetTag;
import com.mydatama.gov.entity.AssetUsageStat;
import com.mydatama.gov.entity.MetadataColumn;
import com.mydatama.gov.entity.MetadataTable;
import com.mydatama.gov.mapper.AssetMapper;
import com.mydatama.gov.mapper.AssetTagMapper;
import com.mydatama.gov.mapper.AssetUsageStatMapper;
import com.mydatama.gov.mapper.MetadataColumnMapper;
import com.mydatama.gov.mapper.MetadataTableMapper;
import com.mydatama.iam.api.IamApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AssetService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AssetMapper assetMapper;
    private final AssetTagMapper assetTagMapper;
    private final MetadataTableMapper metadataTableMapper;
    private final MetadataColumnMapper metadataColumnMapper;
    private final AssetUsageStatMapper assetUsageStatMapper;
    private final IamApi iamApi;

    public IPage<Asset> page(String keyword, String modality, String assetType, String bizDomain,
                             int page, int size) {
        LambdaQueryWrapper<Asset> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.like(Asset::getName, keyword);
        }
        if (StringUtils.hasText(modality)) {
            qw.eq(Asset::getModality, modality);
        }
        if (StringUtils.hasText(assetType)) {
            qw.eq(Asset::getAssetType, assetType);
        }
        if (StringUtils.hasText(bizDomain)) {
            qw.eq(Asset::getBizDomain, bizDomain);
        }
        qw.orderByDesc(Asset::getId);
        IPage<Asset> result = assetMapper.selectPage(new Page<>(page, size), qw);

        UserInfo user = UserContext.get();
        if (user == null) {
            return result;
        }
        Map<String, Object> subject = new HashMap<>();
        subject.put("secret_level", user.getSecretLevel());
        subject.put("dept_code", user.getDeptCode());
        subject.put("roles", user.getRoles());
        List<Asset> filtered = new ArrayList<>();
        for (Asset asset : result.getRecords()) {
            Map<String, Object> resourceAttrs = new HashMap<>();
            resourceAttrs.put("secret_level", asset.getSecretLevel());
            resourceAttrs.put("owner_dept", asset.getOwnerDept());
            if (iamApi.checkAccess("asset", "read", subject, resourceAttrs)) {
                filtered.add(asset);
            }
        }
        result.setRecords(filtered);
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> detail(Long id) {
        Asset asset = assetMapper.selectById(id);
        if (asset == null) {
            throw new BizException(ErrorCode.ASSET_NOT_FOUND);
        }
        Map<String, Object> map = OBJECT_MAPPER.convertValue(asset, Map.class);

        List<Map<String, Object>> columns = new ArrayList<>();
        List<MetadataTable> tables = metadataTableMapper.selectList(
                new LambdaQueryWrapper<MetadataTable>().eq(MetadataTable::getAssetId, id));
        if (!tables.isEmpty()) {
            List<Long> tableIds = tables.stream().map(MetadataTable::getId).toList();
            List<MetadataColumn> cols = metadataColumnMapper.selectList(
                    new LambdaQueryWrapper<MetadataColumn>()
                            .in(MetadataColumn::getTableId, tableIds)
                            .orderByAsc(MetadataColumn::getOrdinal));
            for (MetadataColumn c : cols) {
                Map<String, Object> cm = new HashMap<>();
                cm.put("colName", c.getColName());
                cm.put("dataType", c.getDataType());
                cm.put("ordinal", c.getOrdinal());
                cm.put("sensitive", c.getSensitive());
                cm.put("maskStrategy", c.getMaskStrategy());
                cm.put("comment", c.getComment());
                columns.add(cm);
            }
        }
        map.put("columns", columns);

        List<String> tags = assetTagMapper.selectList(
                        new LambdaQueryWrapper<AssetTag>().eq(AssetTag::getAssetId, id))
                .stream().map(AssetTag::getTag).toList();
        map.put("tags", tags);

        long usageTotal = assetUsageStatMapper.selectList(
                        new LambdaQueryWrapper<AssetUsageStat>().eq(AssetUsageStat::getAssetId, id))
                .stream().mapToLong(s -> s.getCnt() == null ? 0L : s.getCnt()).sum();
        map.put("usageTotal", usageTotal);

        Object extParsed = null;
        if (StringUtils.hasText(asset.getExt())) {
            try {
                extParsed = OBJECT_MAPPER.readValue(asset.getExt(), new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception e) {
                extParsed = asset.getExt();
            }
        }
        map.put("extParsed", extParsed);
        return map;
    }
}
