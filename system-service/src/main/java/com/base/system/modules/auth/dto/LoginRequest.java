package com.base.system.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 邮箱或手机号密码登录请求参数。
 */
@Data
@Schema(description = "邮箱或手机号密码登录请求")
public class LoginRequest {

    @Schema(description = "登录账号，可以是邮箱或手机号；兼容原email字段")
    @NotBlank(message = "登录账号不能为空")
    @Size(max = 254, message = "登录账号不能超过254个字符")
    @JsonAlias("email")
    private String account;

    @Schema(description = "登录密码")
    @NotBlank(message = "密码不能为空")
    @Size(max = 72, message = "密码不能超过72个字符")
    private String password;
}
