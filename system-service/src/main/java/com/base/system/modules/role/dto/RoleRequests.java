package com.base.system.modules.role.dto;

import jakarta.validation.constraints.*;
import java.util.List;

// 角色维护和授权请求，菜单权限与操作权限分别提交。
public final class RoleRequests {
    private RoleRequests() {}
    public record Create(@NotBlank @Size(max = 100) String name) {}
    public record Update(@NotNull @Positive Integer id, @NotBlank @Size(max = 100) String name) {}
    public record Grant(@NotNull @Positive Integer id,
            @NotNull List<@NotNull @Positive Integer> menuIds,
            @NotNull List<@NotNull @Positive Integer> permissionIds) {}
}
