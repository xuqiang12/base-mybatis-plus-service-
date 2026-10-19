package com.base.system.modules.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 发件地址与验证码摘要密钥，由私有配置或环境变量提供。
 */
@Data
@ConfigurationProperties("app.email-code")
public class EmailCodeProperties {

    private String from;

    private String codeSecret;

    // TEMP_EMAIL_CODE_TEST_MODE：本机临时使用固定验证码 123456，默认关闭。
    private boolean testMode;
}
