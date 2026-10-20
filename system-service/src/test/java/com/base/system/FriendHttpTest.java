package com.base.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SystemApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:friendCrud;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "app.email-code.test-mode=true"
        })
@ActiveProfiles("test")
class FriendHttpTest {
    @Autowired
    private TestRestTemplate http;

    @Test
    void friendCrudIsLimitedToCurrentFamily() {
        String owner = account();
        JsonNode created = ok(post("/friend/create", Map.of(
                "name", "张三", "appellation", "同事", "gender", 1, "remark", "大学同学"), owner));
        int id = created.path("id").asInt();
        assertThat(created.path("name").asText()).isEqualTo("张三");

        JsonNode page = ok(get("/friend/list?pageNum=1&pageSize=10&name=张", owner));
        assertThat(page.path("total").asInt()).isEqualTo(1);
        assertThat(page.path("list").get(0).path("id").asInt()).isEqualTo(id);

        assertThat(ok(get("/friend/detail?id=" + id, owner)).path("appellation").asText())
                .isEqualTo("同事");
        ok(post("/friend/update", Map.of("id", id, "name", "张三改", "appellation", "朋友",
                "gender", 1, "remark", "已修改"), owner));
        assertThat(ok(get("/friend/detail?id=" + id, owner)).path("name").asText())
                .isEqualTo("张三改");

        String other = account();
        assertThat(get("/friend/detail?id=" + id, other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/friend/update", Map.of("id", id, "name", "越权", "appellation", "",
                "gender", 0, "remark", ""), other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/friend/delete", Map.of("id", id), other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ok(post("/friend/delete", Map.of("id", id), owner));
        assertThat(get("/friend/detail?id=" + id, owner).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ok(get("/friend/list?pageNum=1&pageSize=10", owner)).path("total").asInt()).isZero();
    }

    @Test
    void friendRequestsValidateFields() {
        String owner = account();
        assertThat(post("/friend/create", Map.of("name", "", "gender", 9), owner)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/friend/list?pageNum=0&pageSize=101", owner)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String account() {
        String email = UUID.randomUUID() + "@example.test";
        ok(post("/auth/email/register", Map.of("email", email, "code", "123456",
                "nickname", "测试家人", "password", "friend-test-password"), null));
        return ok(post("/auth/login", Map.of("account", email, "password", "friend-test-password"), null))
                .path("token").asText();
    }

    private JsonNode ok(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode()).withFailMessage(response.toString()).isEqualTo(HttpStatus.OK);
        return response.getBody().path("data");
    }

    private ResponseEntity<JsonNode> get(String path, String token) {
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> post(String path, Object body, String token) {
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(token)), JsonNode.class);
    }

    private HttpHeaders headers(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }
}
