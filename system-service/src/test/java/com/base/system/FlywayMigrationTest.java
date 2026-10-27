package com.base.system;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 使用隔离数据库验证分表迁移、完整注释和重复启动，不连接本机业务数据库。
class FlywayMigrationTest {
    @Test
    void savingsUpgradeMovesIncomeToCashWithoutChangingHistoricalAccountBalances() {
        var datasource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").target("3.11").load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        jdbc.update("INSERT INTO relations.sys_user(username,email,password) VALUES ('income-upgrade','income-upgrade@example.test','hash')");
        int user = jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE username='income-upgrade'", Integer.class);
        jdbc.update("INSERT INTO relations.ledger_family(id,name) VALUES ('income-upgrade','旧收入家庭')");
        jdbc.update("INSERT INTO relations.ledger_money_project(family_id,type,name,cash_key) VALUES ('income-upgrade','other','现金','cash')");
        long cash = jdbc.queryForObject("SELECT id FROM relations.ledger_money_project WHERE family_id='income-upgrade'", Long.class);
        jdbc.update("INSERT INTO relations.ledger_money_project(family_id,type,name) VALUES ('income-upgrade','bank','银行')");
        long bank = jdbc.queryForObject("SELECT id FROM relations.ledger_money_project WHERE family_id='income-upgrade' AND type='bank'", Long.class);
        jdbc.update("INSERT INTO relations.ledger_category(family_id,type,name,icon) VALUES ('income-upgrade','income','工资','money')");
        long category = jdbc.queryForObject("SELECT id FROM relations.ledger_category WHERE family_id='income-upgrade'", Long.class);
        jdbc.update("INSERT INTO relations.ledger_household_month(family_id,month_value) VALUES ('income-upgrade','2026-10')");
        long month = jdbc.queryForObject("SELECT id FROM relations.ledger_household_month WHERE family_id='income-upgrade'", Long.class);
        jdbc.update("INSERT INTO relations.ledger_household_item(month_id,family_id,category_id,project_id,type,amount_cents,created_by,created_by_name) "
                + "VALUES (?,'income-upgrade',?,?,'income',10000,?,'原记录人')", month, category, bank, user);
        jdbc.update("INSERT INTO relations.ledger_category(family_id,type,name,icon) VALUES ('income-upgrade','expense','消费','wallet')");
        long expense = jdbc.queryForObject("SELECT id FROM relations.ledger_category WHERE family_id='income-upgrade' AND type='expense'", Long.class);
        jdbc.update("INSERT INTO relations.ledger_household_item(month_id,family_id,category_id,project_id,type,amount_cents,created_by,created_by_name) "
                + "VALUES (?,'income-upgrade',?,?,'expense',8000,?,'原记录人')", month, expense, bank, user);
        var upgraded = Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT project_id FROM relations.ledger_household_item WHERE family_id='income-upgrade' AND type='income'", Long.class)).isEqualTo(cash);
        assertThat(jdbc.queryForObject("SELECT SUM(initial_balance_cents) FROM relations.ledger_money_project WHERE family_id='income-upgrade'", Long.class)).isZero();
        long bankExpense = jdbc.queryForObject("SELECT SUM(amount_cents) FROM relations.ledger_household_item WHERE project_id=? AND type='expense'", Long.class, bank);
        long bankTransfer = jdbc.queryForObject("SELECT SUM(amount_cents) FROM relations.ledger_money_transfer WHERE from_project_id=? AND to_project_id=?", Long.class, cash, bank);
        assertThat(bankTransfer - bankExpense).isEqualTo(2000);
        assertThat(10000 - bankTransfer).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_household_item WHERE family_id='income-upgrade'", Long.class)).isEqualTo(2);
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_money_transfer WHERE family_id='income-upgrade'", Long.class)).isEqualTo(1);
    }

    @Test
    void loanAccountUpgradeBackfillsCashWithoutInventingSavingRecords() {
        var datasource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").target("3.10").load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        jdbc.update("INSERT INTO relations.sys_user(username,email,password) VALUES ('loan-upgrade','loan-upgrade@example.test','hash')");
        int user = jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE username='loan-upgrade'", Integer.class);
        jdbc.update("INSERT INTO relations.ledger_family(id,name) VALUES ('loan-upgrade','旧借贷家庭')");
        jdbc.update("INSERT INTO relations.ledger_money_project(family_id,type,name,cash_key) VALUES ('loan-upgrade','other','现金','cash')");
        long cash = jdbc.queryForObject("SELECT id FROM relations.ledger_money_project WHERE family_id='loan-upgrade'", Long.class);
        jdbc.update("INSERT INTO relations.ledger_loan(family_id,direction,name,amount_cents,date,created_by,created_by_name) "
                + "VALUES ('loan-upgrade','lend','张三',10000,'2026-10-20',?,'原记录人')", user);
        var upgraded = Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT project_id FROM relations.ledger_loan WHERE family_id='loan-upgrade'", Long.class)).isEqualTo(cash);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_household_item", Long.class)).isZero();
        jdbc.update("INSERT INTO relations.ledger_loan(family_id,direction,name,amount_cents,date,created_by,created_by_name) "
                + "VALUES ('loan-upgrade','borrow','旧客户端',500,'2026-10-20',?,'原记录人')", user);
        assertThat(jdbc.queryForObject("SELECT project_id FROM relations.ledger_loan WHERE name='旧客户端'", Long.class)).isNull();
        jdbc.update("INSERT INTO relations.ledger_category(family_id,type,name,icon) VALUES ('loan-upgrade','saving','存款','wallet')");
        jdbc.update("INSERT INTO relations.ledger_household_month(family_id,month_value) VALUES ('loan-upgrade','2026-10')");
        long category = jdbc.queryForObject("SELECT id FROM relations.ledger_category WHERE family_id='loan-upgrade'", Long.class);
        long month = jdbc.queryForObject("SELECT id FROM relations.ledger_household_month WHERE family_id='loan-upgrade'", Long.class);
        jdbc.update("INSERT INTO relations.ledger_household_item(month_id,family_id,category_id,project_id,type,amount_cents,created_by,created_by_name) "
                + "VALUES (?,'loan-upgrade',?,?,'saving',100,?,'原记录人')", month, category, cash, user);
        assertThat(jdbc.queryForObject("SELECT saving_effect FROM relations.ledger_household_item", String.class)).isEqualTo("add");
        assertThat(jdbc.queryForObject("SELECT loan_id FROM relations.ledger_household_item", Long.class)).isNull();
        assertThatThrownBy(() -> jdbc.update("UPDATE relations.ledger_household_item SET saving_effect='invalid'"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE relations.ledger_household_item SET amount_cents=-100"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
    }

    @Test
    void savingUpgradePreservesOldRecordsWithoutReplayingCashTransfers() {
        var datasource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").target("3.9").load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        jdbc.update("INSERT INTO relations.sys_user(username,email,password) VALUES ('saving-upgrade','saving-upgrade@example.test','hash')");
        int user = jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE username='saving-upgrade'", Integer.class);
        jdbc.update("INSERT INTO relations.ledger_family(id,name) VALUES ('saving-upgrade','旧存款家庭')");
        jdbc.update("INSERT INTO relations.ledger_money_project(family_id,type,name,cash_key) VALUES ('saving-upgrade','other','现金','cash')");
        long cash = jdbc.queryForObject("SELECT id FROM relations.ledger_money_project WHERE family_id='saving-upgrade'", Long.class);
        jdbc.update("INSERT INTO relations.ledger_category(family_id,type,name,icon) VALUES ('saving-upgrade','saving','存款','wallet')");
        long category = jdbc.queryForObject("SELECT id FROM relations.ledger_category WHERE family_id='saving-upgrade'", Long.class);
        jdbc.update("INSERT INTO relations.ledger_household_month(family_id,month_value) VALUES ('saving-upgrade','2026-10')");
        long month = jdbc.queryForObject("SELECT id FROM relations.ledger_household_month WHERE family_id='saving-upgrade'", Long.class);
        jdbc.update("INSERT INTO relations.ledger_household_item(month_id,family_id,category_id,project_id,type,amount_cents,created_by,created_by_name) "
                + "VALUES (?,'saving-upgrade',?,?,'saving',400000,?,'原记录人')", month, category, cash, user);
        var upgraded = Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(5);
        var record = jdbc.queryForMap("SELECT saving_transfer,amount_cents,project_id FROM relations.ledger_household_item WHERE family_id='saving-upgrade'");
        assertThat(record.get("saving_transfer")).isEqualTo(false);
        assertThat(((Number) record.get("amount_cents")).longValue()).isEqualTo(400000);
        assertThat(((Number) record.get("project_id")).longValue()).isEqualTo(cash);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_money_transfer WHERE family_id='saving-upgrade'", Long.class)).isZero();
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
    }

    @Test
    void upgradesExistingCashReferencesAndArchivedFamiliesWithoutChangingBalances() {
        var datasource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").target("3.7").load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        jdbc.update("INSERT INTO relations.sys_user(username,email,password) VALUES ('cash-upgrade','cash-upgrade@example.test','hash')");
        int user = jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE username='cash-upgrade'", Integer.class);
        jdbc.update("INSERT INTO relations.ledger_family(id,name) VALUES ('cash-current','当前家庭')");
        jdbc.update("INSERT INTO relations.ledger_family(id,name,hidden_owner) VALUES ('cash-archived','历史家庭',?)", user);
        jdbc.update("INSERT INTO relations.ledger_category(family_id,type,name,icon) VALUES ('cash-current','income','工资','money')");
        long category = jdbc.queryForObject("SELECT id FROM relations.ledger_category WHERE family_id='cash-current'", Long.class);
        jdbc.update("INSERT INTO relations.ledger_household_month(family_id,month_value) VALUES ('cash-current','2026-10')");
        long month = jdbc.queryForObject("SELECT id FROM relations.ledger_household_month WHERE family_id='cash-current'", Long.class);
        jdbc.update("INSERT INTO relations.ledger_household_item(month_id,family_id,category_id,type,amount_cents,created_by,created_by_name) VALUES (?,'cash-current',?,'income',1000,?,'测试家人')",
                month, category, user);
        jdbc.update("INSERT INTO relations.ledger_money_project(family_id,type,name) VALUES ('cash-current','bank','银行')");
        long bank = jdbc.queryForObject("SELECT id FROM relations.ledger_money_project WHERE family_id='cash-current'", Long.class);
        jdbc.update("INSERT INTO relations.ledger_money_transfer(family_id,to_project_id,amount_cents,created_by) VALUES ('cash-current',?,300,?)", bank, user);
        jdbc.update("INSERT INTO relations.ledger_money_transfer(family_id,from_project_id,amount_cents,created_by) VALUES ('cash-current',?,100,?)", bank, user);
        var upgraded = Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_money_project WHERE cash_key='cash'", Long.class)).isEqualTo(2);
        long cash = jdbc.queryForObject("SELECT id FROM relations.ledger_money_project WHERE family_id='cash-current' AND cash_key='cash'", Long.class);
        assertThat(jdbc.queryForObject("SELECT project_id FROM relations.ledger_household_item WHERE family_id='cash-current'", Long.class)).isEqualTo(cash);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_money_transfer WHERE from_project_id IS NULL OR to_project_id IS NULL", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT SUM(CASE WHEN to_project_id=? THEN amount_cents ELSE 0 END - CASE WHEN from_project_id=? THEN amount_cents ELSE 0 END) FROM relations.ledger_money_transfer WHERE family_id='cash-current'",
                Long.class, bank, bank)).isEqualTo(200);
        long cashFlow = jdbc.queryForObject("SELECT SUM(CASE WHEN to_project_id=? THEN amount_cents ELSE 0 END - CASE WHEN from_project_id=? THEN amount_cents ELSE 0 END) FROM relations.ledger_money_transfer WHERE family_id='cash-current'",
                Long.class, cash, cash);
        long income = jdbc.queryForObject("SELECT SUM(amount_cents) FROM relations.ledger_household_item WHERE project_id=?", Long.class, cash);
        assertThat(income + cashFlow).isEqualTo(800);
        jdbc.update("INSERT INTO relations.ledger_household_item(month_id,family_id,category_id,project_id,type,amount_cents,created_by,created_by_name) VALUES (?,'cash-current',?,?,'income',2000,?,'对象工资')", month, category, cash, user);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_household_item WHERE month_id=? AND category_id=?", Long.class, month, category)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT SUM(amount_cents) FROM relations.ledger_household_item WHERE month_id=?", Long.class, month)).isEqualTo(3000);
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_money_project WHERE cash_key='cash'", Long.class)).isEqualTo(2);
    }

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

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(26);
        assertThat(jdbc.queryForList("SELECT \"version\" FROM relations.flyway_schema_history "
                + "WHERE \"type\"='SQL' AND \"success\"=TRUE ORDER BY \"installed_rank\"", String.class))
                .containsExactly("1.1", "1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8", "1.9", "2.1", "2.2", "2.3", "3.1", "3.2", "3.3", "3.4", "3.5", "3.6", "3.7", "3.8", "3.10", "3.11", "3.12", "3.13", "3.14");
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
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(17);
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
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(16);
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
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(15);
        var user = jdbc.queryForMap("SELECT nickname,password,email_verified FROM relations.sys_user WHERE username='before-nickname'");
        assertThat(user.get("nickname")).isNull();
        assertThat(user.get("password")).isEqualTo("existing-hash");
        assertThat(user.get("email_verified")).isEqualTo(true);
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
    }
}
