package com.base.system;

import com.base.security.BaseSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

// 统一提供登录、用户、角色、菜单和权限管理。
@SpringBootApplication
@Import(BaseSecurityConfiguration.class)
public class SystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(SystemApplication.class, args);
    }
}
