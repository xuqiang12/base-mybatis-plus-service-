package com.base.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("security.jwt")
public record JwtVerificationProperties(@NotBlank String issuer, @NotNull Resource publicKey) {}
