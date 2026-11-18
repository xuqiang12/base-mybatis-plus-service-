package com.base.system;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminPermissionMigrationTest {
    @Test
    void upgradesCompleteLedgerWithoutChangingAccountsGrantsOrBusinessData() {
        var datasource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").target("3.17").load().migrate();
        var jdbc = new JdbcTemplate(datasource);
        jdbc.update("INSERT INTO relations.sys_user(id,username,email,password) VALUES (10001,'existing','existing@example.test','original-hash')");
        jdbc.update("INSERT INTO relations.ledger_family(id,name) VALUES ('existing-family','原家庭')");
        jdbc.update("INSERT INTO relations.ledger_book(family_id,name) VALUES ('existing-family','原账簿')");
        jdbc.update("INSERT INTO relations.ledger_gift(family_id,event_type,direction,date,name,amount_cents,created_by,created_by_name) "
                + "VALUES ('existing-family','wedding','received',DATE '2026-11-17','张三',10000,10001,'原用户')");
        jdbc.update("INSERT INTO relations.sys_role(id,name) VALUES (10001,'existing-role')");
        jdbc.update("INSERT INTO relations.sys_user_role(user_id,role_id) VALUES (10001,10001)");
        int permission = jdbc.queryForObject("SELECT id FROM relations.sys_permission WHERE code='system:user:list'", Integer.class);
        jdbc.update("INSERT INTO relations.sys_role_permission(role_id,permission_id) VALUES (10001,?)", permission);
        jdbc.update("INSERT INTO relations.sys_menu(id,name,path,component,type) VALUES (10001,'原页面','/existing','/existing','PAGE')");
        jdbc.update("INSERT INTO relations.sys_menu_role(menu_id,role_id) VALUES (10001,10001)");
        jdbc.update("INSERT INTO relations.sys_menu_button(menu_id,permission_id,name) VALUES (10001,?,'原查询')", permission);
        var tables = List.of("sys_user", "sys_role", "sys_user_role", "sys_role_permission", "sys_menu_role",
                "sys_menu_button", "ledger_family", "ledger_book", "ledger_gift");
        var before = tables.stream().map(table -> jdbc.queryForList("SELECT * FROM relations." + table + " ORDER BY 1")).toList();

        var upgraded = Flyway.configure().dataSource(datasource).defaultSchema("relations").schemas("relations")
                .locations("classpath:db/system").load();
        assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(4);
        // 初始化会添加后台按钮，原按钮及其 ID、权限关联必须仍然存在。
        for (int i = 0; i < tables.size(); i++) {
            var rows = jdbc.queryForList("SELECT * FROM relations." + tables.get(i) + " ORDER BY 1");
            if (tables.get(i).equals("sys_role")) rows.forEach(row -> row.remove("seed_code"));
            if (tables.get(i).equals("sys_menu_button")) { assertThat(rows).containsAll(before.get(i)); }
            else { assertThat(rows).isEqualTo(before.get(i)); }
        }
        assertThat(jdbc.queryForObject("SELECT code FROM relations.sys_permission WHERE id=?", String.class, permission))
                .isEqualTo("gl:system:user:list");
        assertThat(jdbc.queryForObject("SELECT hidden FROM relations.sys_menu WHERE id=10001", Boolean.class)).isFalse();
        int page = jdbc.queryForObject("SELECT id FROM relations.sys_menu WHERE path='/system/user/index'", Integer.class);
        jdbc.update("INSERT INTO relations.sys_menu_button(menu_id,permission_id,name) VALUES (?,?,'共享查询')", page, permission);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO relations.sys_menu_button(menu_id,permission_id) VALUES (?,?)", page, permission))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO relations.sys_menu_button(menu_id,permission_id) VALUES (?,999999)", page))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(upgraded.migrate().migrationsExecuted).isZero();
    }
}
