package com.base.system.modules.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

// 用户身份与软删除信息，数据库位于独立 relations schema。
@Getter
@Setter
@TableName("relations.sys_user")
public class UserEntity {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String password;
    private Boolean emailVerified;
    private String avatar;
    private Boolean isSuperAdmin;
    private Boolean isDeleted;
    private Instant deletedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
