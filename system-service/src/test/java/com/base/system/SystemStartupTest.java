package com.base.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

// 即使保留旧参数和本机管理员配置，业务服务也不应再承担初始化职责。
@SpringBootTest(classes = SystemApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        args = "--seed", properties = {
        "spring.datasource.url=jdbc:h2:mem:startup-without-seed;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "app.seed-admin.username=administrator", "app.seed-admin.email=seed@example.test",
        "app.seed-admin.password=seed-test-password", "app.bootstrap-admin.username=bootstrap",
        "app.bootstrap-admin.email=bootstrap@example.test", "app.bootstrap-admin.password=bootstrap-test-password"})
@ActiveProfiles("test")
class SystemStartupTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void normalServiceNeverInitializesAccountsOrSystemSeed() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_user", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_seed_history", Integer.class)).isZero();
    }
}
