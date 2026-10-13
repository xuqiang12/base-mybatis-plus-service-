package com.base.security;

import org.springframework.security.core.AuthenticationException;

// 身份服务故障不能降级为允许访问，也不应误报为账号失效。
public class IdentityServiceUnavailableException extends AuthenticationException {
    public IdentityServiceUnavailableException(Throwable cause) {
        super("统一身份服务暂不可用", cause);
    }
}
