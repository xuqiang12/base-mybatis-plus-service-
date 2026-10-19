package com.base.system.modules.auth.mapper;

import com.base.system.modules.auth.entity.EmailCodeEntity;
import com.base.system.modules.auth.entity.EmailSendLimitEntity;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 邮箱验证码及发送限额数据库访问。
 * 加锁查询必须在事务中调用，SQL 位于 mapper/auth/EmailCodeMapper.xml。
 */
public interface EmailCodeMapper {

    /**
     * 发送限额不存在时创建，已有记录保持不变。
     *
     * @param scopeKey 邮箱或 IP 对应的限额键
     * @param sendDate 发送日期
     * @param initialTime 初始发送时间
     * @return 受影响行数
     */
    int insertSendLimitIfAbsent(@Param("scopeKey") String scopeKey,
            @Param("sendDate") LocalDate sendDate, @Param("initialTime") Instant initialTime);

    /**
     * 查询并锁定邮箱或 IP 的发送限额。
     *
     * @param scopeKey 邮箱或 IP 对应的限额键
     * @return 发送限额记录
     */
    EmailSendLimitEntity getSendLimitForUpdate(@Param("scopeKey") String scopeKey);

    /**
     * 更新发送日期、累计次数和最近发送时间。
     *
     * @param scopeKey 邮箱或 IP 对应的限额键
     * @param sendDate 发送日期
     * @param sendCount 当日累计发送次数
     * @param lastSentAt 最近发送时间
     * @return 受影响行数
     */
    int updateSendCount(@Param("scopeKey") String scopeKey, @Param("sendDate") LocalDate sendDate,
            @Param("sendCount") int sendCount, @Param("lastSentAt") Instant lastSentAt);

    /**
     * 验证码记录不存在时创建，已有记录保持不变。
     *
     * @param email 邮箱
     * @return 受影响行数
     */
    int insertCodeIfAbsent(@Param("email") String email);

    /**
     * 查询并锁定邮箱验证码，防止并发消费。
     *
     * @param email 邮箱
     * @return 验证码记录，不存在时返回 null
     */
    EmailCodeEntity getCodeForUpdate(@Param("email") String email);

    /**
     * 替换验证码摘要和过期时间，并重置使用状态、错误次数。
     *
     * @param email 邮箱
     * @param codeHash 验证码摘要
     * @param expiresAt 过期时间
     * @return 受影响行数
     */
    int updateCode(@Param("email") String email, @Param("codeHash") String codeHash,
            @Param("expiresAt") Instant expiresAt);

    /**
     * 累计一次验证码错误。
     *
     * @param email 邮箱
     * @return 受影响行数
     */
    int updateFailedAttempts(@Param("email") String email);

    /**
     * 将验证码标记为已使用。
     *
     * @param email 邮箱
     * @return 受影响行数
     */
    int updateCodeUsed(@Param("email") String email);

    /**
     * 删除早于指定时间过期的验证码。
     *
     * @param cutoff 清理截止时间
     * @return 受影响行数
     */
    int deleteExpiredCodes(@Param("cutoff") Instant cutoff);

    /**
     * 删除早于指定时间最后发送的限额记录。
     *
     * @param cutoff 清理截止时间
     * @return 受影响行数
     */
    int deleteExpiredSendLimits(@Param("cutoff") Instant cutoff);
}
