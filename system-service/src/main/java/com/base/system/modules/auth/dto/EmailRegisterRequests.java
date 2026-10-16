package com.base.system.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

// 邮箱统一去除两端空白并转小写，验证码始终按字符串处理。
public final class EmailRegisterRequests {
    private EmailRegisterRequests() {}
    public record Send(@NotBlank @Email @Size(max = 254) String email) {
        public Send { email = email == null ? null : email.trim().toLowerCase(Locale.ROOT); }
    }
    public record Register(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "[0-9]{6}", message = "验证码必须为6位数字") String code,
            @NotBlank @Size(min = 8, max = 72) String password) {
        public Register { email = email == null ? null : email.trim().toLowerCase(Locale.ROOT); }
    }
}
