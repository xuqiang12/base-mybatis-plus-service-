package com.base.system.modules.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** 月度分类合计实体。 */
@Getter
@Setter
@TableName("relations.ledger_household_item")
public class HouseholdItemEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long monthId;
    private String familyId;
    private Long categoryId;
    // 改回现金时必须把原项目编号更新为空，不能沿用 MyBatis 默认忽略空值的策略。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long projectId;
    private String type;
    private Long amountCents;
    private String note;
    private Integer createdBy;
    private String createdByName;
    private Instant createdAt;
    private Instant updatedAt;
}
