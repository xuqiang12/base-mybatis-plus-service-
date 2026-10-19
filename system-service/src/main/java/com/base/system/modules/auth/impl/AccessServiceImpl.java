package com.base.system.modules.auth.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.security.LoginUser;
import com.base.system.modules.auth.AccessService;
import com.base.system.modules.auth.mapper.AccessMapper;
import com.base.system.modules.permission.entity.PermissionEntity;
import com.base.system.modules.permission.mapper.PermissionMapper;
import com.base.system.modules.user.entity.UserEntity;
import com.base.system.modules.user.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 当前身份与授权业务实现，每次认证从数据库读取最新状态。
 */
@Service
public class AccessServiceImpl implements AccessService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private AccessMapper accessMapper;

    @Override
    public LoginUser currentUser(int userId) {
        // 校验账号仍然有效，已删除账号的旧 Token 不再放行。
        UserEntity user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "用户不存在或已停用");
        }
        // 超级管理员读取全部操作权限，普通用户读取角色关联的操作权限。
        List<String> permissions;
        if (Boolean.TRUE.equals(user.getIsSuperAdmin())) {
            LambdaQueryWrapper<PermissionEntity> query = new LambdaQueryWrapper<PermissionEntity>()
                    .orderByAsc(PermissionEntity::getId);
            List<PermissionEntity> permissionList = permissionMapper.selectList(query);
            permissions = permissionList.stream().map(PermissionEntity::getCode).toList();
        } else {
            permissions = accessMapper.findPermissionCodes(userId);
        }

        List<String> roles = accessMapper.findRoleNames(userId);
        return new LoginUser(user.getId(), user.getUsername(), user.getEmail(), user.getAvatar(),
                Boolean.TRUE.equals(user.getIsSuperAdmin()), roles, permissions);
    }
}
