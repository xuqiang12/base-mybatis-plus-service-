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

/** 资金守恒、资金来源、借贷联动和事务隔离的 HTTP 契约测试。 */
@SpringBootTest(classes = SystemApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:moneyCrud;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "app.email-code.test-mode=true"
        })
@ActiveProfiles("test")
class MoneyHttpTest {
    @Autowired
    private TestRestTemplate http;

    @Test
    void salarySplitsAndLoansKeepOneTotalAndRejectOverdraftAtomically() {
        String token = account();
        assertMoney(token, 0, 0, 0, 0, 0);
        JsonNode data = ok(get("/ledger/data", token));
        long salary = category(data, "income");
        long expense = category(data, "expense");
        long saving = category(data, "saving");
        Object month = Map.of("month", "2026-10", "items", java.util.List.of(
                Map.of("categoryId", salary, "type", "income", "amountCents", 50000000),
                Map.of("categoryId", saving, "type", "saving", "amountCents", 20000000)));
        ok(post("/ledger/household/month/save", month, token));
        ok(post("/ledger/household/month/save", month, token));
        assertMoney(token, 50000000, 50000000, 0, 0, 50000000);
        JsonNode summary = ok(post("/ledger/money/transfer", Map.of("type", "bank", "name", "银行存款",
                "amountCents", 20000000, "note", "工资拆分"), token));
        long bank = summary.path("projects").get(0).path("id").asLong();
        assertMoney(token, 30000000, 50000000, 0, 0, 50000000);
        ok(post("/ledger/money/transfer", Map.of("type", "stock", "name", "股票",
                "amountCents", 10000000, "note", ""), token));
        assertMoney(token, 20000000, 50000000, 0, 0, 50000000);
        assertThat(post("/ledger/money/transfer", Map.of("name", "错误项目", "type", "other",
                "amountCents", 30000000), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ok(get("/ledger/money", token)).path("projects")).hasSize(2);
        ok(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", java.util.List.of(
                Map.of("categoryId", expense, "type", "expense", "amountCents", 1000000, "projectId", bank))), token));
        assertMoney(token, 20000000, 49000000, 0, 0, 49000000);
        JsonNode loan = ok(post("/ledger/loan/create", Map.of("direction", "lend", "name", "张三",
                "date", "2026-10-26", "amountCents", 5000000), token));
        long friend = loan.path("record").path("friendId").asLong();
        assertMoney(token, 15000000, 44000000, 5000000, 0, 49000000);
        ok(post("/ledger/loan/create", Map.of("direction", "receive", "friendId", friend, "name", "张三",
                "date", "2026-10-26", "amountCents", 1000000), token));
        JsonNode borrowed = ok(post("/ledger/loan/create", Map.of("direction", "borrow", "name", "李四",
                "date", "2026-10-26", "amountCents", 2000000), token));
        assertMoney(token, 18000000, 47000000, 4000000, 2000000, 49000000);
        ok(post("/ledger/loan/create", Map.of("direction", "pay", "name", "李四",
                "friendId", borrowed.path("record").path("friendId").asLong(),
                "date", "2026-10-26", "amountCents", 500000), token));
        assertMoney(token, 17500000, 46500000, 4000000, 1500000, 49000000);
        assertThat(post("/ledger/loan/create", Map.of("direction", "lend", "name", "失败不建亲友",
                "date", "2026-10-26", "amountCents", 19000000), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ok(get("/ledger/data", token)).path("friends").toString()).doesNotContain("失败不建亲友");
        String other = account();
        assertThat(post("/ledger/money/transfer", Map.of("fromProjectId", bank, "amountCents", 1), other)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", java.util.List.of(
                Map.of("categoryId", salary, "type", "income", "amountCents", 1))), token)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertMoney(token, 17500000, 46500000, 4000000, 1500000, 49000000);
    }

    @Test
    void monthValidatesFinalStateAndProjectChangesIncludingNullArePersisted() {
        String token = account();
        JsonNode data = ok(get("/ledger/data", token));
        long salary = category(data, "income");
        long expense = category(data, "expense");
        ok(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", java.util.List.of(
                Map.of("categoryId", expense, "type", "expense", "amountCents", 400),
                Map.of("categoryId", salary, "type", "income", "amountCents", 1000))), token));
        long bank = ok(post("/ledger/money/transfer", Map.of("name", "银行", "type", "bank", "amountCents", 500), token))
                .path("projects").get(0).path("id").asLong();
        ok(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", java.util.List.of(
                Map.of("categoryId", expense, "type", "expense", "amountCents", 400, "projectId", bank))), token));
        assertMoney(token, 500, 600, 0, 0, 600);
        JsonNode edited = ok(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", java.util.List.of(
                Map.of("categoryId", expense, "type", "expense", "amountCents", 400))), token));
        assertThat(edited.path("items").findValues("projectId")).allMatch(JsonNode::isNull);
        assertMoney(token, 100, 600, 0, 0, 600);
        long record = edited.path("items").get(0).path("id").asLong();
        // 删除收入会使已调拨资金无来源，事务必须保留原记录。
        for (JsonNode item : edited.path("items")) if ("income".equals(item.path("type").asText())) record = item.path("id").asLong();
        assertThat(post("/ledger/household/delete", Map.of("id", record), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertMoney(token, 100, 600, 0, 0, 600);
    }

    @Test
    void existingProjectsTransferBothWaysAndIncomeCanGoDirectlyToAProject() {
        String token = account();
        JsonNode data = ok(get("/ledger/data", token));
        long salary = category(data, "income");
        ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", salary,
                "date", "2026-10-01", "amountCents", 1000), token));
        long bank = ok(post("/ledger/money/transfer", Map.of("name", "银行", "type", "bank", "amountCents", 500), token))
                .path("projects").get(0).path("id").asLong();
        assertThat(post("/ledger/money/transfer", Map.of("fromProjectId", bank,
                "name", "禁止从银行拆分", "type", "stock", "amountCents", 200), token)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode unchanged = ok(get("/ledger/money", token));
        assertThat(unchanged.path("projects")).hasSize(1);
        assertThat(unchanged.path("projects").get(0).path("balanceCents").asLong()).isEqualTo(500);
        assertMoney(token, 500, 1000, 0, 0, 1000);
        JsonNode stocks = ok(post("/ledger/money/transfer", Map.of(
                "name", "股票", "type", "stock", "amountCents", 200), token));
        long stock = stocks.path("projects").get(1).path("id").asLong();
        assertThat(stocks.path("projects").get(0).path("balanceCents").asLong()).isEqualTo(500);
        ok(post("/ledger/money/transfer", Map.of("fromProjectId", bank, "toProjectId", stock, "amountCents", 100), token));
        ok(post("/ledger/money/transfer", Map.of("fromProjectId", stock, "toProjectId", bank, "amountCents", 100), token));
        ok(post("/ledger/money/transfer", Map.of("fromProjectId", bank, "amountCents", 200), token));
        assertMoney(token, 500, 1000, 0, 0, 1000);
        JsonNode month = ok(post("/ledger/household/month/save", Map.of("month", "2026-11", "items", java.util.List.of(
                Map.of("categoryId", salary, "type", "income", "amountCents", 100, "projectId", bank))), token));
        assertThat(month.path("items").get(0).path("projectId").asLong()).isEqualTo(bank);
        assertMoney(token, 500, 1100, 0, 0, 1100);
        assertThat(post("/ledger/money/transfer", Map.of("fromProjectId", bank, "toProjectId", bank, "amountCents", 1), token)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/ledger/money/transfer", Map.of("amountCents", 0), token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/ledger/money/transfer", Map.of("amountCents", 1.5), token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String other = account();
        JsonNode otherData = ok(get("/ledger/data", other));
        assertThat(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", java.util.List.of(
                Map.of("categoryId", category(otherData, "income"), "type", "income", "amountCents", 100, "projectId", bank))), other)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertMoney(other, 0, 0, 0, 0, 0);
    }

    @Test
    void concurrentSplitsCannotSpendTheSameCash() throws Exception {
        String token = account();
        long salary = category(ok(get("/ledger/data", token)), "income");
        ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", salary,
                "date", "2026-10-01", "amountCents", 1000), token));
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> split = () -> {
                start.await();
                return post("/ledger/money/transfer", Map.of("name", "银行", "type", "bank", "amountCents", 800), token)
                        .getStatusCode().value();
            };
            var first = pool.submit(split);
            var second = pool.submit(split);
            start.countDown();
            assertThat(java.util.List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(15, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        assertMoney(token, 200, 1000, 0, 0, 1000);
        assertThat(ok(get("/ledger/money", token)).path("projects")).hasSize(1);
    }

    private long category(JsonNode data, String type) {
        for (JsonNode item : data.path("householdCategories"))
            if (type.equals(item.path("type").asText())) return item.path("id").asLong();
        throw new AssertionError(type);
    }

    private void assertMoney(String token, long cash, long held, long lent, long debt, long total) {
        JsonNode value = ok(get("/ledger/money", token));
        assertThat(value.path("cashCents").asLong()).isEqualTo(cash);
        assertThat(value.path("heldCents").asLong()).isEqualTo(held);
        assertThat(value.path("lentCents").asLong()).isEqualTo(lent);
        assertThat(value.path("debtCents").asLong()).isEqualTo(debt);
        assertThat(value.path("totalCents").asLong()).isEqualTo(total);
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
