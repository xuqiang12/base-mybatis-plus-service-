package com.base.system.modules.friend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 返回给前端的亲友资料。
 */
@Getter
@Setter
public class FriendResponse {

    /**
     * 亲友编号。
     */
    private Long id;

    /**
     * 所属家庭编号。
     */
    private String familyId;

    /**
     * 姓名。
     */
    private String name;

    /**
     * 称谓。
     */
    private String appellation;

    /**
     * 性别：0 未填写，1 男，2 女。
     */
    private Integer gender;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 创建时间。
     */
    private Instant createdAt;

    /**
     * 最后修改时间。
     */
    private Instant updatedAt;
}
