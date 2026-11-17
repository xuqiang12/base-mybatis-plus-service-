package com.base.seed;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;

// 只在首次运行初始化程序创建管理员时使用，不提供内置密码。
@ConfigurationProperties("app.seed-admin")
public record SeedAdminProperties(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) @Pattern(regexp = "[!-~]+") String password) {}
