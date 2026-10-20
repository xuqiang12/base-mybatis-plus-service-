package com.base.system.modules.friend.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 修改亲友的请求参数。
 * 按整份表单提交：未传的选填字段使用下方默认值，会参与更新。
 */
@Getter
@Setter
public class FriendUpdateRequest {

    /**
     * 要修改的亲友编号，必填；所属家庭由后端根据登录用户检查。
     */
    @NotNull(message = "请选择要修改的亲友")
    @Positive(message = "亲友编号无效")
    private Long id;

    /**
     * 姓名，必填，最多 30 个字符。
     */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 30, message = "姓名不能超过30个字符")
    private String name;

    /**
     * 称谓，最多 30 个字符；未传时为空字符串。
     * Nulls.SKIP 表示收到 JSON 的 null 时跳过赋值，保留空字符串默认值。
     */
    @Size(max = 30, message = "称谓不能超过30个字符")
    @JsonSetter(nulls = Nulls.SKIP)
    private String appellation = "";

    /**
     * 性别：0 未填写、1 男、2 女；未传或传 null 时使用默认值 0。
     */
    @Min(value = 0, message = "性别选项无效，请重新选择")
    @Max(value = 2, message = "性别选项无效，请重新选择")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer gender = 0;

    /**
     * 备注，最多 200 个字符；未传时为空字符串。
     */
    @Size(max = 200, message = "备注不能超过200个字符")
    private String remark = "";
}
