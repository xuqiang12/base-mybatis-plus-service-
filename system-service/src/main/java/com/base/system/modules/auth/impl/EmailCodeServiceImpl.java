package com.base.system.modules.auth.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.base.common.exception.BusinessException;
import com.base.system.modules.auth.EmailCodeProperties;
import com.base.system.modules.auth.EmailCodeService;
import com.base.system.modules.auth.dto.EmailRegisterRequest;
import com.base.system.modules.auth.dto.SendEmailCodeRequest;
import com.base.system.modules.auth.entity.EmailCodeEntity;
import com.base.system.modules.auth.entity.EmailSendLimitEntity;
import com.base.system.modules.auth.mapper.EmailCodeMapper;
import com.base.system.modules.user.entity.UserEntity;
import com.base.system.modules.user.mapper.UserMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 邮箱验证码注册服务实现。
 * 业务拒绝在事务提交后抛出，保留验证码错误次数和发送尝试次数。
 */
@Service
@EnableScheduling
@EnableConfigurationProperties(EmailCodeProperties.class)
public class EmailCodeServiceImpl implements EmailCodeService {

    private static final int SUCCESS = 200;
    private static final int INVALID_CODE = 401;
    private static final int EMAIL_ALREADY_REGISTERED = 409;
    private static final int SEND_LIMIT_EXCEEDED = 429;
    private static final int MAIL_SEND_FAILED = 503;

    @Autowired
    private EmailCodeMapper emailCodeMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailCodeProperties emailCodeProperties;

    @Autowired
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private Validator validator;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public void sendEmailCode(SendEmailCodeRequest request, String ip) {
        // 先统一邮箱格式，再按 DTO 上的注解校验参数。
        if (request.getEmail() != null) {
            request.setEmail(request.getEmail().trim().toLowerCase(Locale.ROOT));
        }
        validateRequest(request);
        String email = request.getEmail();

        // 检查验证码密钥和邮件发送配置。
        checkSecret();
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null || emailCodeProperties.getFrom() == null || emailCodeProperties.getFrom().isBlank()) {
            throw new BusinessException(503, "邮箱发送服务尚未配置");
        }
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Integer status = transactionTemplate.execute(transaction -> {
            Instant now = Instant.now();
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));

            // 按邮箱和来源 IP 分别锁定限额，检查发送间隔和每日次数。
            String emailKey = "email:" + email;
            String ipKey = "ip:" + ip;
            emailCodeMapper.insertSendLimitIfAbsent(emailKey, today, Instant.EPOCH);
            EmailSendLimitEntity emailLimit = emailCodeMapper.getSendLimitForUpdate(emailKey);
            if (emailLimit.getLastSentAt().plusSeconds(60).isAfter(now)
                    || (emailLimit.getSendDate().equals(today) && emailLimit.getSendCount() >= 10)) {
                return SEND_LIMIT_EXCEEDED;
            }

            emailCodeMapper.insertSendLimitIfAbsent(ipKey, today, Instant.EPOCH);
            EmailSendLimitEntity ipLimit = emailCodeMapper.getSendLimitForUpdate(ipKey);
            if (ipLimit.getSendDate().equals(today) && ipLimit.getSendCount() >= 50) {
                return SEND_LIMIT_EXCEEDED;
            }

            // 先记录发送尝试，邮件发送失败也计入限额。
            int emailSendCount = emailLimit.getSendDate().equals(today) ? emailLimit.getSendCount() + 1 : 1;
            int ipSendCount = ipLimit.getSendDate().equals(today) ? ipLimit.getSendCount() + 1 : 1;
            emailCodeMapper.updateSendCount(emailKey, today, emailSendCount, now);
            emailCodeMapper.updateSendCount(ipKey, today, ipSendCount, now);

            // 生成与上一条不同的六位验证码。
            EmailCodeEntity previousCode = emailCodeMapper.getCodeForUpdate(email);
            String code;
            do {
                code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
            } while (previousCode != null && createCodeHash(email, code).equals(previousCode.getCodeHash()));

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(emailCodeProperties.getFrom());
            message.setTo(email);
            message.setSubject("注册验证码");
            message.setText("您的注册验证码是：" + code + "，5分钟内有效。请勿向他人透露；若非本人操作，请忽略本邮件。");
            try {
                mailSender.send(message);
            } catch (MailException e) {
                return MAIL_SEND_FAILED;
            }

            // 邮件发送成功后再替换旧验证码，只保存摘要和过期时间。
            emailCodeMapper.insertCodeIfAbsent(email);
            emailCodeMapper.updateCode(email, createCodeHash(email, code), Instant.now().plusSeconds(300));
            return SUCCESS;
        });
        if (Integer.valueOf(SEND_LIMIT_EXCEEDED).equals(status)) {
            throw new BusinessException(429, "发送过于频繁，请稍后再试");
        }
        if (!Integer.valueOf(SUCCESS).equals(status)) {
            throw new BusinessException(503, "验证码邮件发送失败，请稍后再试");
        }
    }

    @Override
    public void register(EmailRegisterRequest request) {
        // 先统一邮箱格式、去除昵称首尾空白，再按 DTO 上的注解校验参数。
        if (request.getEmail() != null) {
            request.setEmail(request.getEmail().trim().toLowerCase(Locale.ROOT));
        }
        if (request.getNickname() != null) {
            request.setNickname(request.getNickname().strip());
        }
        validateRequest(request);

        boolean testMode = emailCodeProperties.isTestMode();
        if (!testMode) {
            checkSecret();
        }
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Integer status = transactionTemplate.execute(transaction -> {
            // TEMP_EMAIL_CODE_TEST_MODE：关闭开关后恢复原来的邮件验证码校验。
            if (testMode) {
                if (!"123456".equals(request.getCode())) {
                    return INVALID_CODE;
                }
            } else {
                // 锁定当前验证码，防止同一条验证码被并发使用。
                EmailCodeEntity emailCode = emailCodeMapper.getCodeForUpdate(request.getEmail());
                if (emailCode == null || emailCode.isUsed() || emailCode.getExpiresAt() == null
                        || !emailCode.getExpiresAt().isAfter(Instant.now()) || emailCode.getFailedAttempts() >= 5) {
                    return INVALID_CODE;
                }

                // 验证失败时累计错误次数，事务提交后再抛出业务异常。
                String actualCodeHash = createCodeHash(request.getEmail(), request.getCode());
                byte[] actualHashBytes = actualCodeHash.getBytes(StandardCharsets.US_ASCII);
                byte[] expectedHashBytes = emailCode.getCodeHash().getBytes(StandardCharsets.US_ASCII);
                if (!MessageDigest.isEqual(actualHashBytes, expectedHashBytes)) {
                    emailCodeMapper.updateFailedAttempts(request.getEmail());
                    return INVALID_CODE;
                }
                emailCodeMapper.updateCodeUsed(request.getEmail());
            }

            // 包含已删除账号；注册不能覆盖旧账号或重置其密码。
            LambdaQueryWrapper<UserEntity> query = new LambdaQueryWrapper<UserEntity>()
                    .apply("LOWER(email) = {0}", request.getEmail());
            List<UserEntity> existingUsers = userMapper.selectList(query);
            if (!existingUsers.isEmpty()) {
                return EMAIL_ALREADY_REGISTERED;
            }

            // 创建已验证邮箱的普通用户，并保存密码的 BCrypt 散列。
            UserEntity user = new UserEntity();
            user.setUsername("email_" + UUID.randomUUID().toString().replace("-", ""));
            user.setNickname(request.getNickname());
            user.setEmail(request.getEmail());
            user.setIsSuperAdmin(false);
            user.setIsDeleted(false);
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setEmailVerified(true);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(user.getCreatedAt());
            userMapper.insert(user);
            return SUCCESS;
        });
        if (Integer.valueOf(EMAIL_ALREADY_REGISTERED).equals(status)) {
            throw new BusinessException(409, "该邮箱已注册，请使用密码登录");
        }
        if (!Integer.valueOf(SUCCESS).equals(status)) {
            throw new BusinessException(401, "验证码无效、已过期或已使用");
        }
    }

    /**
     * 整理邮箱后执行请求参数校验，避免带空格的邮箱提前被 Controller 拦截。
     */
    private void validateRequest(Object request) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new BusinessException(400, violations.iterator().next().getMessage());
        }
    }

    /**
     * 检查验证码摘要密钥配置。
     */
    private void checkSecret() {
        if (emailCodeProperties.getCodeSecret() == null
                || emailCodeProperties.getCodeSecret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new BusinessException(503, "邮箱验证码服务尚未配置");
        }
    }

    /**
     * 生成与邮箱绑定的验证码摘要。
     */
    private String createCodeHash(String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            byte[] secretBytes = emailCodeProperties.getCodeSecret().getBytes(StandardCharsets.UTF_8);
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            String content = "register\n" + email + "\n" + code;
            byte[] digest = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("无法生成验证码摘要", e);
        }
    }

    /**
     * 清理七天前过期的验证码和发送限额，避免与当前验证码争锁。
     */
    @Scheduled(fixedDelay = 3_600_000, initialDelay = 3_600_000)
    public void clearExpired() {
        Instant cutoff = Instant.now().minusSeconds(7 * 86400L);
        emailCodeMapper.deleteExpiredCodes(cutoff);
        emailCodeMapper.deleteExpiredSendLimits(cutoff);
    }
}
