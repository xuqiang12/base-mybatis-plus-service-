package com.base.seed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = SeedApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.config.name=database-seed",
                "spring.datasource.url=jdbc:h2:mem:cms-seed;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.username=sa", "spring.datasource.password=",
                "app.seed-admin.email=cms-seed@example.test", "app.seed-admin.password=seed-test-password"})
class CmsSeedTest {
    @Autowired CmsSeedService seeds;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearCms() {
        for (String table : List.of("cms_page_binding", "cms_home_component", "cms_home_decoration", "cms_tabbar_config")) {
            jdbc.update("DELETE FROM relations." + table);
        }
        jdbc.update("DELETE FROM relations.sys_seed_history WHERE module LIKE 'cms-%'");
    }

    @Test
    void initializesRealLedgerDefaultsAndSkipsRepeatedBatch() {
        assertThat(seeds.initialize()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.cms_home_component", Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT page_key FROM relations.cms_page_binding", String.class)).isEqualTo("home");
        String navigation = jdbc.queryForObject("SELECT config FROM relations.cms_tabbar_config", String.class);
        assertThat(navigation).contains("/pages/category/index", "/pages/tools/index").doesNotContain("aiChat");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_permission WHERE code LIKE 'gl:cms:%'", Integer.class)).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_menu WHERE path LIKE '/cms%'", Integer.class)).isEqualTo(4);
        jdbc.update("UPDATE relations.cms_home_decoration SET name='我的布局'");
        jdbc.update("DELETE FROM relations.cms_home_component");
        jdbc.update("UPDATE relations.cms_tabbar_config SET name='我的导航', status=0, active_key=NULL");
        jdbc.update("DELETE FROM relations.sys_menu_button WHERE menu_id IN (SELECT id FROM relations.sys_menu WHERE path LIKE '/cms%')");
        assertThat(seeds.initialize()).isZero();
        assertThat(jdbc.queryForObject("SELECT name FROM relations.cms_home_decoration", String.class)).isEqualTo("我的布局");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.cms_home_component", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM relations.cms_tabbar_config", Integer.class)).isZero();
    }

    @Test
    void firstAdoptionPreservesExistingContentAndDoesNotAddRoleGrants() {
        jdbc.update("INSERT INTO relations.cms_home_decoration(id,code,name,scene,status) VALUES ('custom-home','ledger-home','已装修','homePage',0)");
        jdbc.update("INSERT INTO relations.cms_tabbar_config(id,code,name,config,status) VALUES ('custom-nav','ledger-default','已配置','{}',0)");
        int grants = jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_role_permission", Integer.class);
        seeds.initialize();
        assertThat(jdbc.queryForObject("SELECT name FROM relations.cms_home_decoration", String.class)).isEqualTo("已装修");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.cms_home_component", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT decoration_id FROM relations.cms_page_binding", String.class)).isEqualTo("custom-home");
        assertThat(jdbc.queryForObject("SELECT status FROM relations.cms_tabbar_config", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_role_permission", Integer.class)).isEqualTo(grants);
    }

    @Test
    void duplicateManagementPathRollsBackAllNewCmsBatches() {
        jdbc.update("INSERT INTO relations.sys_menu(name,path,type) VALUES ('重复运营1','/cms','DIRECTORY'),('重复运营2','/cms','DIRECTORY')");
        assertThatThrownBy(() -> seeds.initialize()).hasMessageContaining("重复");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_seed_history WHERE module LIKE 'cms-%'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.cms_home_decoration", Integer.class)).isZero();
        jdbc.update("DELETE FROM relations.sys_menu WHERE name IN ('重复运营1','重复运营2')");
    }
}
