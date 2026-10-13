package com.base.system.modules.permission.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

// 操作权限码，数据库位于独立 relations schema。
@Getter
@Setter
@TableName("relations.sys_permission")
public class PermissionEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String code;
    private String name;
}
