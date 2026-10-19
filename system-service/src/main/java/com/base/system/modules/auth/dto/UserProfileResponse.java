package com.base.system.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 当前用户基本资料，不包含密码散列。
 */
@Data
@Schema(description = "当前用户基本资料")
public class UserProfileResponse {

    @Schema(description = "用户 ID")
    private Integer id;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "昵称")
    private String nickname;

    @Schema(description = "头像")
    private String avatar;

    @Schema(description = "是否超级管理员")
    private Boolean isSuperAdmin;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "手机号")
    private String phone;
}
