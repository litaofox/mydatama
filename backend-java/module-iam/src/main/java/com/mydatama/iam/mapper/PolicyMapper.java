package com.mydatama.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydatama.iam.entity.Policy;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PolicyMapper extends BaseMapper<Policy> {
}
