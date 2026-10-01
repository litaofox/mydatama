package com.mydatama.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydatama.iam.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
