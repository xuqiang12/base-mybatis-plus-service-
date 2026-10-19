package com.base.system.modules.auth.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.common.exception.BusinessException;
import com.base.security.LoginUser;
import com.base.system.modules.auth.AccessService;
import com.base.system.modules.auth.AuthService;
import com.base.system.modules.auth.dto.LoginRequest;
import com.base.system.modules.auth.dto.LoginResponse;
import com.base.system.modules.auth.dto.UserInfoResponse;
import com.base.system.modules.auth.dto.UserProfileResponse;
import com.base.system.modules.menu.MenuService;
import com.base.system.modules.user.entity.UserEntity;
import com.base.system.modules.user.mapper.UserMapper;
import com.base.system.security.JwtSigningProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * 密码登录与当前用户信息业务实现。
 */
@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtSigningProperties jwtSigningProperties;

    @Autowired
    private AccessService accessService;

    @Autowired
    private MenuService menuService;

    @Override
    public LoginResponse login(LoginRequest request) {
        // 根据账号类型查询有效用户。
        String account = request.getAccount().trim();
        LambdaQueryWrapper<UserEntity> query = new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getIsDeleted, false);
        if (account.contains("@")) {
            query.eq(UserEntity::getEmail, account);
        } else {
            if (!account.matches("^1[3-9]\\d{9}$")) {
                throw new BusinessException(401, "账号或密码错误");
            }
            query.eq(UserEntity::getPhone, account);
        }
        List<UserEntity> matchingUsers = userMapper.selectList(query);

        // 历史数据允许大小写不同的邮箱并存，优先保留原来的精确匹配。
        if (matchingUsers.isEmpty() && account.contains("@")) {
            matchingUsers = userMapper.selectList(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getIsDeleted, false)
                    .apply("LOWER(email) = {0}", account.toLowerCase(Locale.ROOT)));
        }
        // 使用 BCrypt 校验密码，账号不存在或密码错误时返回同一提示。
        UserEntity user = matchingUsers.size() == 1 ? matchingUsers.getFirst() : null;
        if (user == null || user.getPassword() == null
                || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "账号或密码错误");
        }

        // 查询当前权限，并签发有有效期的 JWT。
        LoginUser currentUser = accessService.currentUser(user.getId());
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtSigningProperties.issuer())
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(jwtSigningProperties.ttl()))
                .claim("userId", user.getId())
                .claim("username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("roles", currentUser.roles())
                .claim("permissions", currentUser.permissions())
                .claim("isSuperAdmin", currentUser.isSuperAdmin())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        JwtEncoderParameters jwtParameters = JwtEncoderParameters.from(header, claims);
        String token = jwtEncoder.encode(jwtParameters).getTokenValue();

        LoginResponse response = new LoginResponse();
        response.setToken(token);
        return response;
    }

    @Override
    public UserInfoResponse getUserInfo(LoginUser user) {
        // 读取当前账号资料，不向前端返回密码散列。
        UserEntity account = userMapper.selectById(user.id());
        if (account == null || Boolean.TRUE.equals(account.getIsDeleted())) {
            throw new BusinessException(401, "用户不存在或已停用");
        }
        UserProfileResponse profile = new UserProfileResponse();
        profile.setId(user.id());
        profile.setUsername(user.username());
        profile.setNickname(account.getNickname());
        profile.setAvatar(user.avatar());
        profile.setIsSuperAdmin(user.isSuperAdmin());
        profile.setEmail(account.getEmail());
        profile.setPhone(account.getPhone());

        // 组装用户资料、当前权限与可见菜单。
        UserInfoResponse response = new UserInfoResponse();
        response.setUserInfo(profile);
        response.setPermissions(user.permissions());
        response.setMenus(menuService.userMenus(user));
        return response;
    }
}
