package com.base.system.modules.friend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 亲友档案，对应数据库中的 relations.ledger_friend 表。xi
 */
@Getter
@Setter
@TableName("relations.ledger_friend")
public class FriendEntity {

    /**
     * 亲友编号，由数据库自动生成。
     */
    @TableId(type = IdType.AUTO)
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
     * 称谓，例如表哥、同事。
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
