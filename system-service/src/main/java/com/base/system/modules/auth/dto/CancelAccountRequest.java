package com.base.system.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "账号注销请求")
public class CancelAccountRequest {

    @Schema(description = "当前登录密码")
    @NotBlank(message = "密码不能为空")
    @Size(max = 72, message = "密码不能超过72个字符")
    private String password;
}
