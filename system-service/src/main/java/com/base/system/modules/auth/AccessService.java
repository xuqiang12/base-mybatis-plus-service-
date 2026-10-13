package com.base.system.modules.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.security.LoginUser;
import com.base.security.CurrentUserProvider;
import com.base.system.modules.auth.mapper.AccessMapper;
import com.base.system.modules.permission.entity.PermissionEntity;
import com.base.system.modules.permission.mapper.PermissionMapper;
import com.base.system.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;

// 请求时读取有效身份和最新授权，避免 JWT 中的旧权限继续生效。
@Service
@RequiredArgsConstructor
public class AccessService implements CurrentUserProvider {
    private final UserMapper userMapper;
    private final PermissionMapper permissionMapper;
    private final AccessMapper accessMapper;

    @Override
    public LoginUser currentUser(int userId, String token) { return currentUser(userId); }

    public LoginUser currentUser(int userId) {
        var user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "用户不存在或已停用");
        }
        var permissions = Boolean.TRUE.equals(user.getIsSuperAdmin())
                ? permissionMapper.selectList(new LambdaQueryWrapper<PermissionEntity>().orderByAsc(PermissionEntity::getId))
                    .stream().map(PermissionEntity::getCode).toList()
                : accessMapper.permissions(userId);
        return new LoginUser(user.getId(), user.getUsername(), user.getEmail(), user.getAvatar(),
                Boolean.TRUE.equals(user.getIsSuperAdmin()), accessMapper.roles(userId), permissions);
    }
}
