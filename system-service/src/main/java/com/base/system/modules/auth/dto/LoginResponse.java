package com.base.system.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 密码登录返回结果。
 */
@Data
@Schema(description = "密码登录返回结果")
public class LoginResponse {

    @Schema(description = "后续请求使用的 Bearer Token")
    private String token;
}
