package com.base.system.modules.auth.entity;

import lombok.Data;

import java.time.Instant;

/**
 * 邮箱最近一次成功发送的注册验证码。
 */
@Data
public class EmailCodeEntity {

    private String email;

    // 仅保存验证码摘要，不保存明文。
    private String codeHash;

    private Instant expiresAt;

    private int failedAttempts;

    private boolean used;
}
