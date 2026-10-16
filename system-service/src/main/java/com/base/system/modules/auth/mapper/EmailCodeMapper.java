package com.base.system.modules.auth.mapper;

import org.apache.ibatis.annotations.*;
import java.time.Instant;
import java.time.LocalDate;

// 验证码与限额查询；所有动态值使用绑定参数，行锁在事务中生效。
public interface EmailCodeMapper {
    record Code(String email, String codeHash, Instant expiresAt, int failedAttempts, boolean used) {}
    record Limit(String scopeKey, LocalDate sendDate, int sendCount, Instant lastSentAt) {}

    @Insert("INSERT INTO relations.sys_email_send_limit(scope_key,send_date,send_count,last_sent_at) "
            + "VALUES(#{key},#{date},0,#{initial}) ON CONFLICT DO NOTHING")
    int ensureLimit(@Param("key") String key, @Param("date") LocalDate date, @Param("initial") Instant initial);
    @Select("SELECT scope_key,send_date,send_count,last_sent_at FROM relations.sys_email_send_limit WHERE scope_key=#{key} FOR UPDATE")
    Limit lockLimit(String key);
    @Update("UPDATE relations.sys_email_send_limit SET send_date=#{date},send_count=#{count},last_sent_at=#{now} WHERE scope_key=#{key}")
    int countSend(@Param("key") String key, @Param("date") LocalDate date, @Param("count") int count, @Param("now") Instant now);
    @Insert("INSERT INTO relations.sys_email_code(email) VALUES(#{email}) ON CONFLICT DO NOTHING")
    int ensureCode(String email);
    @Select("SELECT email,code_hash,expires_at,failed_attempts,used FROM relations.sys_email_code WHERE email=#{email} FOR UPDATE")
    Code lockCode(String email);
    @Update("UPDATE relations.sys_email_code SET code_hash=#{hash},expires_at=#{expiry},failed_attempts=0,used=FALSE WHERE email=#{email}")
    int saveCode(@Param("email") String email, @Param("hash") String hash, @Param("expiry") Instant expiry);
    @Update("UPDATE relations.sys_email_code SET failed_attempts=failed_attempts+1 WHERE email=#{email}")
    int failed(String email);
    @Update("UPDATE relations.sys_email_code SET used=TRUE WHERE email=#{email}")
    int consume(String email);
    @Delete("DELETE FROM relations.sys_email_code WHERE expires_at < #{cutoff}")
    int clearCodes(Instant cutoff);
    @Delete("DELETE FROM relations.sys_email_send_limit WHERE last_sent_at < #{cutoff}")
    int clearLimits(Instant cutoff);
}
