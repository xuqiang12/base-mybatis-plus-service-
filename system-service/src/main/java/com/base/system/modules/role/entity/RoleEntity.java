package com.base.system.modules.role.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

// 角色信息，数据库位于独立 relations schema。
@Getter
@Setter
@TableName("relations.sys_role")
public class RoleEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String name;
}
