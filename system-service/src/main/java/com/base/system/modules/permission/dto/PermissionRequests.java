package com.base.system.modules.permission.dto;

import jakarta.validation.constraints.*;

// 操作权限维护入参。
public final class PermissionRequests {
    private PermissionRequests() {}
    public record Create(@NotBlank(message = "权限码不能为空") @Size(max = 150, message = "权限码不能超过150个字符") String code,
            @NotBlank(message = "权限名称不能为空") @Size(max = 100, message = "权限名称不能超过100个字符") String name) {}
    public record Update(@NotNull(message = "请选择要修改的权限") @Positive(message = "权限编号无效") Integer id,
            @NotBlank(message = "权限码不能为空") @Size(max = 150, message = "权限码不能超过150个字符") String code,
            @NotBlank(message = "权限名称不能为空") @Size(max = 100, message = "权限名称不能超过100个字符") String name) {}
}
