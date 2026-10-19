package com.base.system.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送注册验证码请求参数。
 */
@Data
@Schema(description = "发送注册验证码请求")
public class SendEmailCodeRequest {

    @Schema(description = "接收注册验证码的邮箱")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱不能超过254个字符")
    private String email;

}
