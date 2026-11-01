package com.base.system;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class BusinessAccountMigrationTest {
    @Test
    void mergesPreviousBooksByOwnerWithoutLosingCustomersTradesOrAllocations() {
        var datasource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").target("4.2").callbacks(new PostgresConstraintNames()).load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        jdbc.update("INSERT INTO relations.sys_user(id,username,email,password) VALUES (30001,'owner','owner@example.test','hash'),(30002,'member','member@example.test','hash')");
        jdbc.update("INSERT INTO business.biz_book(id,name,created_by,request_id) VALUES (101,'春季',30001,'spring'),(102,'秋季',30001,'fall'),(103,'另一账号',30002,'other')");
        jdbc.update("INSERT INTO business.biz_member(book_id,user_id,role) VALUES (101,30001,'owner'),(101,30002,'member'),(102,30001,'owner'),(103,30002,'owner')");
        jdbc.update("INSERT INTO business.biz_customer(id,book_id,type,name,request_id,created_by) VALUES (201,101,'farmer','张三','same-request',30001),(202,102,'merchant','粮商','same-request',30001),(203,103,'farmer','李四','same-request',30002)");
        jdbc.update("INSERT INTO business.biz_trade(id,book_id,customer_id,direction,biz_date,season,created_by) VALUES (301,101,201,'sale',DATE '2026-01-01','2026春',30001)");
        jdbc.update("INSERT INTO business.biz_trade_item(id,book_id,customer_id,trade_id,category,product_name,quantity,unit,unit_price,amount_cents) VALUES (401,101,201,301,'fertilizer','尿素',10,'袋',50,50000)");
        jdbc.update("INSERT INTO business.biz_settlement(id,book_id,customer_id,kind,amount_cents,biz_date,created_by) VALUES (501,101,201,'receive',20000,DATE '2026-02-01',30002)");
        jdbc.update("INSERT INTO business.biz_settlement_item(id,book_id,customer_id,settlement_id,trade_item_id,amount_cents) VALUES (601,101,201,501,401,20000)");
        var users = jdbc.queryForList("SELECT * FROM relations.sys_user ORDER BY id");
        var trade = jdbc.queryForList("SELECT id,customer_id,direction,biz_date,season,created_by FROM business.biz_trade");
        var allocations = jdbc.queryForList("SELECT id,customer_id,settlement_id,trade_item_id,amount_cents FROM business.biz_settlement_item");
        var upgrade = Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_customer WHERE deleted_at IS NULL", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT * FROM relations.sys_user ORDER BY id")).isEqualTo(users);
        assertThat(jdbc.queryForList("SELECT id,customer_id,direction,biz_date,season,created_by FROM business.biz_trade")).isEqualTo(trade);
        assertThat(jdbc.queryForList("SELECT id,customer_id,settlement_id,trade_item_id,amount_cents FROM business.biz_settlement_item")).isEqualTo(allocations);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_customer WHERE owner_id=30001", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT owner_id FROM business.biz_settlement WHERE id=501", Integer.class)).isEqualTo(30001);
        assertThat(jdbc.queryForObject("SELECT created_by FROM business.biz_settlement WHERE id=501", Integer.class)).isEqualTo(30002);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='business' AND table_name IN ('biz_book','biz_member')", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='business' AND column_name='book_id'", Integer.class)).isZero();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO business.biz_customer(owner_id,type,name,request_id,created_by) VALUES (30001,'farmer','重复','same-request',30001)"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE business.biz_settlement_item SET owner_id=30002 WHERE id=601"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(upgrade.migrate().migrationsExecuted).isZero();
    }
}
