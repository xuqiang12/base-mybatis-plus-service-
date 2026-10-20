package com.base.system.modules.friend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * 分页查询亲友的请求参数。
 */
@Getter
@Setter
public class FriendQueryRequest {

    /**
     * 页码，从 1 开始；未传时查询第 1 页。
     */
    @Min(value = 1, message = "页码不能小于1")
    private Integer pageNum = 1;

    /**
     * 每页条数，默认 10 条，允许 1～100 条。
     */
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Integer pageSize = 10;

    /**
     * 姓名关键字，按包含关系查询；未传或为空白时不按姓名筛选。
     */
    private String name;

    /**
     * 性别筛选：0 未填写、1 男、2 女；未传时查询所有性别。
     */
    @Min(value = 0, message = "性别选项无效，请重新选择")
    @Max(value = 2, message = "性别选项无效，请重新选择")
    private Integer gender;
}
