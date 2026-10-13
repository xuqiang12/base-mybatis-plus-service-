package com.base.system.modules.menu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

// 菜单按钮与操作权限的绑定，数据库位于独立 relations schema。
@Getter
@Setter
@TableName("relations.sys_menu_button")
public class MenuButtonEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private Integer menuId;
    private Integer permissionId;
    private String name;
    private Integer sort;
}
