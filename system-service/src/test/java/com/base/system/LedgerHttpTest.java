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

/** 账簿、人情和家庭归属的 HTTP 契约测试。 */
@SpringBootTest(classes = SystemApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:ledgerCrud;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "app.email-code.test-mode=true"
        })
@ActiveProfiles("test")
class LedgerHttpTest {
    @Autowired
    private TestRestTemplate http;

    @Test
    void bookAndGiftUseFamilyScopeAndKeepGiftAfterBookDelete() {
        String owner = account();
        JsonNode book = ok(post("/ledger/book/create", Map.of("name", "婚礼账簿", "date", "2026-10-20", "note", ""), owner));
        long bookId = book.path("id").asLong();
        JsonNode created = ok(post("/ledger/gift/create", Map.of(
                "eventType", "wedding", "direction", "received", "date", "2026-10-20",
                "name", "张三", "appellation", "同事", "amountCents", 10000,
                "note", "", "bookId", bookId), owner));
        assertThat(created.path("record").path("bookId").asLong()).isEqualTo(bookId);
        assertThat(created.path("friend").path("name").asText()).isEqualTo("张三");

        JsonNode data = ok(get("/ledger/data", owner));
        assertThat(data.path("books")).hasSize(1);
        assertThat(data.path("records")).hasSize(1);
        assertThat(data.path("friends")).hasSize(1);

        ok(post("/ledger/book/delete", Map.of("id", bookId), owner));
        JsonNode afterDelete = ok(get("/ledger/data", owner));
        assertThat(afterDelete.path("books")).isEmpty();
        assertThat(afterDelete.path("records").get(0).path("bookId").isNull()).isTrue();

        String other = account();
        assertThat(get("/ledger/data", other).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ok(get("/ledger/data", other)).path("records")).isEmpty();
    }

    private String account() {
        String email = UUID.randomUUID() + "@example.test";
        ok(post("/auth/email/register", Map.of("email", email, "code", "123456",
                "nickname", "测试家人", "password", "ledger-test-password"), null));
        return ok(post("/auth/login", Map.of("account", email, "password", "ledger-test-password"), null))
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
        if (token != null) headers.setBearerAuth(token);
        return headers;
    }
}
