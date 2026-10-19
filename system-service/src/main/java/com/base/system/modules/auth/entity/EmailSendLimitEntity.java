package com.base.system.modules.auth.entity;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 按邮箱或来源 IP 记录的验证码发送限额。
 */
@Data
public class EmailSendLimitEntity {

    private String scopeKey;

    private LocalDate sendDate;

    private int sendCount;

    private Instant lastSentAt;
}
