package com.base.system.modules.user.dto;

import jakarta.validation.constraints.*;
import java.util.List;

// 用户接口入参；更新请求只修改非空字段，角色列表为空表示清空角色。
public final class UserRequests {
    private UserRequests() {}

    public record Create(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误") String phone,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Size(max = 1000) String avatar,
            Boolean isSuperAdmin,
            List<@NotNull @Positive Integer> roleIds) {}

    public record Update(
            @NotNull @Positive Integer id,
            @Pattern(regexp = "(?s).*\\S.*", message = "用户名不能为空") @Size(max = 100) String username,
            @Email @Size(min = 1, max = 254) String email,
            @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误") String phone,
            @Size(min = 8, max = 72) String password,
            @Size(max = 1000) String avatar,
            Boolean isSuperAdmin,
            List<@NotNull @Positive Integer> roleIds) {}

    public record Register(
            @NotBlank @Size(max = 100) String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误") String phone,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Size(max = 1000) String avatar) {}

    public record BindPhone(
            @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误") String phone) {}

    public record ListQuery(
            @Min(1) Integer pageNum,
            @Min(1) @Max(100) Integer pageSize,
            String username, String email, String phone, Boolean includeDeleted) {
        public ListQuery {
            pageNum = pageNum == null ? 1 : pageNum;
            pageSize = pageSize == null ? 10 : pageSize;
        }
    }
}
