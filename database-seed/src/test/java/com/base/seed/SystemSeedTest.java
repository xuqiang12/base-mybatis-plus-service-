package com.base.seed;

import com.base.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = SeedApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
        "spring.config.name=database-seed",
        "spring.datasource.url=jdbc:h2:mem:system-seed;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "app.seed-admin.username=administrator", "app.seed-admin.email=seed-admin@example.test",
        "app.seed-admin.password=seed-test-password"})
class SystemSeedTest {
    private final SeedAdminProperties admin = new SeedAdminProperties("administrator", "seed-admin@example.test", "seed-test-password");
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired SystemSeedService seeds;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;

    @BeforeEach
    void reset() {
        for (String table : List.of("sys_seed_history", "ledger_member", "ledger_money_project", "ledger_family",
                "sys_user_role", "sys_menu_role", "sys_role_permission", "sys_menu_button", "sys_role", "sys_user")) {
            jdbc.update("DELETE FROM relations." + table);
        }
        jdbc.update("UPDATE relations.sys_menu SET parent_id=NULL");
        jdbc.update("DELETE FROM relations.sys_menu");
        jdbc.update("DELETE FROM relations.sys_permission WHERE code LIKE 'gl:cms:%'");
    }

    @Test
    void initializesAdministratorAndCompleteSystemGrants() {
        seeds.initialize(admin);
        assertThat(context).isNotInstanceOf(org.springframework.web.context.WebApplicationContext.class);
        assertThat(context.getBeansWithAnnotation(org.springframework.web.bind.annotation.RestController.class)).isEmpty();
        assertThat(context.containsBean("jwtEncoder")).isFalse();
        assertThat(count("ledger_family")).isEqualTo(1);
        assertThat(count("ledger_member")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT name FROM relations.ledger_money_project WHERE cash_key='cash'", String.class)).isEqualTo("现金");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_member m JOIN relations.sys_user u ON u.id=m.user_id JOIN relations.ledger_money_project p ON p.family_id=m.family_id WHERE u.username='administrator'", Integer.class)).isEqualTo(1);
        assertThat(count("sys_permission")).isEqualTo(18);
        assertThat(jdbc.queryForList("SELECT code FROM relations.sys_permission", String.class)).allMatch(c -> c.startsWith("gl:system:"));
        assertThat(count("sys_menu")).isEqualTo(6);
        assertThat(count("sys_menu_button")).isEqualTo(14);
        assertThat(count("sys_role")).isEqualTo(1);
        assertThat(count("sys_menu_role")).isEqualTo(6);
        assertThat(count("sys_role_permission")).isEqualTo(18);
        assertThat(count("sys_user_role")).isEqualTo(1);
        assertThat(count("sys_seed_history")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT is_super_admin FROM relations.sys_user WHERE username='administrator'", Boolean.class)).isTrue();
        assertThat(passwords.matches(admin.password(), jdbc.queryForObject("SELECT password FROM relations.sys_user WHERE username='administrator'", String.class))).isTrue();
    }

    @Test
    void repeatDoesNotRestoreRevokedGrantsOrOverwriteEditedMenusAndPassword() {
        seeds.initialize(admin);
        jdbc.update("UPDATE relations.sys_menu SET name='人工改名',hidden=TRUE WHERE path='/system/user/index'");
        jdbc.update("DELETE FROM relations.sys_role_permission");
        jdbc.update("DELETE FROM relations.sys_user_role");
        jdbc.update("DELETE FROM relations.sys_menu_button");
        String changed = passwords.encode("changed-test-password");
        jdbc.update("UPDATE relations.sys_user SET password=?,is_super_admin=FALSE", changed);
        assertThat(seeds.initialize(new SeedAdminProperties(null, null, null))).isZero();
        assertThat(jdbc.queryForObject("SELECT name FROM relations.sys_menu WHERE path='/system/user/index'", String.class)).isEqualTo("人工改名");
        assertThat(count("sys_role_permission")).isZero();
        assertThat(count("sys_user_role")).isZero();
        assertThat(count("sys_menu_button")).isZero();
        assertThat(jdbc.queryForObject("SELECT password FROM relations.sys_user", String.class)).isEqualTo(changed);
        assertThat(jdbc.queryForObject("SELECT is_super_admin FROM relations.sys_user", Boolean.class)).isFalse();
        assertThat(count("sys_seed_history")).isEqualTo(2);
    }

    @Test
    void refusesToPromoteExistingOrdinaryAccountAndRollsBackInitialization() {
        jdbc.update("INSERT INTO relations.sys_user(username,email,password) VALUES (?,?,?)", admin.username(), admin.email(), "original-hash");
        assertThatThrownBy(() -> seeds.initialize(admin)).isInstanceOf(BusinessException.class);
        assertThat(count("sys_seed_history")).isZero();
        assertThat(count("sys_role")).isZero();
        assertThat(count("sys_menu")).isZero();
        assertThat(jdbc.queryForObject("SELECT password FROM relations.sys_user", String.class)).isEqualTo("original-hash");
    }

    @Test
    void preservesExistingRoleGrantsAndAdministratorPasswordOnFirstAdoption() {
        jdbc.update("INSERT INTO relations.sys_role(name) VALUES ('Administrator')");
        jdbc.update("INSERT INTO relations.sys_user(username,email,password,is_super_admin) VALUES (?,?,?,TRUE)", admin.username(), admin.email(), "original-hash");
        seeds.initialize(admin);
        assertThat(count("sys_role")).isEqualTo(1);
        assertThat(count("sys_role_permission")).isZero();
        assertThat(count("sys_user_role")).isZero();
        assertThat(jdbc.queryForObject("SELECT password FROM relations.sys_user", String.class)).isEqualTo("original-hash");
    }

    @Test
    void refusesAmbiguousEmailAndDuplicateMenuPaths() {
        jdbc.update("INSERT INTO relations.sys_user(username,email,is_super_admin) VALUES ('other','SEED-ADMIN@example.test',TRUE)");
        assertThatThrownBy(() -> seeds.initialize(admin)).isInstanceOf(BusinessException.class);
        assertThat(count("sys_seed_history")).isZero();
        jdbc.update("DELETE FROM relations.sys_user");
        jdbc.update("INSERT INTO relations.sys_menu(name,path,type) VALUES ('重复1','/system','DIRECTORY'),('重复2','/system','DIRECTORY')");
        assertThatThrownBy(() -> seeds.initialize(admin)).isInstanceOf(BusinessException.class);
        assertThat(count("sys_seed_history")).isZero();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM relations." + table, Integer.class);
    }
}
