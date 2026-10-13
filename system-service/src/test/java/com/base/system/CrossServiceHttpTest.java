package com.base.system;

import com.base.relations.RelationsApplication;
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
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// 启动两个真实 HTTP 服务；亲友服务不通过本地数据库读取身份和权限。
class CrossServiceHttpTest {
    private final TestRestTemplate http = new TestRestTemplate();
    private ServletWebServerApplicationContext system;
    private ServletWebServerApplicationContext relations;
    private JdbcTemplate jdbc;
    private int systemPort;
    private int relationsPort;

    @BeforeEach
    void startServices() {
        system = (ServletWebServerApplicationContext) new SpringApplicationBuilder(SystemApplication.class)
                .profiles("test").run("--server.port=0", "--spring.main.banner-mode=off",
                        "--spring.datasource.url=jdbc:h2:mem:" + UUID.randomUUID()
                                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        systemPort = system.getWebServer().getPort();
        jdbc = system.getBean(JdbcTemplate.class);
        jdbc.update("INSERT INTO relations.sys_user(username,email,phone,password,is_super_admin) VALUES (?,?,?,?,TRUE)",
                "root", "root@example.test", "13800000000", new BCryptPasswordEncoder().encode("test-admin-password"));
        relations = (ServletWebServerApplicationContext) new SpringApplicationBuilder(RelationsApplication.class,
                ProbeConfiguration.class).run("--server.port=0", "--spring.main.banner-mode=off",
                        "--spring.application.name=relations-service", "--security.jwt.issuer=system-service",
                        "--security.jwt.public-key=classpath:keys/test-public.pem",
                        "--security.identity.access-url=http://localhost:" + systemPort + "/auth/access",
                        "--security.identity.connect-timeout=1s", "--security.identity.read-timeout=1s",
                        "--security.public-endpoints.get[0]=/probe/public",
                        "--spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
                                + "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration");
        relationsPort = relations.getWebServer().getPort();
    }

    @AfterEach
    void stopServices() {
        if (relations != null) { relations.close(); }
        if (system != null) { system.close(); }
    }

    @Test
    void sharedTokenUsesLatestAuthorizationAndUserState() {
        assertThat(get(relationsPort, "/health", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get(relationsPort, "/probe/public", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get(relationsPort, "/probe/who", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get(relationsPort, "/probe/who", "forged-token").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get(systemPort, "/auth/access", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String admin = login("root@example.test", "test-admin-password");
        assertThat(post(systemPort, "/permission/create", Map.of("code", "relations:record:list", "name", "查询往来"), admin)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post(systemPort, "/role/create", Map.of("name", "reader"), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        int role = jdbc.queryForObject("SELECT id FROM relations.sys_role WHERE name='reader'", Integer.class);
        int permission = jdbc.queryForObject("SELECT id FROM relations.sys_permission WHERE code='relations:record:list'", Integer.class);
        assertThat(post(systemPort, "/user/create", Map.of("username", "reader", "email", "reader@example.test",
                "phone", "13800000001", "password", "test-user-password", "roleIds", List.of(role)), admin)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        int user = jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE username='reader'", Integer.class);
        String token = login("reader@example.test", "test-user-password");

        var who = get(relationsPort, "/probe/who", token);
        assertThat(who.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(who.getBody().path("data").path("id").asInt()).isEqualTo(user);
        assertThat(who.getBody().path("data").path("username").asText()).isEqualTo("reader");
        assertThat(get(relationsPort, "/probe/records", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(get(relationsPort, "/probe/records", token).getBody().path("code").asInt()).isEqualTo(403);

        grant(admin, role, List.of(permission));
        assertThat(get(relationsPort, "/probe/records", token).getStatusCode()).isEqualTo(HttpStatus.OK);
        grant(admin, role, List.of());
        assertThat(get(relationsPort, "/probe/records", token).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(post(systemPort, "/user/delete", Map.of("id", user), admin).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get(relationsPort, "/probe/who", token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void relationsHasNoManagementRoutesDatabaseOrSigningBean() {
        String admin = login("root@example.test", "test-admin-password");
        assertThat(relations.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(relations.getBeansOfType(JwtEncoder.class)).isEmpty();
        for (String path : List.of("/user/list", "/role/list", "/menu/tree", "/permission/list", "/auth/access")) {
            assertThat(get(relationsPort, path, admin).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(post(relationsPort, "/auth/login", Map.of("email", "root@example.test", "password", "test-admin-password"), admin)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void identityServiceFailureRejectsBusinessRequestButHealthRemainsPublic() {
        String admin = login("root@example.test", "test-admin-password");
        system.close();
        system = null;
        var response = get(relationsPort, "/probe/who", admin);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().path("code").asInt()).isEqualTo(503);
        assertThat(get(relationsPort, "/health", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String login(String email, String password) {
        var response = post(systemPort, "/auth/login", Map.of("email", email, "password", password), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().path("data").path("token").asText();
    }

    private void grant(String token, int role, List<Integer> permissions) {
        assertThat(post(systemPort, "/role/permission", Map.of("id", role, "menuIds", List.of(), "permissionIds", permissions), token)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<JsonNode> get(int port, String path, String token) {
        return http.exchange("http://localhost:" + port + path, HttpMethod.GET, new HttpEntity<>(headers(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> post(int port, String path, Object body, String token) {
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
        @GetMapping("/probe/public")
        String publicValue() { return "公开"; }
        @GetMapping("/probe/who")
        LoginUser who(@AuthenticationPrincipal LoginUser user) { return user; }
        @GetMapping("/probe/records")
        @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('permission:relations:record:list')")
        List<String> records() { return List.of("record"); }
    }
}
