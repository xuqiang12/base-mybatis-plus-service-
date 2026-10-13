package com.base.system.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.core.io.Resource;
import java.time.Duration;

// 私钥仅由统一管理服务持有。
@Validated
@ConfigurationProperties("security.jwt")
public record JwtSigningProperties(@NotNull Resource privateKey,
        @NotBlank String issuer, @NotNull Duration ttl) {
    public JwtSigningProperties {
        if (ttl != null && (ttl.isNegative() || ttl.isZero())) {
            throw new IllegalArgumentException("JWT 有效期必须为正数");
        }
    }
}
