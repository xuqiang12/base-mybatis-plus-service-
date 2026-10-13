package com.base.system.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.base.system.modules.user.entity.UserEntity;

// 通过 MyBatis-Plus 执行用户身份与软删除信息的基础数据库操作。
public interface UserMapper extends BaseMapper<UserEntity> {}
