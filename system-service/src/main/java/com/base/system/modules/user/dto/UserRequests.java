package com.base.system.modules.user.dto;

import jakarta.validation.constraints.*;
import java.util.List;

// 用户接口入参；更新请求只修改非空字段，角色列表为空表示清空角色。
public final class UserRequests {
    private UserRequests() {}

    public record Create(
            @NotBlank(message = "用户名不能为空") @Size(max = 100, message = "用户名不能超过100个字符") String username,
            @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") @Size(max = 254, message = "邮箱不能超过254个字符") String email,
            @NotBlank(message = "手机号不能为空") @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误") String phone,
            @NotBlank(message = "密码不能为空")
            @Size(min = 8, max = 72, message = "密码长度必须为8～72个字符")
            @Pattern(regexp = "[!-~]+", message = "密码仅支持英文字母、数字和英文半角符号，不能包含空格") String password,
            @Size(max = 1000, message = "头像地址不能超过1000个字符") String avatar,
            Boolean isSuperAdmin,
            List<@NotNull(message = "角色编号不能为空") @Positive(message = "角色编号无效") Integer> roleIds) {}

    public record Update(
            @NotNull(message = "请选择要修改的用户") @Positive(message = "用户编号无效") Integer id,
            @Pattern(regexp = "(?s).*\\S.*", message = "用户名不能为空") @Size(max = 100, message = "用户名不能超过100个字符") String username,
            @Email(message = "邮箱格式不正确") @Size(min = 1, max = 254, message = "邮箱长度必须为1～254个字符") String email,
            @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误") String phone,
            @Size(min = 8, max = 72, message = "密码长度必须为8～72个字符")
            @Pattern(regexp = "[!-~]+", message = "密码仅支持英文字母、数字和英文半角符号，不能包含空格") String password,
            @Size(max = 1000, message = "头像地址不能超过1000个字符") String avatar,
            Boolean isSuperAdmin,
            List<@NotNull(message = "角色编号不能为空") @Positive(message = "角色编号无效") Integer> roleIds) {}

    public record Register(
            @NotBlank(message = "用户名不能为空") @Size(max = 100, message = "用户名不能超过100个字符") String username,
            @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") @Size(max = 254, message = "邮箱不能超过254个字符") String email,
            @NotBlank(message = "手机号不能为空") @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误") String phone,
            @NotBlank(message = "密码不能为空")
            @Size(min = 8, max = 72, message = "密码长度必须为8～72个字符")
            @Pattern(regexp = "[!-~]+", message = "密码仅支持英文字母、数字和英文半角符号，不能包含空格") String password,
            @Size(max = 1000, message = "头像地址不能超过1000个字符") String avatar) {}

    public record BindPhone(
            @NotBlank(message = "手机号不能为空") @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误") String phone) {}

    public record ListQuery(
            @Min(value = 1, message = "页码不能小于1") Integer pageNum,
            @Min(value = 1, message = "每页条数不能小于1") @Max(value = 100, message = "每页条数不能超过100") Integer pageSize,
            String username, String email, String phone, Boolean includeDeleted) {
        public ListQuery {
            pageNum = pageNum == null ? 1 : pageNum;
            pageSize = pageSize == null ? 10 : pageSize;
        }
    }
}
