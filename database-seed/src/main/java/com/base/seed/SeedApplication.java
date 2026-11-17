package com.base.seed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// 独立初始化入口：只加载本包，不启动业务服务或监听端口。
@SpringBootApplication
@EnableConfigurationProperties(SeedAdminProperties.class)
public class SeedApplication {
    public static void main(String[] args) {
        var application = new SpringApplication(SeedApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(java.util.Map.of("spring.config.name", "database-seed"));
        try (var context = application.run(args)) {
            // ApplicationRunner 已执行完毕，关闭连接池后退出。
        }
    }

    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
