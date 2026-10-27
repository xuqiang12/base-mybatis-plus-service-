package com.base.system;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GiftMigrationTest {
    @Test
    void upgradingOldGiftsAddsNoAccountsHouseholdRecordsOrMoneyLogs() {
        var datasource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").target("3.13").load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        jdbc.update("INSERT INTO relations.sys_user(username,email,password) VALUES ('old-gift','old-gift@example.test','hash')");
        int user = jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE username='old-gift'", Integer.class);
        jdbc.update("INSERT INTO relations.ledger_family(id,name) VALUES ('old-gift','旧人情家庭')");
        jdbc.update("INSERT INTO relations.ledger_money_project(family_id,type,name,cash_key,initial_balance_cents) "
                + "VALUES ('old-gift','other','现金','cash',2000)");
        jdbc.update("INSERT INTO relations.ledger_gift(family_id,event_type,direction,date,name,amount_cents,created_by,created_by_name) "
                + "VALUES ('old-gift','wedding','received',DATE '2026-09-23','张三',50000,?,'原记录人')", user);
        jdbc.update("INSERT INTO relations.ledger_gift(family_id,event_type,direction,date,name,amount_cents,created_by,created_by_name) "
                + "VALUES ('old-gift','birthday','sent',DATE '2026-09-24','李四',20000,?,'原记录人')", user);
        var originalGifts = jdbc.queryForList("SELECT id,direction,date,name,amount_cents FROM relations.ledger_gift ORDER BY id");
        var originalAccounts = jdbc.queryForList("SELECT * FROM relations.ledger_money_project ORDER BY id");
        var upgrade = Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT id,direction,date,name,amount_cents FROM relations.ledger_gift ORDER BY id")).isEqualTo(originalGifts);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_gift WHERE project_id IS NOT NULL", Long.class)).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM relations.ledger_money_project ORDER BY id")).isEqualTo(originalAccounts);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_household_item", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_money_log", Long.class)).isZero();
        assertThat(upgrade.migrate().migrationsExecuted).isZero();
    }
}
