package com.base.system;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GiftCategoryMigrationTest {
    @Test
    void separatesGiftSourceWithoutChangingAmountsOrOrdinaryCategoryReferences() {
        var datasource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").target("3.16").load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        jdbc.update("INSERT INTO relations.sys_user(id,username,email,password) VALUES (10001,'gift-category','gift-category@example.test','hash')");
        jdbc.update("INSERT INTO relations.ledger_family(id,name) VALUES ('gift-category','人情迁移家庭')");
        jdbc.update("INSERT INTO relations.ledger_money_project(id,family_id,type,name,cash_key) VALUES (10001,'gift-category','other','现金','cash')");
        jdbc.update("INSERT INTO relations.ledger_category(id,family_id,type,name,icon) VALUES "
                + "(10001,'gift-category','income','人情收礼','money'),"
                + "(10002,'gift-category','income','共同分类','money'),"
                + "(10003,'gift-category','expense','人情送礼','money')");
        jdbc.update("INSERT INTO relations.ledger_household_month(id,family_id,month_value) VALUES (10001,'gift-category','2026-10')");
        jdbc.update("INSERT INTO relations.ledger_gift(id,family_id,event_type,direction,date,name,amount_cents,project_id,created_by,created_by_name) VALUES "
                + "(10001,'gift-category','wedding','received',DATE '2026-10-01','原礼金',1000,10001,10001,'原记录人'),"
                + "(10002,'gift-category','wedding','received',DATE '2026-10-02','共享分类礼金',2000,10001,10001,'原记录人'),"
                + "(10003,'gift-category','wedding','received',DATE '2025-10-01','未入账旧礼金',9000,NULL,10001,'原记录人')");
        jdbc.update("INSERT INTO relations.ledger_household_item(id,month_id,family_id,category_id,project_id,gift_id,type,amount_cents,note,created_by,created_by_name) VALUES "
                + "(10001,10001,'gift-category',10001,10001,10001,'income',1000,'历史礼金',10001,'原记录人'),"
                + "(10002,10001,'gift-category',10002,10001,10002,'income',2000,'共享分类礼金',10001,'原记录人'),"
                + "(10003,10001,'gift-category',10002,10001,NULL,'income',3000,'普通收入',10001,'原记录人')");
        String preservedColumns = "SELECT id,month_id,family_id,project_id,gift_id,type,amount_cents,note,created_by,created_by_name,created_at,updated_at "
                + "FROM relations.ledger_household_item ORDER BY id";
        var originalItems = jdbc.queryForList(preservedColumns);
        var originalGifts = jdbc.queryForList("SELECT * FROM relations.ledger_gift ORDER BY id");
        var upgraded = Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").load();

        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForList(preservedColumns)).isEqualTo(originalItems);
        assertThat(jdbc.queryForList("SELECT * FROM relations.ledger_gift ORDER BY id")).isEqualTo(originalGifts);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_household_item WHERE gift_id IS NOT NULL AND category_id IS NULL", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT category_id FROM relations.ledger_household_item WHERE id=10003", Long.class)).isEqualTo(10002);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM relations.ledger_category WHERE id=10001", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM relations.ledger_category WHERE id=10002", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM relations.ledger_category WHERE id=10003", Boolean.class)).isTrue();
        assertThatThrownBy(() -> jdbc.update("UPDATE relations.ledger_household_item SET category_id=NULL WHERE id=10003"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE relations.ledger_household_item SET category_id=10002 WHERE id=10001"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
    }
}
