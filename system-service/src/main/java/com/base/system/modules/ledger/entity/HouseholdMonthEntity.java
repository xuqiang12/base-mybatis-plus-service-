package com.base.system.modules.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** 家庭月度记录主表。 */
@Getter
@Setter
@TableName("relations.ledger_household_month")
public class HouseholdMonthEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String familyId;
    @TableField("month_value")
    private String monthValue;
    private Instant createdAt;
    private Instant updatedAt;
}
