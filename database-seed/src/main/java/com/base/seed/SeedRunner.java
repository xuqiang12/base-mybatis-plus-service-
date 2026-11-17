package com.base.seed;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeedRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);
    private final SystemSeedService seeds;
    private final SeedAdminProperties admin;

    @Override
    public void run(ApplicationArguments arguments) {
        int applied = seeds.initialize(admin);
        log.info("数据库初始化完成：执行 {} 个新模块版本；已完成版本跳过", applied);
    }
}
