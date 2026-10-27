package com.base.system.modules.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** 借贷流水实体。 */
@Getter
@Setter
@TableName("relations.ledger_loan")
public class LoanEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String familyId;
    private String direction;
    private Long friendId;
    private Long projectId;
    private String name;
    private String appellation;
    private Long amountCents;
    private LocalDate date;
    private String note;
    private Integer createdBy;
    private String createdByName;
    private Instant createdAt;
    private Instant updatedAt;
}
