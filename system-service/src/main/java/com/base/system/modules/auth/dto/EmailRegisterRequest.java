package com.base.system.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 邮箱验证码注册请求参数。
 */
@Data
@Schema(description = "邮箱注册请求")
public class EmailRegisterRequest {

    @Schema(description = "昵称，允许重复，去除首尾空白后最多100个字符", example = "小明")
    @NotBlank(message = "昵称不能为空")
    @Size(max = 100, message = "昵称不能超过100个字符")
    private String nickname;

    @Schema(description = "注册邮箱")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 254, message = "邮箱不能超过254个字符")
    private String email;

    @Schema(description = "六位数字验证码，使用字符串保留开头的0", example = "012345")
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "[0-9]{6}", message = "验证码必须为6位数字")
    private String code;

    @Schema(description = "密码，8～72个字符，仅支持英文字母、数字和英文半角符号，不能包含空格")
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 72, message = "密码长度必须为8～72个字符")
    @Pattern(regexp = "[!-~]+", message = "密码仅支持英文字母、数字和英文半角符号，不能包含空格")
    private String password;

}
