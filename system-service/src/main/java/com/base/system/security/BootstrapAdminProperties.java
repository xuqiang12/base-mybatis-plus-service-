package com.base.system.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 首个管理员的显式初始化配置，禁止使用默认密码。
@ConfigurationProperties("app.bootstrap-admin")
public record BootstrapAdminProperties(String username, String email, String phone, String password) {}
