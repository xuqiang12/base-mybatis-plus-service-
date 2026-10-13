package com.base.relations.security;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties("security.identity")
public record IdentityClientProperties(@NotNull URI accessUrl, @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout) {}
