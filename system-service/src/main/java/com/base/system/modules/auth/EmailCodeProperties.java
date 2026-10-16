package com.base.system.modules.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 发件地址与验证码摘要密钥均从私有配置或环境变量提供。
@ConfigurationProperties("app.email-code")
public record EmailCodeProperties(String from, String codeSecret) {}
