package com.base.system.modules.menu.dto;

// 菜单按钮详情，包含关联权限码。
public record ButtonResponse(Integer id, Integer menuId, String name, Integer sort,
        Integer permissionId, String permissionCode) {}
