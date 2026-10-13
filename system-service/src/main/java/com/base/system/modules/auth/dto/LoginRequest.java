package com.base.system.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 邮箱密码登录请求，密码不写入日志。
public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 72) String password) {}
