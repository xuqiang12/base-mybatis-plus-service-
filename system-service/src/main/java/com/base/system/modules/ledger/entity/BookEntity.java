package com.base.system.modules.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** 收礼账簿实体。 */
@Getter
@Setter
@TableName("relations.ledger_book")
public class BookEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String familyId;
    private String name;
    private LocalDate date;
    private String note;
    private Instant createdAt;
    private Instant updatedAt;
}
