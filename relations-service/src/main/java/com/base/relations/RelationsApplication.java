package com.base.relations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import com.base.security.BaseSecurityConfiguration;

// 启动亲友业务服务；身份和授权由统一管理服务提供。
@SpringBootApplication
@Import(BaseSecurityConfiguration.class)
public class RelationsApplication {
    public static void main(String[] args) {
        SpringApplication.run(RelationsApplication.class, args);
    }
}
