package com.base.system.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// account 接收邮箱或手机号；email 保留原接口已使用的邮箱字段名。
public record LoginRequest(@NotBlank @Size(max = 254) @JsonAlias("email") String account,
        @NotBlank @Size(max = 72) String password) {}
