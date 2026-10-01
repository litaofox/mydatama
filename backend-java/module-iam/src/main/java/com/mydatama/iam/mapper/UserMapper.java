package com.mydatama.iam.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mydatama.iam.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
