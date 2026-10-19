package com.base.system.modules.auth;

import com.base.system.modules.auth.dto.EmailRegisterRequest;
import com.base.system.modules.auth.dto.SendEmailCodeRequest;

/**
 * 邮箱验证码注册服务。
 */
public interface EmailCodeService {

    /**
     * 发送注册验证码。
     *
     * @param request 发送验证码请求参数
     * @param ip 请求来源 IP
     */
    void sendEmailCode(SendEmailCodeRequest request, String ip);

    /**
     * 校验邮箱验证码并注册普通用户。
     *
     * @param request 邮箱注册请求参数
     */
    void register(EmailRegisterRequest request);
}
