package com.mydatama.ds.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydatama.ds.entity.Dataset;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DatasetMapper extends BaseMapper<Dataset> {
}
