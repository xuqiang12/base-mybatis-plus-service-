package com.base.system.modules.menu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

// 菜单和父子关系，数据库位于独立 relations schema。
@Getter
@Setter
@TableName("relations.sys_menu")
public class MenuEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer parentId;
    private String name;
    private String path;
    private String component;
    private String icon;
    private Integer sort;
    private String type;
    private Instant createdAt;
    private Instant updatedAt;
}
