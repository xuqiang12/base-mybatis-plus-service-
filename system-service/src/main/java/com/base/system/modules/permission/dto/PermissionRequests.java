package com.base.system.modules.permission.dto;

import jakarta.validation.constraints.*;

// 操作权限维护入参。
public final class PermissionRequests {
    private PermissionRequests() {}
    public record Create(@NotBlank @Size(max = 150) String code, @NotBlank @Size(max = 100) String name) {}
    public record Update(@NotNull @Positive Integer id, @NotBlank @Size(max = 150) String code,
            @NotBlank @Size(max = 100) String name) {}
}
