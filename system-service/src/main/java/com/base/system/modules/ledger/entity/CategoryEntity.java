package com.base.system.modules.ledger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** 家庭收支分类实体。 */
@Getter
@Setter
@TableName("relations.ledger_category")
public class CategoryEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String familyId;
    private String type;
    private String name;
    private String icon;
    private Boolean active;
    private Instant deletedAt;
    private Integer sortOrder;
    private Instant createdAt;
    private Instant updatedAt;
}
