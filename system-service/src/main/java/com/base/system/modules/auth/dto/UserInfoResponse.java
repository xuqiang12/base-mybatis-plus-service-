package com.base.system.modules.auth.dto;

import com.base.system.modules.menu.dto.MenuNode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 当前用户资料、权限及可见菜单返回结果。
 */
@Data
@Schema(description = "当前用户信息返回结果")
public class UserInfoResponse {

    @Schema(description = "用户基本资料")
    private UserProfileResponse userInfo;

    @Schema(description = "当前操作权限码")
    private List<String> permissions;

    @Schema(description = "当前可见菜单树")
    private List<MenuNode> menus;
}
