package com.base.system.modules.menu.dto;

import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

// 菜单及按钮请求；顶级菜单 parentId 为 null。
public final class MenuRequests {
    private MenuRequests() {}
    public enum MenuType { DIRECTORY, PAGE }
    public record Create(@Positive Integer parentId, @NotBlank @Size(max = 100) String name,
            @Size(max = 500) String path, @Size(max = 500) String component,
            @Size(max = 100) String icon, Integer sort, @NotNull MenuType type) {}
    @Getter
    @Setter
    public static class Update {
        @NotNull @Positive private Integer id;
        @Positive private Integer parentId;
        @Pattern(regexp = "(?s).*\\S.*") @Size(max = 100) private String name;
        @Size(max = 500) private String path;
        @Size(max = 500) private String component;
        @Size(max = 100) private String icon;
        private Integer sort;
        private MenuType type;
        @JsonIgnore private boolean parentIdProvided;

        // 区分未提交父级和显式 null，允许把子菜单移到顶级。
        public void setParentId(Integer parentId) {
            this.parentId = parentId;
            this.parentIdProvided = true;
        }
    }
    public record CreateButton(@NotNull @Positive Integer menuId,
            @NotBlank @Size(max = 150) String permissionCode, @NotBlank @Size(max = 100) String name, Integer sort) {}
    public record UpdateButton(@NotNull @Positive Integer id, @NotNull @Positive Integer menuId,
            @NotBlank @Size(max = 150) String permissionCode, @NotBlank @Size(max = 100) String name, Integer sort) {}
}
