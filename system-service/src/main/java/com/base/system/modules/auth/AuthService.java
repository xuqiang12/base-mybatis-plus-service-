package com.base.system.modules.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.common.exception.BusinessException;
import com.base.system.security.JwtSigningProperties;
import com.base.security.LoginUser;
import com.base.system.modules.auth.dto.LoginRequest;
import com.base.system.modules.menu.MenuService;
import com.base.system.modules.user.entity.UserEntity;
import com.base.system.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;

// 校验密码、签发 JWT 并提供用户与菜单权限信息。
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserMapper users;
    private final PasswordEncoder passwords;
    private final JwtEncoder jwt;
    private final JwtSigningProperties properties;
    private final AccessService access;
    private final MenuService menus;

    public Map<String, String> login(LoginRequest request) {
        var user = users.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, request.email())
                .eq(UserEntity::getIsDeleted, false));
        if (user == null || !passwords.matches(request.password(), user.getPassword())) {
            throw new BusinessException(401, "账号或密码错误");
        }
        var current = access.currentUser(user.getId());
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(properties.issuer()).subject(user.getId().toString())
                .issuedAt(now).expiresAt(now.plus(properties.ttl()))
                .claim("userId", user.getId()).claim("username", user.getUsername()).claim("email", user.getEmail())
                .claim("roles", current.roles()).claim("permissions", current.permissions())
                .claim("isSuperAdmin", current.isSuperAdmin()).build();
        return Map.of("token", jwt.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue());
    }

    public Map<String, Object> userInfo(LoginUser user) {
        var info = new java.util.LinkedHashMap<String, Object>();
        info.put("id", user.id()); info.put("username", user.username());
        info.put("avatar", user.avatar()); info.put("isSuperAdmin", user.isSuperAdmin());
        return Map.of("userInfo", info, "permissions", user.permissions(), "menus", menus.userMenus(user));
    }
}
