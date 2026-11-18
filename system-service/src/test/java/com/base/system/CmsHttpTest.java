package com.base.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 独立数据库上的真实 HTTP、授权和 CMS 聚合保存测试。
@SpringBootTest(classes = SystemApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:cms;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class CmsHttpTest {
    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    private String admin;

    @BeforeEach
    void reset() {
        for (String table : List.of("cms_page_binding", "cms_home_component", "cms_home_decoration", "cms_tabbar_config")) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='relations' AND table_name=?", Integer.class, table) > 0) {
                jdbc.update("DELETE FROM relations." + table);
            }
        }
        for (String table : List.of("ledger_member", "ledger_money_project", "ledger_family", "sys_user_role", "sys_menu_role", "sys_role_permission", "sys_menu_button", "sys_role", "sys_user")) {
            jdbc.update("DELETE FROM relations." + table);
        }
        jdbc.update("UPDATE relations.sys_menu SET parent_id=NULL");
        jdbc.update("DELETE FROM relations.sys_menu");
        jdbc.update("DELETE FROM relations.sys_permission WHERE code LIKE 'gl:cms:%'");
        jdbc.update("INSERT INTO relations.sys_user(username,email,phone,password,is_super_admin) VALUES (?,?,?,?,TRUE)",
                "root", "root@example.test", "13800000000", new BCryptPasswordEncoder().encode("test-admin-password"));
        admin = login("root@example.test", "test-admin-password");
    }

    @Test
    void savesAndReplacesComponentsWithoutDoubleEncodingJson() {
        var created = saved("/cms/home/save", home("main", List.of(component("notice", 20, 1), component("homePageTitle", 10, 1))));
        assertThat(created.path("id").asText()).isNotBlank();
        assertThat(created.path("scene").asText()).isEqualTo("homePage");
        assertThat(created.path("components").get(0).path("info").isObject()).isTrue();
        assertThat(get("/cms/home/list", admin).getBody().path("data").size()).isEqualTo(1);
        var changed = (ObjectNode) created.deepCopy();
        changed.put("name", "新版首页");
        changed.putArray("components").addObject().put("templateId", "cube").put("templateName", "入口")
                .put("sortNo", 0).put("status", 1).putObject("info").put("title", "家庭账本");
        var updated = saved("/cms/home/save", changed);
        assertThat(updated.path("components").size()).isEqualTo(1);
        assertThat(updated.path("components").get(0).path("info").path("title").asText()).isEqualTo("家庭账本");
        assertThat(get("/cms/home/detail?id=" + created.path("id").asText(), admin).getBody().path("data").path("name").asText()).isEqualTo("新版首页");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.cms_home_component", Integer.class)).isEqualTo(1);
        assertThat(post("/cms/home/delete", Map.of("id", created.path("id").asText()), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/cms/home/detail?id=" + created.path("id").asText(), admin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void boundHomeCannotBeDeletedAndClientOnlyReceivesEnabledSortedComponents() {
        var home = saved("/cms/home/save", home("main", List.of(component("notice", 20, 1), component("cube", 5, 0), component("homePageTitle", 10, 1))));
        var page = saved("/cms/page/save", Map.of("name", "首页", "pageKey", "home", "decorationId", home.path("id").asText(), "status", 1));
        assertThat(post("/cms/home/delete", Map.of("id", home.path("id").asText()), admin).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var client = get("/cms/client/page?pageKey=home", admin).getBody().path("data");
        assertThat(client.path("components").size()).isEqualTo(2);
        assertThat(client.path("components").get(0).path("templateId").asText()).isEqualTo("homePageTitle");
        assertThat(client.path("components").get(1).path("templateId").asText()).isEqualTo("notice");
        assertThat(get("/cms/client/page?pageKey=missing", admin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/cms/client/page?pageKey=home", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var disabled = (ObjectNode) page.deepCopy(); disabled.put("status", 0);
        saved("/cms/page/save", disabled);
        assertThat(get("/cms/client/page?pageKey=home", admin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        disabled.put("status", 1); saved("/cms/page/save", disabled);
        var disabledHome = (ObjectNode) home.deepCopy(); disabledHome.put("status", 0); saved("/cms/home/save", disabledHome);
        assertThat(get("/cms/client/page?pageKey=home", admin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/cms/page/list", admin).getBody().path("data").size()).isEqualTo(1);
        assertThat(post("/cms/page/delete", Map.of("id", page.path("id").asText()), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/cms/home/delete", Map.of("id", home.path("id").asText()), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void codesAndPageKeysAreUniqueAndCannotBeChanged() {
        var home = saved("/cms/home/save", home("main", List.of()));
        assertThat(post("/cms/home/save", home("main", List.of()), admin).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var changed = (ObjectNode) home.deepCopy(); changed.put("code", "renamed");
        assertThat(post("/cms/home/save", changed, admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var pageRequest = Map.of("name", "首页", "pageKey", "home", "decorationId", home.path("id").asText(), "status", 1);
        var page = saved("/cms/page/save", pageRequest);
        assertThat(post("/cms/page/save", pageRequest, admin).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var changedPage = (ObjectNode) page.deepCopy(); changedPage.put("pageKey", "other");
        assertThat(post("/cms/page/save", changedPage, admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/cms/page/save", Map.of("name", "无效", "pageKey", "other", "decorationId", "missing", "status", 1), admin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/cms/home/delete", Map.of("id", "missing"), admin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void savingExistingBindingChangesTheHomeWithoutChangingItsPageKey() {
        var first = saved("/cms/home/save", home("first", List.of()));
        var second = saved("/cms/home/save", home("second", List.of()));
        var page = saved("/cms/page/save", Map.of("name", "首页", "pageKey", "home", "decorationId", first.path("id").asText(), "status", 1));
        var rebind = (ObjectNode) page.deepCopy(); rebind.put("decorationId", second.path("id").asText());
        saved("/cms/page/save", rebind);
        assertThat(get("/cms/client/page?pageKey=home", admin).getBody().path("data").path("id").asText()).isEqualTo(second.path("id").asText());
        assertThat(post("/cms/home/delete", Map.of("id", first.path("id").asText()), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/cms/home/delete", Map.of("id", second.path("id").asText()), admin).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidComponentSaveLeavesExistingAggregateIntact() {
        var home = saved("/cms/home/save", home("main", List.of(component("notice", 0, 1))));
        var changed = (ObjectNode) home.deepCopy(); changed.put("name", "不应保存");
        changed.withArray("components").addObject().put("templateId", "unknown").put("templateName", "未知")
                .put("sortNo", 1).put("status", 1).putObject("info");
        assertThat(post("/cms/home/save", changed, admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var existing = get("/cms/home/detail?id=" + home.path("id").asText(), admin).getBody().path("data");
        assertThat(existing.path("name").asText()).isEqualTo("首页");
        assertThat(existing.path("components").size()).isEqualTo(1);
        assertThat(post("/cms/home/save", home("other", List.of(existing.path("components").get(0))), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/cms/home/list", admin).getBody().path("data").size()).isEqualTo(1);
        assertThat(post("/cms/home/save", home("invalid-info", List.of(Map.of("templateId", "notice", "info", "text", "sortNo", 0, "status", 1))), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/cms/home/save", Map.of("code", "incomplete", "name", "不完整", "status", 1), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void oversizedSceneAndPageKeyReturnValidationErrors() {
        var request = new HashMap<>(home("main", List.of())); request.put("scene", "x".repeat(33));
        assertThat(post("/cms/home/save", request, admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var home = saved("/cms/home/save", home("main", List.of()));
        assertThat(post("/cms/page/save", Map.of("name", "过长页面标识", "pageKey", "x".repeat(65), "decorationId", home.path("id").asText(), "status", 1), admin)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void enablingTabbarReplacesCurrentOneAndClientFlattensConfig() {
        var first = saved("/cms/tabbar/save", tabbar("first", 1));
        var second = saved("/cms/tabbar/save", tabbar("second", 1));
        assertThat(get("/cms/tabbar/detail?id=" + first.path("id").asText(), admin).getBody().path("data").path("status").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.cms_tabbar_config WHERE status=1", Integer.class)).isEqualTo(1);
        var client = get("/cms/client/tabbar", admin).getBody().path("data");
        assertThat(client.path("id").asText()).isEqualTo(second.path("id").asText());
        assertThat(client.path("tabBarMode").asText()).isEqualTo("custom");
        assertThat(client.path("items").get(0).path("pagePath").asText()).isEqualTo("/pages/index/index");
        assertThat(get("/cms/client/tabbar", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/cms/tabbar/save", tabbar("second", 0), admin).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("/cms/tabbar/save", tabbar("first", 1), admin).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(get("/cms/client/tabbar", admin).getBody().path("data").path("id").asText()).isEqualTo(second.path("id").asText());
        var changed = (ObjectNode) second.deepCopy(); changed.put("code", "changed");
        assertThat(post("/cms/tabbar/save", changed, admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/cms/tabbar/list", admin).getBody().path("data").size()).isEqualTo(2);
        assertThat(post("/cms/tabbar/delete", Map.of("id", second.path("id").asText()), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/cms/client/tabbar", admin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void invalidTabbarStructuresDoNotDisableExistingConfig() {
        var active = saved("/cms/tabbar/save", tabbar("active", 1));
        for (var change : List.of(Map.entry("tabBarMode", "invalid"), Map.entry("radiusMode", "invalid"),
                Map.entry("rootPagePath", "https://example.test"), Map.entry("bgColorMode", "invalid"), Map.entry("textColorMode", "invalid"), Map.entry("bgColor", "#12345"))) {
            var config = new HashMap<>(config()); config.put(change.getKey(), change.getValue());
            assertThat(post("/cms/tabbar/save", Map.of("code", "bad", "name", "无效", "status", 1, "config", config), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        for (var change : List.of(Map.entry("icon", "javascript:alert(1)"), Map.entry("icon", "https:///"), Map.entry("pagePath", "//evil.test"),
                Map.entry("pagePath", "/pages/index/index?x=1"), Map.entry("linkType", "url"), Map.entry("id", ""))) {
            var item = new HashMap<>(item()); item.put(change.getKey(), change.getValue());
            var config = new HashMap<>(config()); config.put("items", List.of(item));
            assertThat(post("/cms/tabbar/save", Map.of("code", "bad", "name", "无效", "status", 1, "config", config), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        var duplicate = new HashMap<>(config()); duplicate.put("items", List.of(item(), item()));
        assertThat(post("/cms/tabbar/save", Map.of("code", "bad", "name", "重复", "status", 1, "config", duplicate), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/cms/tabbar/save", Map.of("code", "bad", "name", "空配置", "status", 1, "config", Map.of()), admin).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/cms/client/tabbar", admin).getBody().path("data").path("id").asText()).isEqualTo(active.path("id").asText());
    }

    @Test
    void managementPermissionsApplyToExistingTokensIncludingAddVersusEdit() {
        assertThat(post("/role/create", Map.of("name", "cms-editor"), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        int role = jdbc.queryForObject("SELECT id FROM relations.sys_role WHERE name='cms-editor'", Integer.class);
        assertThat(post("/user/create", Map.of("username", "editor", "email", "editor@example.test", "phone", "13800000001", "password", "test-user-password", "roleIds", List.of(role)), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        String editor = login("editor@example.test", "test-user-password");
        for (String scope : List.of("home", "page", "tabbar")) {
            assertThat(get("/cms/" + scope + "/list", editor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(post("/cms/" + scope + "/delete", Map.of("id", "missing"), editor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
        assertThat(post("/cms/home/save", home("main", List.of()), editor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        for (String action : List.of("list", "add", "edit", "delete")) {
            jdbc.update("INSERT INTO relations.sys_permission(code,name) VALUES (?,?)", "gl:cms:home:" + action, action);
        }
        var permissionIds = jdbc.queryForList("SELECT id FROM relations.sys_permission WHERE code IN ('gl:cms:home:list','gl:cms:home:add')", Integer.class);
        assertThat(post("/role/permission", Map.of("id", role, "menuIds", List.of(), "permissionIds", permissionIds), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/cms/home/list", editor).getStatusCode()).isEqualTo(HttpStatus.OK);
        var home = post("/cms/home/save", home("main", List.of()), editor);
        assertThat(home.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/cms/home/detail?id=" + home.getBody().path("data").path("id").asText(), editor).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/cms/home/save", home.getBody().path("data"), editor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var all = jdbc.queryForList("SELECT id FROM relations.sys_permission WHERE code LIKE 'gl:cms:home:%'", Integer.class);
        assertThat(post("/role/permission", Map.of("id", role, "menuIds", List.of(), "permissionIds", all), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/cms/home/save", home.getBody().path("data"), editor).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/cms/home/delete", Map.of("id", home.getBody().path("data").path("id").asText()), editor).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/role/permission", Map.of("id", role, "menuIds", List.of(), "permissionIds", List.of()), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/cms/home/list", editor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/cms/home/save", home("revoked", List.of()), editor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void pageAndTabbarPermissionsDoNotGrantOtherCmsOperations() {
        var home = saved("/cms/home/save", home("main", List.of()));
        assertThat(post("/role/create", Map.of("name", "config-editor"), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        int role = jdbc.queryForObject("SELECT id FROM relations.sys_role WHERE name='config-editor'", Integer.class);
        assertThat(post("/user/create", Map.of("username", "editor", "email", "editor@example.test", "phone", "13800000001", "password", "test-user-password", "roleIds", List.of(role)), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        String editor = login("editor@example.test", "test-user-password");
        for (String scope : List.of("page", "tabbar")) {
            Object request = scope.equals("page") ? Map.of("name", "首页", "pageKey", "home", "decorationId", home.path("id").asText(), "status", 1) : tabbar("main", 1);
            String base = "/cms/" + scope;
            assertThat(post(base + "/save", request, editor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            for (String action : List.of("list", "add", "edit", "delete")) {
                jdbc.update("INSERT INTO relations.sys_permission(code,name) VALUES (?,?)", "gl:cms:" + scope + ":" + action, action);
            }
            var addAndList = jdbc.queryForList("SELECT id FROM relations.sys_permission WHERE code IN (?,?)", Integer.class,
                    "gl:cms:" + scope + ":list", "gl:cms:" + scope + ":add");
            assertThat(post("/role/permission", Map.of("id", role, "menuIds", List.of(), "permissionIds", addAndList), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get(base + "/list", editor).getStatusCode()).isEqualTo(HttpStatus.OK);
            var response = post(base + "/save", request, editor);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            var saved = response.getBody().path("data");
            assertThat(get(scope.equals("page") ? "/cms/client/page?pageKey=home" : "/cms/client/tabbar", editor).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(post(base + "/save", saved, editor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(post(base + "/delete", Map.of("id", saved.path("id").asText()), editor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(get("/cms/home/list", editor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            var all = jdbc.queryForList("SELECT id FROM relations.sys_permission WHERE code LIKE ?", Integer.class, "gl:cms:" + scope + ":%");
            assertThat(post("/role/permission", Map.of("id", role, "menuIds", List.of(), "permissionIds", all), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(post(base + "/save", saved, editor).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(post(base + "/delete", Map.of("id", saved.path("id").asText()), editor).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(post("/role/permission", Map.of("id", role, "menuIds", List.of(), "permissionIds", List.of()), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get(base + "/list", editor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(post(base + "/save", request, editor).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    private Map<String, Object> home(String code, List<?> components) {
        return Map.of("code", code, "name", "首页", "status", 1, "components", components);
    }
    private Map<String, Object> component(String template, int sort, int status) {
        return Map.of("templateId", template, "templateName", "组件", "info", Map.of("title", "礼账"), "sortNo", sort, "status", status);
    }
    private Map<String, Object> tabbar(String code, int status) {
        return Map.of("code", code, "name", "底部导航", "status", status, "config", config());
    }
    private Map<String, Object> config() {
        return Map.of("tabBarMode", "custom", "bgColorMode", "system", "bgColor", "#ffffff", "textColorMode", "custom",
                "textColor", "#808080", "activeColor", "#f05656", "radiusMode", "square", "rootPagePath", "/pages/index/index", "items", List.of(item()));
    }
    private Map<String, Object> item() {
        return Map.of("id", "home", "name", "首页", "icon", "/static/tabbar/home.png", "activeIcon", "/static/tabbar/home-active.png", "linkType", "page", "pagePath", "/pages/index/index", "sortNo", 0);
    }
    private JsonNode saved(String path, Object body) {
        var response = post(path, body, admin);
        assertThat(response.getStatusCode()).as(response.getBody().toString()).isEqualTo(HttpStatus.OK);
        return response.getBody().path("data");
    }
    private String login(String email, String password) {
        var response = post("/auth/login", Map.of("email", email, "password", password), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().path("data").path("token").asText();
    }
    private ResponseEntity<JsonNode> get(String path, String token) {
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), JsonNode.class);
    }
    private ResponseEntity<JsonNode> post(String path, Object body, String token) {
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(token)), JsonNode.class);
    }
    private HttpHeaders headers(String token) {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return headers;
    }
}
