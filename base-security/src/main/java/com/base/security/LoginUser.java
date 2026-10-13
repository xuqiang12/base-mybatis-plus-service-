package com.base.security;

import java.util.List;

// 各服务共用的当前身份和最新角色、权限，不携带密码。
public record LoginUser(Integer id, String username, String email, String avatar,
        boolean isSuperAdmin, List<String> roles, List<String> permissions) {}
