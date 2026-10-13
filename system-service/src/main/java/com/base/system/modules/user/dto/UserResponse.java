package com.base.system.modules.user.dto;

import java.time.Instant;
import java.util.List;

// 显式选择可返回的用户字段，不包含密码散列。
public record UserResponse(Integer id, String username, String email, String phone, String avatar,
        boolean isSuperAdmin, boolean isDeleted, Instant deletedAt, Instant createdAt, Instant updatedAt,
        List<Integer> roleIds, List<String> roleNames, String roleNamesText) {}
