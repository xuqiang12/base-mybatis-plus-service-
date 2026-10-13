package com.base.system.modules.role.dto;

import java.util.List;

// 角色列表和详情的返回结构。
public final class RoleResponses {
    private RoleResponses() {}
    public record Summary(Integer id, String name, long userCount) {}
    public record Detail(Integer id, String name, List<Integer> menuIds, List<Integer> permissionIds) {}
}
