package com.mydatama.ds.api;

import com.mydatama.ds.entity.DatasetItem;
import com.mydatama.ds.entity.DatasetVersion;

import java.util.List;

/**
 * DS 对外服务接口（跨模块调用仅允许走 api 包）。
 */
public interface DsApi {

    /** 按ID查数据集版本，不存在返回 null。 */
    DatasetVersion getVersionById(Long versionId);

    /** 查版本内全部数据集项，按 id 升序。 */
    List<DatasetItem> getVersionItems(Long versionId);
}
