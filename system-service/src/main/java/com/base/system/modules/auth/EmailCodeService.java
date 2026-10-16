package com.base.system.modules.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.common.exception.BusinessException;
import com.base.system.modules.auth.dto.EmailRegisterRequests;
import com.base.system.modules.auth.mapper.EmailCodeMapper;
import com.base.system.modules.user.entity.UserEntity;
import com.base.system.modules.user.mapper.UserMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

// 复用数据库串行化同邮箱操作；业务拒绝在事务提交后抛出，避免错误次数被回滚。
@Service
@EnableScheduling
@EnableConfigurationProperties(EmailCodeProperties.class)
public class EmailCodeService {
    private final EmailCodeMapper codes;
    private final UserMapper users;
    private final PasswordEncoder passwords;
    private final EmailCodeProperties properties;
    private final ObjectProvider<JavaMailSender> sender;
    private final TransactionTemplate tx;
    private final SecureRandom random = new SecureRandom();

    public EmailCodeService(EmailCodeMapper codes, UserMapper users, PasswordEncoder passwords,
            EmailCodeProperties properties, ObjectProvider<JavaMailSender> sender, PlatformTransactionManager transactions) {
        this.codes = codes; this.users = users; this.passwords = passwords;
        this.properties = properties; this.sender = sender; this.tx = new TransactionTemplate(transactions);
    }

    public String send(String email, String ip) {
        checkSecret();
        var mail = sender.getIfAvailable();
        if (mail == null || properties.from() == null || properties.from().isBlank()) {
            throw new BusinessException(503, "邮箱发送服务尚未配置");
        }
        Integer status = tx.execute(transaction -> {
            Instant now = Instant.now();
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
            String emailKey = "email:" + email;
            String ipKey = "ip:" + ip;
            codes.ensureLimit(emailKey, today, Instant.EPOCH);
            var emailLimit = codes.lockLimit(emailKey);
            if (emailLimit.lastSentAt().plusSeconds(60).isAfter(now)
                    || (emailLimit.sendDate().equals(today) && emailLimit.sendCount() >= 10)) { return 429; }
            codes.ensureLimit(ipKey, today, Instant.EPOCH);
            var ipLimit = codes.lockLimit(ipKey);
            if (ipLimit.sendDate().equals(today) && ipLimit.sendCount() >= 50) { return 429; }
            codes.countSend(emailKey, today, emailLimit.sendDate().equals(today) ? emailLimit.sendCount() + 1 : 1, now);
            codes.countSend(ipKey, today, ipLimit.sendDate().equals(today) ? ipLimit.sendCount() + 1 : 1, now);
            var previous = codes.lockCode(email);
            String code;
            do { code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000)); }
            while (previous != null && hash(email, code).equals(previous.codeHash()));
            var message = new SimpleMailMessage();
            message.setFrom(properties.from()); message.setTo(email); message.setSubject("注册验证码");
            message.setText("您的注册验证码是：" + code + "，5分钟内有效。请勿向他人透露；若非本人操作，请忽略本邮件。");
            try { mail.send(message); }
            catch (MailException e) { return 503; }
            codes.ensureCode(email);
            codes.saveCode(email, hash(email, code), Instant.now().plusSeconds(300));
            return 200;
        });
        if (Integer.valueOf(429).equals(status)) { throw new BusinessException(429, "发送过于频繁，请稍后再试"); }
        if (!Integer.valueOf(200).equals(status)) { throw new BusinessException(503, "验证码邮件发送失败，请稍后再试"); }
        return "验证码已发送，请查收邮箱";
    }

    public String register(EmailRegisterRequests.Register request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(400, "密码不能超过72字节");
        }
        checkSecret();
        Integer status = tx.execute(transaction -> {
            var code = codes.lockCode(request.email());
            if (code == null || code.used() || code.expiresAt() == null
                    || !code.expiresAt().isAfter(Instant.now()) || code.failedAttempts() >= 5) { return 401; }
            if (!MessageDigest.isEqual(hash(request.email(), request.code()).getBytes(StandardCharsets.US_ASCII),
                    code.codeHash().getBytes(StandardCharsets.US_ASCII))) {
                codes.failed(request.email()); return 401;
            }
            codes.consume(request.email());
            // 包含已删除账号；注册不能覆盖旧账号或重置其密码。
            var matching = users.selectList(new LambdaQueryWrapper<UserEntity>().apply("LOWER(email) = {0}", request.email()));
            if (!matching.isEmpty()) { return 409; }
            var user = new UserEntity();
            user.setUsername("email_" + UUID.randomUUID().toString().replace("-", ""));
            user.setEmail(request.email()); user.setIsSuperAdmin(false); user.setIsDeleted(false);
            user.setPassword(passwords.encode(request.password())); user.setEmailVerified(true);
            user.setCreatedAt(Instant.now()); user.setUpdatedAt(user.getCreatedAt());
            users.insert(user);
            return 200;
        });
        if (Integer.valueOf(409).equals(status)) { throw new BusinessException(409, "该邮箱已注册，请使用密码登录"); }
        if (!Integer.valueOf(200).equals(status)) { throw new BusinessException(401, "验证码无效、已过期或已使用"); }
        return "注册成功";
    }

    private void checkSecret() {
        if (properties.codeSecret() == null || properties.codeSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new BusinessException(503, "邮箱验证码服务尚未配置");
        }
    }

    private String hash(String email, String code) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.codeSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("register\n" + email + "\n" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) { throw new IllegalStateException("无法生成验证码摘要", e); }
    }

    // 保留七天用于排查限流；只清理早已过期的数据，避免和当前验证码争锁。
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void clearExpired() {
        Instant cutoff = Instant.now().minusSeconds(7 * 86400L);
        codes.clearCodes(cutoff); codes.clearLimits(cutoff);
    }
}
