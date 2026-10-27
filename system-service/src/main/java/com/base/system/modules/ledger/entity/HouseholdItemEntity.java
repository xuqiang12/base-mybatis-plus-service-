package com.base.system.modules.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** 家庭收支独立记录；同月同分类允许多笔。 */
@Getter
@Setter
@TableName("relations.ledger_household_item")
public class HouseholdItemEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long monthId;
    private String familyId;
    private Long categoryId;
    private Long projectId;
    private Boolean savingTransfer;
    private Long loanId;
    private String savingEffect = "add";
    private String type;
    private Long amountCents;
    private String note;
    private Integer createdBy;
    private String createdByName;
    private Instant createdAt;
    private Instant updatedAt;
}
