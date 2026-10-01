package com.mydatama.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydatama.iam.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
