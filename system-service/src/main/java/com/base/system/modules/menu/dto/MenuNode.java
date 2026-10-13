package com.base.system.modules.menu.dto;

import java.util.List;

// 返回菜单树；管理树的按钮为详情对象，用户菜单的按钮为权限码。
public record MenuNode(Integer id, Integer parentId, String name, String path, String component,
        String icon, String type, Integer sort, List<?> buttons, List<MenuNode> children) {}
