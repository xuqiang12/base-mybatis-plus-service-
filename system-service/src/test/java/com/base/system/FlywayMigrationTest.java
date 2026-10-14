package com.base.system;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 使用隔离数据库验证分表迁移、完整注释和重复启动，不连接本机业务数据库。
class FlywayMigrationTest {
    @Test
    void initializesDocumentedTablesAndSkipsAppliedMigrations() {
        var datasource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        var flyway = Flyway.configure().dataSource(datasource)
                .defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").load();
        var jdbc = new JdbcTemplate(datasource);

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(9);
        assertThat(jdbc.queryForList("SELECT \"version\" FROM relations.flyway_schema_history "
                + "WHERE \"type\"='SQL' AND \"success\"=TRUE ORDER BY \"installed_rank\"", String.class))
                .containsExactly("1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8", "1.9");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema='relations' AND table_name LIKE 'sys_%'", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema='relations' AND table_name LIKE 'sys_%'", Integer.class)).isEqualTo(37);
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema='relations' AND table_name LIKE 'sys_%' "
                + "AND TRIM(COALESCE(remarks,''))=''", String.class)).isEmpty();
        assertThat(jdbc.queryForList("SELECT table_name || '.' || column_name FROM information_schema.columns "
                + "WHERE table_schema='relations' AND table_name LIKE 'sys_%' "
                + "AND TRIM(COALESCE(remarks,''))=''", String.class)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_permission", Integer.class)).isEqualTo(18);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_user", Integer.class)).isZero();

        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_permission", Integer.class)).isEqualTo(18);
    }
}
