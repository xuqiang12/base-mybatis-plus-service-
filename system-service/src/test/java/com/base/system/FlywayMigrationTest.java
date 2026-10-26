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
    void removesPrebuiltLedgerTablesWithoutChangingAccountsOrFamilies() {
        var datasource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").target("2.3").load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        jdbc.update("INSERT INTO relations.sys_user(username,email,password) VALUES ('learner','learner@example.test','existing-hash')");
        Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").target("3.1").load().migrate();
        var users = jdbc.queryForList("SELECT * FROM relations.sys_user ORDER BY id");
        var families = jdbc.queryForList("SELECT * FROM relations.ledger_family ORDER BY id");
        var members = jdbc.queryForList("SELECT * FROM relations.ledger_member ORDER BY user_id");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_category", Integer.class)).isEqualTo(11);

        var cleanup = Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").target("3.2").load();
        assertThat(cleanup.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema='relations' AND table_name LIKE 'ledger_%' ORDER BY table_name", String.class))
                .containsExactly("ledger_family", "ledger_member");
        assertThat(jdbc.queryForList("SELECT * FROM relations.sys_user ORDER BY id")).isEqualTo(users);
        assertThat(jdbc.queryForList("SELECT * FROM relations.ledger_family ORDER BY id")).isEqualTo(families);
        assertThat(jdbc.queryForList("SELECT * FROM relations.ledger_member ORDER BY user_id")).isEqualTo(members);
        assertThat(cleanup.migrate().migrationsExecuted).isZero();
    }

    @Test
    void initializesDocumentedTablesAndSkipsAppliedMigrations() {
        var datasource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "");
        var flyway = Flyway.configure().dataSource(datasource)
                .defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").load();
        var jdbc = new JdbcTemplate(datasource);

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(19);
        assertThat(jdbc.queryForList("SELECT \"version\" FROM relations.flyway_schema_history "
                + "WHERE \"type\"='SQL' AND \"success\"=TRUE ORDER BY \"installed_rank\"", String.class))
                .containsExactly("1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8", "1.9", "2.1", "2.2", "2.3", "3.1", "3.2", "3.3", "3.4", "3.5", "3.6", "3.7");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema='relations' AND table_name LIKE 'sys_%'", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns "
                + "WHERE table_schema='relations' AND table_name LIKE 'sys_%'", Integer.class)).isEqualTo(48);
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema='relations' AND table_name LIKE 'sys_%' "
                + "AND TRIM(COALESCE(remarks,''))=''", String.class)).isEmpty();
        assertThat(jdbc.queryForList("SELECT table_name || '.' || column_name FROM information_schema.columns "
                + "WHERE table_schema='relations' AND table_name LIKE 'sys_%' "
                + "AND TRIM(COALESCE(remarks,''))=''", String.class)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_permission", Integer.class)).isEqualTo(18);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_user", Integer.class)).isZero();

        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema='relations' AND table_name LIKE 'ledger_%' "
                + "AND TRIM(COALESCE(remarks,''))=''", String.class)).isEmpty();
        assertThat(jdbc.queryForList("SELECT table_name || '.' || column_name FROM information_schema.columns "
                + "WHERE table_schema='relations' AND table_name LIKE 'ledger_%' "
                + "AND TRIM(COALESCE(remarks,''))=''", String.class)).isEmpty();

        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_permission", Integer.class)).isEqualTo(18);
    }

    @Test
    void upgradesExistingUsersWithoutVerifyingEmailOrChangingPasswords() {
        var datasource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").target("1.9").load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        jdbc.update("INSERT INTO relations.sys_user(username,email,phone,password) VALUES ('existing','existing@example.test','13800000099','existing-hash')");
        var upgraded = Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT email_verified FROM relations.sys_user WHERE username='existing'", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT password FROM relations.sys_user WHERE username='existing'", String.class)).isEqualTo("existing-hash");
        assertThat(jdbc.queryForObject("SELECT nickname FROM relations.sys_user WHERE username='existing'", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_member", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_family", Integer.class)).isEqualTo(1);
        jdbc.update("INSERT INTO relations.sys_user(username,email,password,email_verified) VALUES ('new-email','new@example.test','new-hash',TRUE)");
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
    }

    @Test
    void upgradesPreviousCodeLoginSchemaAndInvalidatesItsCodes() {
        var datasource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").target("2.1").load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        jdbc.update("INSERT INTO relations.sys_user(username,email,email_login_enabled) VALUES ('previous-code-user','previous@example.test',TRUE)");
        jdbc.update("INSERT INTO relations.sys_email_code(email,code_hash,expires_at,used) VALUES ('pending@example.test','old-digest',CURRENT_TIMESTAMP,FALSE)");
        var upgraded = Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT email_verified FROM relations.sys_user WHERE username='previous-code-user'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT password FROM relations.sys_user WHERE username='previous-code-user'", String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT used FROM relations.sys_email_code WHERE email='pending@example.test'", Boolean.class)).isTrue();
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
    }

    @Test
    void addsNicknameToExistingRegistrationSchemaWithoutChangingAccounts() {
        var datasource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").target("2.2").load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        jdbc.update("INSERT INTO relations.sys_user(username,email,password,email_verified) VALUES ('before-nickname','before-nickname@example.test','existing-hash',TRUE)");
        var upgraded = Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(8);
        var user = jdbc.queryForMap("SELECT nickname,password,email_verified FROM relations.sys_user WHERE username='before-nickname'");
        assertThat(user.get("nickname")).isNull();
        assertThat(user.get("password")).isEqualTo("existing-hash");
        assertThat(user.get("email_verified")).isEqualTo(true);
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
    }
}
