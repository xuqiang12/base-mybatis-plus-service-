package com.base.system;

import com.base.security.LoginUser;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 一个应用同时处理账号管理和业务授权，不需要远程身份服务。
class SingleServiceHttpTest {
    private final TestRestTemplate http = new TestRestTemplate();
    private ServletWebServerApplicationContext application;
    private JdbcTemplate jdbc;
    private int port;

    @BeforeEach
    void startApplication() {
        application = (ServletWebServerApplicationContext) new SpringApplicationBuilder(SystemApplication.class,
                ProbeConfiguration.class).profiles("test").run("--server.port=0", "--spring.main.banner-mode=off",
                        "--security.public-endpoints.get[0]=/single/public",
                        "--spring.datasource.url=jdbc:h2:mem:" + UUID.randomUUID()
                                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        port = application.getWebServer().getPort();
        jdbc = application.getBean(JdbcTemplate.class);
        jdbc.update("INSERT INTO relations.sys_user(username,email,phone,password,is_super_admin) VALUES (?,?,?,?,TRUE)",
                "root", "root@example.test", "13800000000", new BCryptPasswordEncoder().encode("test-admin-password"));
    }

    @AfterEach
    void stopApplication() {
        if (application != null) { application.close(); }
    }

    @Test
    void sameApplicationUsesLatestAuthorizationAndUserState() {
        assertThat(get("/health", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/health", null).getBody().path("data").path("service").asText()).isEqualTo("system-service");
        assertThat(get("/single/public", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/single/who", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/single/who", "forged-token").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String admin = login("root@example.test", "test-admin-password");
        assertThat(post("/permission/create", Map.of("code", "relations:record:list", "name", "查询往来"), admin)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post("/role/create", Map.of("name", "reader"), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        int role = jdbc.queryForObject("SELECT id FROM relations.sys_role WHERE name='reader'", Integer.class);
        int permission = jdbc.queryForObject("SELECT id FROM relations.sys_permission WHERE code='relations:record:list'", Integer.class);
        assertThat(post("/user/create", Map.of("username", "reader", "email", "reader@example.test",
                "phone", "13800000001", "password", "test-user-password", "roleIds", List.of(role)), admin)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        int user = jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE username='reader'", Integer.class);
        String token = login("reader@example.test", "test-user-password");

        var who = get("/single/who", token);
        assertThat(who.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(who.getBody().path("data").path("id").asInt()).isEqualTo(user);
        assertThat(who.getBody().path("data").path("username").asText()).isEqualTo("reader");
        assertThat(get("/single/records", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get("/single/records", token).getBody().path("code").asInt()).isEqualTo(403);

        grant(admin, role, List.of(permission));
        assertThat(get("/single/records", token).getStatusCode()).isEqualTo(HttpStatus.OK);
        grant(admin, role, List.of());
        assertThat(get("/single/records", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post("/user/delete", Map.of("id", user), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/single/who", token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void remoteIdentityEndpointIsNoLongerExposed() {
        String admin = login("root@example.test", "test-admin-password");
        assertThat(get("/auth/userInfo", admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/auth/access", admin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String login(String email, String password) {
        var response = post("/auth/login", Map.of("email", email, "password", password), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().path("data").path("token").asText();
    }

    private void grant(String token, int role, List<Integer> permissions) {
        assertThat(post("/role/permission", Map.of("id", role, "menuIds", List.of(), "permissionIds", permissions), token)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<JsonNode> get(String path, String token) {
        return http.exchange("http://localhost:" + port + path, HttpMethod.GET, new HttpEntity<>(headers(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> post(String path, Object body, String token) {
        return http.exchange("http://localhost:" + port + path, HttpMethod.POST, new HttpEntity<>(body, headers(token)), JsonNode.class);
    }

    private HttpHeaders headers(String token) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) { headers.setBearerAuth(token); }
        return headers;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import(ProbeController.class)
    static class ProbeConfiguration {}

    @RestController
    static class ProbeController {
        @GetMapping("/single/public")
        String publicValue() { return "公开"; }
        @GetMapping("/single/who")
        LoginUser who(@AuthenticationPrincipal LoginUser user) { return user; }
        @GetMapping("/single/records")
        @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:relations:record:list')")
        List<String> records() { return List.of("record"); }
    }
}
