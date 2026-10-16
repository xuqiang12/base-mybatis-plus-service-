package com.base.system;

import com.base.security.BaseSecurityConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

// 唯一应用入口；账号权限和后续亲友业务按 modules 内的业务目录组织。
@SpringBootApplication
@Import(BaseSecurityConfiguration.class)
public class SystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(SystemApplication.class, args);
    }
}
