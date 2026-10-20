package com.base.system.modules.friend.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;


/**
 * 新增亲友的请求参数。
 */
@Getter
@Setter
public class FriendCreateRequest {

    /**
     * 姓名，必填。
     */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 30, message = "姓名不能超过30个字符")
    private String name;

    /**
     * 称谓，选填。
     */
    @Size(max = 30, message = "称谓不能超过30个字符")
    @JsonSetter(nulls = Nulls.SKIP)
    private String appellation = "";

    /**
     * 性别：0 未填写，1 男，2 女；选填。
     */
    @Min(value = 0, message = "性别只能填写0、1或2")
    @Max(value = 2, message = "性别只能填写0、1或2")
    @JsonSetter(nulls = Nulls.SKIP)
    private Integer gender = 0;

    /**
     * 备注，选填。
     */
    @Size(max = 200, message = "备注不能超过200个字符")
    private String remark = "";
}
