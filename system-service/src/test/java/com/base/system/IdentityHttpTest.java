package com.base.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.base.common.exception.BusinessException;
import com.base.system.modules.auth.AccessService;
import com.base.system.modules.user.UserService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.base.system.security.BootstrapAdminProperties;
import com.base.system.security.BootstrapAdminRunner;
import jakarta.validation.Validator;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import java.time.Instant;
import com.base.system.modules.menu.MenuService;
import com.base.system.modules.menu.dto.MenuRequests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 用真实 HTTP、真实数据库和真实密码/Token 验证基础身份与授权行为。
@SpringBootTest(classes = SystemApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class IdentityHttpTest {
    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserService users;
    @Autowired private AccessService access;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private Validator validator;
    @Autowired private JwtEncoder jwt;
    @Autowired private MenuService menus;

    @BeforeEach
    void resetUsersAndGrants() {
        for (String table : List.of("sys_user_role", "sys_menu_role", "sys_role_permission", "sys_menu_button", "sys_role", "sys_user")) {
            jdbc.update("DELETE FROM relations." + table);
        }
        jdbc.update("UPDATE relations.sys_menu SET parent_id = NULL");
        jdbc.update("DELETE FROM relations.sys_menu");
        jdbc.update("DELETE FROM relations.sys_permission WHERE code NOT LIKE 'system:%'");
        jdbc.update("INSERT INTO relations.sys_user(username,email,phone,password,is_super_admin) VALUES (?,?,?,?,TRUE)",
                "root", "root@example.test", "13800000000", new BCryptPasswordEncoder().encode("test-admin-password"));
    }

    @Test
    void loginSignsTokenWithAsymmetricKey() {
        String token = login("root@example.test", "test-admin-password");
        String header = new String(java.util.Base64.getUrlDecoder().decode(token.split("\\.")[0]),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(header).contains("RS256");
    }

    @Test
    void healthIsPublicButManagementRequiresValidToken() {
        assertThat(get("/health", null).getBody().path("data").path("status").asText()).isEqualTo("UP");
        var denied = get("/user/list", null);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(denied.getBody().path("code").asInt()).isEqualTo(401);
        assertThat(get("/user/list", "forged-token").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginValidatesInputAndPasswordWithoutExposingStoredHash() {
        assertThat(post("/auth/login", Map.of("email", "bad", "password", ""), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/auth/login", Map.of("email", "root@example.test", "password", "wrong"), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var info = get("/auth/userInfo", login("root@example.test", "test-admin-password")).getBody();
        assertThat(info.path("data").path("userInfo").path("username").asText()).isEqualTo("root");
        assertThat(info.toString()).doesNotContain("password");
        assertThat(info.path("data").path("permissions").isArray()).isTrue();
    }

    @Test
    void userPasswordChangesRejectUnsupportedCharactersAndInvalidLengths() {
        String admin = login("root@example.test", "test-admin-password");
        int userId = createUser(admin, "password-rules", "password-rules@example.test", "13800000012", List.of());
        String originalHash = jdbc.queryForObject("SELECT password FROM relations.sys_user WHERE id=?", String.class, userId);
        for (String password : List.of("中文密码abc123", "abc123！@", "abc123😀", "abc defgh", "Ab1!xyz", "a".repeat(73))) {
            var body = Map.of("username", "invalid-password", "email", "invalid-password@example.test",
                    "phone", "13800000013", "password", password);
            for (String path : List.of("/user/create", "/user/register", "/user/update")) {
                Object request = path.equals("/user/update") ? Map.of("id", userId, "password", password) : body;
                var response = post(path, request, admin);
                assertThat(response.getStatusCode()).as(path + ": " + password).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(response.getBody().path("message").asText()).isEqualTo(
                        password.length() < 8 || password.length() > 72 ? "密码长度必须为8～72个字符"
                                : "密码仅支持英文字母、数字和英文半角符号，不能包含空格");
            }
        }
        assertThat(jdbc.queryForObject("SELECT password FROM relations.sys_user WHERE id=?", String.class, userId)).isEqualTo(originalHash);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_user WHERE email='invalid-password@example.test'", Integer.class)).isZero();
        assertThat(post("/user/update", Map.of("id", userId, "password", "Ab1!@#~?"), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        login("password-rules@example.test", "Ab1!@#~?");
    }

    @Test
    void existingUnicodePasswordsCanStillLogIn() {
        jdbc.update("UPDATE relations.sys_user SET password=? WHERE username='root'",
                new BCryptPasswordEncoder().encode("原有密码abc123"));
        login("root@example.test", "原有密码abc123");
    }

    @Test
    void emailLoginFailsClearlyWhenPrivateMailConfigurationIsAbsent() {
        var response = post("/auth/email/code", Map.of("email", "new@example.test"), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().path("code").asInt()).isEqualTo(503);
    }

    @Test
    void userCrudUsesPaginationHashesPasswordsAndInvalidatesDeletedUserToken() {
        String admin = login("root@example.test", "test-admin-password");
        int userId = createUser(admin, "alice", "alice@example.test", "13800000001", List.of());
        String hash = jdbc.queryForObject("SELECT password FROM relations.sys_user WHERE id=?", String.class, userId);
        assertThat(hash).isNotEqualTo("test-user-password");
        assertThat(new BCryptPasswordEncoder().matches("test-user-password", hash)).isTrue();
        var page = get("/user/list?pageNum=1&pageSize=1&username=alice", admin).getBody().path("data");
        assertThat(page.path("total").asInt()).isEqualTo(1);
        assertThat(page.path("list").size()).isEqualTo(1);
        assertThat(page.path("pageNum").asInt()).isEqualTo(1);
        assertThat(page.toString()).doesNotContain("password");
        assertThat(post("/user/update", Map.of("id", userId, "username", "alice-updated"), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        String userToken = login("alice@example.test", "test-user-password");
        assertThat(post("/user/delete", Map.of("id", userId), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/auth/userInfo", userToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM relations.sys_user WHERE id=?", Boolean.class, userId)).isTrue();
    }

    @Test
    void menuVisibilityAndButtonAuthorityAreSeparateAndChangesApplyToExistingToken() {
        String admin = login("root@example.test", "test-admin-password");
        int roleId = createRole(admin, "reader");
        int menuId = createMenu(admin, "用户页", null);
        int permissionId = jdbc.queryForObject("SELECT id FROM relations.sys_permission WHERE code='system:user:list'", Integer.class);
        post("/menu/button/create", Map.of("menuId", menuId, "permissionCode", "system:user:list", "name", "查询"), admin);
        grant(admin, roleId, List.of(menuId), List.of());
        createUser(admin, "reader", "reader@example.test", "13800000002", List.of(roleId));
        String token = login("reader@example.test", "test-user-password");
        var menus = get("/auth/userInfo", token).getBody().path("data").path("menus");
        assertThat(menus.size()).isEqualTo(1);
        assertThat(menus.get(0).path("buttons").size()).isZero();
        assertThat(get("/user/list", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        grant(admin, roleId, List.of(menuId), List.of(permissionId));
        assertThat(get("/user/list", token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/auth/userInfo", token).getBody().path("data").path("menus").get(0).path("buttons").get(0).asText()).isEqualTo("system:user:list");
        grant(admin, roleId, List.of(menuId), List.of());
        assertThat(get("/user/list", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void failedRoleGrantRollsBackAndAssignedRoleCannotBeDeleted() {
        String admin = login("root@example.test", "test-admin-password");
        int roleId = createRole(admin, "operator");
        int menuId = createMenu(admin, "配置", null);
        int permissionId = jdbc.queryForObject("SELECT id FROM relations.sys_permission WHERE code='system:user:list'", Integer.class);
        grant(admin, roleId, List.of(menuId), List.of(permissionId));
        var failed = post("/role/permission", Map.of("id", roleId, "menuIds", List.of(menuId), "permissionIds", List.of(999999)), admin);
        assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var detail = get("/role/detail?id=" + roleId, admin).getBody().path("data");
        assertThat(detail.path("permissionIds").get(0).asInt()).isEqualTo(permissionId);
        createUser(admin, "operator", "operator@example.test", "13800000003", List.of(roleId));
        assertThat(post("/role/delete", Map.of("id", roleId), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void validatesMenuHierarchyAndProtectsTheLastAdministrator() {
        String admin = login("root@example.test", "test-admin-password");
        int parent = createMenu(admin, "父菜单", null);
        int child = createMenu(admin, "子菜单", parent);
        assertThat(post("/menu/update", Map.of("id", parent, "parentId", child), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/menu/delete", Map.of("id", parent), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        int root = jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE username='root'", Integer.class);
        assertThat(post("/user/delete", Map.of("id", root), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/user/update", Map.of("id", root, "isSuperAdmin", false), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void duplicateUsersAndInvalidPaginationReturnControlledErrors() {
        String admin = login("root@example.test", "test-admin-password");
        var failed = post("/user/create", Map.of("username", "root", "email", "other@example.test", "phone", "13800000005", "password", "test-user-password"), admin);
        assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(failed.getBody().path("data").isNull()).isTrue();
        assertThat(get("/user/list?pageNum=0", admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/user/list?pageSize=101", admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void permissionCodeCannotImpersonateSuperAdministrator() {
        String admin = login("root@example.test", "test-admin-password");
        assertThat(post("/permission/create", Map.of("code", "ROLE_SUPER_ADMIN", "name", "普通权限"), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        int permission = jdbc.queryForObject("SELECT id FROM relations.sys_permission WHERE code='ROLE_SUPER_ADMIN'", Integer.class);
        int role = createRole(admin, "ordinary");
        grant(admin, role, List.of(), List.of(permission));
        createUser(admin, "ordinary", "ordinary@example.test", "13800000006", List.of(role));
        String token = login("ordinary@example.test", "test-user-password");
        assertThat(get("/auth/userInfo", token).getBody().path("data").path("userInfo").path("isSuperAdmin").asBoolean()).isFalse();
        assertThat(get("/user/list", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deletingButtonPreservesIndependentRolePermissions() {
        String admin = login("root@example.test", "test-admin-password");
        int menu = createMenu(admin, "用户管理", null);
        assertThat(post("/menu/button/create", Map.of("menuId", menu, "permissionCode", "system:user:list", "name", "查询"), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        int button = jdbc.queryForObject("SELECT id FROM relations.sys_menu_button WHERE menu_id=?", Integer.class, menu);
        int permission = jdbc.queryForObject("SELECT id FROM relations.sys_permission WHERE code='system:user:list'", Integer.class);
        int role = createRole(admin, "api-reader");
        grant(admin, role, List.of(), List.of(permission));
        createUser(admin, "api-reader", "api-reader@example.test", "13800000007", List.of(role));
        String token = login("api-reader@example.test", "test-user-password");
        assertThat(get("/user/list", token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/menu/button/delete", Map.of("id", button), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/menu/button/list?menuId=" + menu, admin).getBody().path("data").size()).isZero();
        assertThat(get("/user/list", token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void concurrentAdministratorDeletionKeepsOneActiveAdministrator() throws Exception {
        String admin = login("root@example.test", "test-admin-password");
        assertThat(post("/user/create", Map.of("username", "second-admin", "email", "second@example.test",
                "phone", "13800000008", "password", "test-user-password", "isSuperAdmin", true), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        int root = jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE username='root'", Integer.class);
        int second = jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE username='second-admin'", Integer.class);
        var operator = access.currentUser(second);
        var firstDeleted = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var commitFirst = new CountDownLatch(1);
        var tx = new TransactionTemplate(transactions);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> tx.execute(status -> {
                users.delete(root, operator);
                firstDeleted.countDown();
                try {
                    if (!commitFirst.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("首个事务等待超时"); }
                } catch (InterruptedException e) { throw new IllegalStateException(e); }
                return "deleted";
            }));
            assertThat(firstDeleted.await(5, TimeUnit.SECONDS)).isTrue();
            var last = executor.submit(() -> {
                secondStarted.countDown();
                return tx.execute(status -> users.delete(second, operator));
            });
            try {
                assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> last.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                commitFirst.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("deleted");
            assertThatThrownBy(() -> last.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessException.class);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_user WHERE is_super_admin=TRUE AND is_deleted=FALSE", Integer.class)).isEqualTo(1);
    }

    @Test
    void legacyRegistrationRequiresAdministratorAndCannotAssignRoles() {
        String admin = login("root@example.test", "test-admin-password");
        int role = createRole(admin, "management");
        var request = Map.of("username", "registered", "email", "registered@example.test",
                "phone", "13800000009", "password", "test-user-password", "isSuperAdmin", true, "roleIds", List.of(role));
        assertThat(post("/user/register", request, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/user/register", request, admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = login("registered@example.test", "test-user-password");
        var info = get("/auth/userInfo", token).getBody().path("data");
        assertThat(info.path("userInfo").path("isSuperAdmin").asBoolean()).isFalse();
        assertThat(info.path("permissions").size()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_user_role", Integer.class)).isZero();
        assertThat(get("/user/list", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void invalidUserRolesRollBackBothInsertionAndUpdate() {
        String admin = login("root@example.test", "test-admin-password");
        assertThat(post("/user/create", Map.of("username", "rollback", "email", "rollback@example.test", "phone", "13800000010",
                "password", "test-user-password", "roleIds", List.of(999999)), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_user WHERE username='rollback'", Integer.class)).isZero();
        int role = createRole(admin, "original-role");
        int user = createUser(admin, "original", "original@example.test", "13800000010", List.of(role));
        assertThat(post("/user/update", Map.of("id", user, "username", "changed", "roleIds", List.of(999999)), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var detail = get("/user/detail?id=" + user, admin).getBody().path("data");
        assertThat(detail.path("username").asText()).isEqualTo("original");
        assertThat(detail.path("roleIds").get(0).asInt()).isEqualTo(role);
    }

    @Test
    void menuAndButtonUpdatesPreserveOmittedParentAndAllowExplicitRoot() {
        String admin = login("root@example.test", "test-admin-password");
        int parent = createMenu(admin, "目录", null);
        int child = createMenu(admin, "页面", parent);
        assertThat(post("/menu/update", Map.of("id", child, "name", "改名"), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT parent_id FROM relations.sys_menu WHERE id=?", Integer.class, child)).isEqualTo(parent);
        var move = new java.util.HashMap<String, Object>();
        move.put("id", child); move.put("parentId", null);
        assertThat(post("/menu/update", move, admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT parent_id FROM relations.sys_menu WHERE id=?", Integer.class, child)).isNull();
        assertThat(post("/menu/button/create", Map.of("menuId", child, "permissionCode", "relations:item:add", "name", "新增"), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        int button = jdbc.queryForObject("SELECT id FROM relations.sys_menu_button WHERE menu_id=?", Integer.class, child);
        assertThat(post("/menu/button/update", Map.of("id", button, "menuId", parent, "permissionCode", "relations:item:edit", "name", "修改"), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        var updated = get("/menu/button/list?menuId=" + parent, admin).getBody().path("data").get(0);
        assertThat(updated.path("permissionCode").asText()).isEqualTo("relations:item:edit");
        assertThat(post("/menu/delete", Map.of("id", parent), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/menu/button/delete", Map.of("id", button), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/menu/delete", Map.of("id", parent), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/menu/delete", Map.of("id", child), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/menu/tree", admin).getBody().path("data").size()).isZero();
    }

    @Test
    void permissionCrudRejectsReferencedDeletionAndProtectsBuiltInCodes() {
        String admin = login("root@example.test", "test-admin-password");
        assertThat(post("/permission/create", Map.of("code", "relations:read", "name", "读取"), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        int permission = jdbc.queryForObject("SELECT id FROM relations.sys_permission WHERE code='relations:read'", Integer.class);
        assertThat(post("/permission/update", Map.of("id", permission, "code", "relations:view", "name", "查看"), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        int role = createRole(admin, "viewer");
        assertThat(post("/role/update", Map.of("id", role, "name", "renamed-viewer"), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        grant(admin, role, List.of(), List.of(permission));
        assertThat(post("/permission/delete", Map.of("id", permission), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        grant(admin, role, List.of(), List.of());
        assertThat(post("/permission/delete", Map.of("id", permission), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/role/delete", Map.of("id", role), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_permission WHERE id=?", Integer.class, permission)).isZero();
        int builtIn = jdbc.queryForObject("SELECT id FROM relations.sys_permission WHERE code='system:user:list'", Integer.class);
        assertThat(post("/permission/update", Map.of("id", builtIn, "code", "changed", "name", "改名"), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/permission/delete", Map.of("id", builtIn), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void administratorBootstrapIsExplicitAndIdempotent() {
        jdbc.update("DELETE FROM relations.sys_user");
        var args = new DefaultApplicationArguments(new String[0]);
        new BootstrapAdminRunner(new BootstrapAdminProperties(null, null, null, null), validator, users).run(args);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_user", Integer.class)).isZero();
        var configured = new BootstrapAdminRunner(new BootstrapAdminProperties("initial", "initial@example.test", "13800000011", "initial-admin-password"), validator, users);
        configured.run(args); configured.run(args);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_user WHERE is_super_admin=TRUE", Integer.class)).isEqualTo(1);
        String token = login("initial@example.test", "initial-admin-password");
        assertThat(get("/user/list", token).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void signedButExpiredOrWrongIssuerTokensAreRejected() {
        int root = jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE username='root'", Integer.class);
        Instant now = Instant.now();
        var expired = JwtClaimsSet.builder().subject(Integer.toString(root)).issuer("system-service")
                .issuedAt(now.minusSeconds(7200)).expiresAt(now.minusSeconds(3600)).build();
        var wrongIssuer = JwtClaimsSet.builder().subject(Integer.toString(root)).issuer("another-service")
                .issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
        for (var claims : List.of(expired, wrongIssuer)) {
            String token = jwt.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
            assertThat(get("/auth/userInfo", token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Test
    void concurrentParentUpdatesCannotCreateMenuCycle() throws Exception {
        String admin = login("root@example.test", "test-admin-password");
        int a = createMenu(admin, "菜单A", null);
        int b = createMenu(admin, "菜单B", null);
        var aUnderB = new MenuRequests.Update(); aUnderB.setId(a); aUnderB.setParentId(b);
        var bUnderA = new MenuRequests.Update(); bUnderA.setId(b); bUnderA.setParentId(a);
        var firstUpdated = new CountDownLatch(1);
        var commitFirst = new CountDownLatch(1);
        var tx = new TransactionTemplate(transactions);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> tx.execute(status -> {
                menus.update(aUnderB);
                firstUpdated.countDown();
                try {
                    if (!commitFirst.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("首个事务等待超时"); }
                } catch (InterruptedException e) { throw new IllegalStateException(e); }
                return "updated";
            }));
            assertThat(firstUpdated.await(5, TimeUnit.SECONDS)).isTrue();
            var last = executor.submit(() -> tx.execute(status -> menus.update(bUnderA)));
            try {
                assertThatThrownBy(() -> last.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                commitFirst.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("updated");
            assertThatThrownBy(() -> last.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessException.class);
        }
        assertThat(jdbc.queryForObject("SELECT parent_id FROM relations.sys_menu WHERE id=?", Integer.class, a)).isEqualTo(b);
        assertThat(jdbc.queryForObject("SELECT parent_id FROM relations.sys_menu WHERE id=?", Integer.class, b)).isNull();
        assertThat(get("/menu/tree", admin).getBody().path("data").get(0).path("children").get(0).path("id").asInt()).isEqualTo(a);
    }

    private String login(String email, String password) {
        var response = post("/auth/login", Map.of("email", email, "password", password), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().path("data").path("token").asText();
    }

    private int createUser(String admin, String username, String email, String phone, List<Integer> roles) {
        assertThat(post("/user/create", Map.of("username", username, "email", email, "phone", phone, "password", "test-user-password", "roleIds", roles), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        return jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE username=?", Integer.class, username);
    }

    private int createRole(String admin, String name) {
        assertThat(post("/role/create", Map.of("name", name), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        return jdbc.queryForObject("SELECT id FROM relations.sys_role WHERE name=?", Integer.class, name);
    }

    private int createMenu(String admin, String name, Integer parent) {
        var body = new java.util.HashMap<String, Object>();
        body.put("name", name); body.put("type", "PAGE"); body.put("parentId", parent);
        assertThat(post("/menu/create", body, admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        return jdbc.queryForObject("SELECT id FROM relations.sys_menu WHERE name=?", Integer.class, name);
    }

    private void grant(String admin, int role, List<Integer> menus, List<Integer> permissions) {
        assertThat(post("/role/permission", Map.of("id", role, "menuIds", menus, "permissionIds", permissions), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<JsonNode> get(String path, String token) {
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> post(String path, Object body, String token) {
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(token)), JsonNode.class);
    }

    private HttpHeaders headers(String token) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) { headers.setBearerAuth(token); }
        return headers;
    }
}
