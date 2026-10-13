package com.base.common.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;

// 确认 HTTP 服务可响应，不代表数据库和外部依赖健康。
@RestController
public class HealthController {
    private final String serviceName;

    public HealthController(@Value("${spring.application.name:application}") String serviceName) {
        this.serviceName = serviceName;
    }

    @GetMapping("/health")
    public Map<String, String> health() { return Map.of("service", serviceName, "status", "UP"); }
}
