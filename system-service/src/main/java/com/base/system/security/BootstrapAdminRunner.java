package com.base.system.security;

import com.base.system.modules.user.UserService;
import com.base.system.modules.user.dto.UserRequests;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

// 未配置初始化邮箱时跳过；配置有效且用户库为空时建立首个管理员。
@Component
@EnableConfigurationProperties(BootstrapAdminProperties.class)
@RequiredArgsConstructor
public class BootstrapAdminRunner implements ApplicationRunner {
    private final BootstrapAdminProperties properties;
    private final Validator validator;
    private final UserService users;

    @Override
    public void run(ApplicationArguments arguments) {
        if (properties.email() == null || properties.email().isBlank()) { return; }
        var request = new UserRequests.Create(properties.username(), properties.email(), properties.phone(),
                properties.password(), null, true, List.of());
        if (!validator.validate(request).isEmpty()) { throw new IllegalStateException("管理员初始化配置不完整或格式不合法"); }
        users.bootstrap(properties.username(), properties.email(), properties.phone(), properties.password());
    }
}
