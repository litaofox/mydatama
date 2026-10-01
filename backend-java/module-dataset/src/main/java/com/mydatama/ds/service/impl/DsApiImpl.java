package com.mydatama.ds.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mydatama.ds.api.DsApi;
import com.mydatama.ds.entity.DatasetItem;
import com.mydatama.ds.entity.DatasetVersion;
import com.mydatama.ds.mapper.DatasetItemMapper;
import com.mydatama.ds.mapper.DatasetVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DsApiImpl implements DsApi {

    private final DatasetVersionMapper datasetVersionMapper;
    private final DatasetItemMapper datasetItemMapper;

    @Override
    public DatasetVersion getVersionById(Long versionId) {
        return versionId == null ? null : datasetVersionMapper.selectById(versionId);
    }

    @Override
    public List<DatasetItem> getVersionItems(Long versionId) {
        if (versionId == null) {
            return Collections.emptyList();
        }
        return datasetItemMapper.selectList(new LambdaQueryWrapper<DatasetItem>()
                .eq(DatasetItem::getVersionId, versionId)
                .orderByAsc(DatasetItem::getId));
    }
}
