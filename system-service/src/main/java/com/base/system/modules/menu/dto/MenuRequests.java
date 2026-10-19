package com.base.system.modules.menu.dto;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

// 菜单及按钮请求；顶级菜单 parentId 为 null。
public final class MenuRequests {
    private MenuRequests() {}
    public enum MenuType { DIRECTORY, PAGE }
    public record Create(@Positive(message = "父级菜单编号无效") Integer parentId,
            @NotBlank(message = "菜单名称不能为空") @Size(max = 100, message = "菜单名称不能超过100个字符") String name,
            @Size(max = 500, message = "菜单路径不能超过500个字符") String path,
            @Size(max = 500, message = "组件路径不能超过500个字符") String component,
            @Size(max = 100, message = "图标名称不能超过100个字符") String icon, Integer sort,
            @NotNull(message = "请选择菜单类型") MenuType type) {}
    @Getter
    @Setter
    public static class Update {
        @NotNull(message = "请选择要修改的菜单") @Positive(message = "菜单编号无效") private Integer id;
        @Positive(message = "父级菜单编号无效") private Integer parentId;
        @Pattern(regexp = "(?s).*\\S.*", message = "菜单名称不能为空") @Size(max = 100, message = "菜单名称不能超过100个字符") private String name;
        @Size(max = 500, message = "菜单路径不能超过500个字符") private String path;
        @Size(max = 500, message = "组件路径不能超过500个字符") private String component;
        @Size(max = 100, message = "图标名称不能超过100个字符") private String icon;
        private Integer sort;
        private MenuType type;
        @JsonIgnore private boolean parentIdProvided;

        // 区分未提交父级和显式 null，允许把子菜单移到顶级。
        public void setParentId(Integer parentId) {
            this.parentId = parentId;
            this.parentIdProvided = true;
        }
    }
    public record CreateButton(@NotNull(message = "请选择所属菜单") @Positive(message = "菜单编号无效") Integer menuId,
            @NotBlank(message = "权限码不能为空") @Size(max = 150, message = "权限码不能超过150个字符") String permissionCode,
            @NotBlank(message = "按钮名称不能为空") @Size(max = 100, message = "按钮名称不能超过100个字符") String name, Integer sort) {}
    public record UpdateButton(@NotNull(message = "请选择要修改的按钮") @Positive(message = "按钮编号无效") Integer id,
            @NotNull(message = "请选择所属菜单") @Positive(message = "菜单编号无效") Integer menuId,
            @NotBlank(message = "权限码不能为空") @Size(max = 150, message = "权限码不能超过150个字符") String permissionCode,
            @NotBlank(message = "按钮名称不能为空") @Size(max = 100, message = "按钮名称不能超过100个字符") String name, Integer sort) {}
}
