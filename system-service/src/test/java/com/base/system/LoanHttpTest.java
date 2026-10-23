package com.base.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.List;
import java.util.concurrent.*;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 借贷余额、超额还款和亲友删除规则测试。 */
@SpringBootTest(classes = SystemApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:loanCrud;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "app.email-code.test-mode=true"
        })
@ActiveProfiles("test")
class LoanHttpTest {
    @Autowired
    private TestRestTemplate http;

    @Test
    void repaymentCannotExceedOutstandingBalanceAndBlocksFriendDelete() {
        String token = account();
        JsonNode lend = ok(post("/ledger/loan/create", Map.of(
                "direction", "lend", "name", "张三", "appellation", "同事",
                "amountCents", 10000, "date", "2026-10-20", "note", ""), token));
        long friendId = lend.path("record").path("friendId").asLong();
        ok(post("/ledger/loan/create", Map.of(
                "direction", "receive", "name", "张三", "appellation", "同事",
                "friendId", friendId, "amountCents", 3000, "date", "2026-10-21", "note", ""), token));
        ResponseEntity<JsonNode> over = post("/ledger/loan/create", Map.of(
                "direction", "receive", "name", "张三", "appellation", "同事",
                "friendId", friendId, "amountCents", 8000, "date", "2026-10-22", "note", ""), token);
        assertThat(over.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ResponseEntity<JsonNode> deleted = post("/friend/delete", Map.of("id", friendId), token);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void concurrentRepaymentsCannotSpendTheSameOutstandingBalance() throws Exception {
        String token = account();
        JsonNode created = ok(post("/ledger/loan/create", Map.of("direction", "lend", "name", "并发亲友",
                "amountCents", 10000, "date", "2026-10-01"), token));
        long friendId = created.path("record").path("friendId").asLong();
        var body = Map.of("direction", "receive", "name", "并发亲友", "friendId", friendId,
                "amountCents", 8000, "date", "2026-10-01");
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> action = () -> { start.await(); return post("/ledger/loan/create", body, token).getStatusCode().value(); };
            var first = pool.submit(action);
            var second = pool.submit(action);
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
    }

    @Test
    void fractionalCentsAreRejectedInsteadOfSilentlyTruncated() {
        String token = account();
        assertThat(post("/ledger/loan/create", Map.of("direction", "lend", "name", "金额校验",
                "amountCents", 1.5, "date", "2026-10-01"), token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String account() {
        String email = UUID.randomUUID() + "@example.test";
        ok(post("/auth/email/register", Map.of("email", email, "code", "123456",
                "nickname", "测试家人", "password", "loan-test-password"), null));
        return ok(post("/auth/login", Map.of("account", email, "password", "loan-test-password"), null))
                .path("token").asText();
    }

    private JsonNode ok(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode()).withFailMessage(response.toString()).isEqualTo(HttpStatus.OK);
        return response.getBody().path("data");
    }

    private ResponseEntity<JsonNode> post(String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }
}
