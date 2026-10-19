package com.base.system.modules.role.dto;

import jakarta.validation.constraints.*;
import java.util.List;

// 角色维护和授权请求，菜单权限与操作权限分别提交。
public final class RoleRequests {
    private RoleRequests() {}
    public record Create(@NotBlank(message = "角色名称不能为空") @Size(max = 100, message = "角色名称不能超过100个字符") String name) {}
    public record Update(@NotNull(message = "请选择要修改的角色") @Positive(message = "角色编号无效") Integer id,
            @NotBlank(message = "角色名称不能为空") @Size(max = 100, message = "角色名称不能超过100个字符") String name) {}
    public record Grant(@NotNull(message = "请选择要授权的角色") @Positive(message = "角色编号无效") Integer id,
            @NotNull(message = "未提交菜单列表") List<@NotNull(message = "菜单编号不能为空") @Positive(message = "菜单编号无效") Integer> menuIds,
            @NotNull(message = "未提交权限列表") List<@NotNull(message = "权限编号不能为空") @Positive(message = "权限编号无效") Integer> permissionIds) {}
}
