package com.base.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
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
    @Autowired private JdbcTemplate jdbc;

    @Test
    void registrationPersistsOneCashProjectBeforeAnyMoneyRequest() {
        String token = account();
        String family = ok(get("/family/state", token)).path("family").path("id").asText();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_money_project WHERE family_id=?",
                Long.class, family)).isEqualTo(1);
        JsonNode first = ok(get("/ledger/money", token));
        assertThat(first.path("projects")).hasSize(1);
        JsonNode cash = first.path("projects").get(0);
        assertThat(cash.path("id").asLong()).isPositive();
        assertThat(cash.path("type").asText()).isEqualTo("cash");
        assertThat(cash.path("name").asText()).isEqualTo("现金");
        assertThat(cash.path("balanceCents").asLong()).isZero();
        assertThat(ok(get("/ledger/money", token)).path("projects")).isEqualTo(first.path("projects"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_money_project WHERE family_id=?",
                Long.class, family)).isEqualTo(1);
    }

    @Test
    void realCashIdWorksForHouseholdAndTransfersWhileNullRequestsStoreTheSameId() {
        String token = account();
        long cash = project(ok(get("/ledger/money", token)), "cash").path("id").asLong();
        JsonNode data = ok(get("/ledger/data", token));
        long salary = category(data, "income");
        long expense = category(data, "expense");
        JsonNode month = ok(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", java.util.List.of(
                Map.of("categoryId", salary, "type", "income", "amountCents", 1000, "projectId", cash),
                Map.of("categoryId", expense, "type", "expense", "amountCents", 200, "projectId", cash))), token));
        assertThat(month.path("items").findValues("projectId")).allMatch(id -> id.asLong() == cash);
        JsonNode split = ok(post("/ledger/money/transfer", Map.of("fromProjectId", cash,
                "type", "bank", "name", "银行", "amountCents", 300), token));
        long bank = project(split, "bank").path("id").asLong();
        ok(post("/ledger/money/transfer", Map.of("fromProjectId", bank, "toProjectId", cash, "amountCents", 100), token));
        assertMoney(token, 600, 800, 0, 0, 800);
        JsonNode legacy = ok(post("/ledger/household/save", Map.of("type", "expense", "categoryId", expense,
                "date", "2026-10-01", "amountCents", 200), token));
        assertThat(legacy.path("projectId").asLong()).isEqualTo(cash);
        assertThat(jdbc.queryForObject("SELECT project_id FROM relations.ledger_household_item WHERE id=?",
                Long.class, legacy.path("id").asLong())).isEqualTo(cash);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_money_transfer WHERE from_project_id IS NULL OR to_project_id IS NULL",
                Long.class)).isZero();
        assertThat(post("/ledger/money/transfer", Map.of("fromProjectId", cash, "toProjectId", cash, "amountCents", 1), token)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/ledger/money/transfer", Map.of("fromProjectId", cash, "type", "cash", "name", "伪造现金", "amountCents", 1), token)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String other = account();
        long otherCash = project(ok(get("/ledger/money", other)), "cash").path("id").asLong();
        assertThat(post("/ledger/money/transfer", Map.of("fromProjectId", cash, "toProjectId", otherCash, "amountCents", 1), token)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/ledger/household/save", Map.of("type", "expense", "categoryId", expense,
                "date", "2026-10-01", "amountCents", 1, "projectId", otherCash), token)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertMoney(token, 400, 600, 0, 0, 600);
        assertMoney(other, 0, 0, 0, 0, 0);
    }

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
        assertMoney(token, 50000000, 50000000, 0, 0, 50000000);
        JsonNode summary = ok(post("/ledger/money/transfer", Map.of("type", "bank", "name", "银行存款",
                "amountCents", 20000000, "note", "工资拆分"), token));
        long bank = project(summary, "bank").path("id").asLong();
        assertMoney(token, 30000000, 50000000, 0, 0, 50000000);
        ok(post("/ledger/money/transfer", Map.of("type", "stock", "name", "股票",
                "amountCents", 10000000, "note", ""), token));
        assertMoney(token, 20000000, 50000000, 0, 0, 50000000);
        assertThat(post("/ledger/money/transfer", Map.of("name", "错误项目", "type", "other",
                "amountCents", 30000000), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ok(get("/ledger/money", token)).path("projects")).hasSize(3);
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
                Map.of("categoryId", expense, "type", "expense", "amountCents", 999999999))), token)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertMoney(token, 17500000, 46500000, 4000000, 1500000, 49000000);
    }

    @Test
    void repeatedCategoryExpensesKeepTheirOwnProjectsAndValidateFinalBatchBalance() {
        String token = account();
        JsonNode data = ok(get("/ledger/data", token));
        long salary = category(data, "income");
        long expense = category(data, "expense");
        ok(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", java.util.List.of(
                Map.of("categoryId", expense, "type", "expense", "amountCents", 400),
                Map.of("categoryId", salary, "type", "income", "amountCents", 2000))), token));
        long bank = ok(post("/ledger/money/transfer", Map.of("name", "银行", "type", "bank", "amountCents", 500), token))
                .path("projects").get(1).path("id").asLong();
        ok(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", java.util.List.of(
                Map.of("categoryId", expense, "type", "expense", "amountCents", 400, "projectId", bank))), token));
        assertMoney(token, 1100, 1200, 0, 0, 1200);
        JsonNode added = ok(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", java.util.List.of(
                Map.of("categoryId", expense, "type", "expense", "amountCents", 400))), token));
        assertThat(added.path("items")).hasSize(4);
        assertThat(added.path("items").findValues("id")).doesNotHaveDuplicates();
        assertThat(added.path("items").findValues("projectId")).anyMatch(id -> id.asLong() == bank);
        assertMoney(token, 700, 800, 0, 0, 800);
        long record = 0;
        for (JsonNode item : added.path("items")) if ("income".equals(item.path("type").asText())) record = item.path("id").asLong();
        assertThat(post("/ledger/household/delete", Map.of("id", record), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertMoney(token, 700, 800, 0, 0, 800);
    }

    @Test
    void existingProjectsTransferBothWaysAndIncomeCanGoDirectlyToAProject() {
        String token = account();
        JsonNode data = ok(get("/ledger/data", token));
        long salary = category(data, "income");
        ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", salary,
                "date", "2026-10-01", "amountCents", 1000), token));
        long bank = ok(post("/ledger/money/transfer", Map.of("name", "银行", "type", "bank", "amountCents", 500), token))
                .path("projects").get(1).path("id").asLong();
        assertThat(post("/ledger/money/transfer", Map.of("fromProjectId", bank,
                "name", "禁止从银行拆分", "type", "stock", "amountCents", 200), token)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode unchanged = ok(get("/ledger/money", token));
        assertThat(unchanged.path("projects")).hasSize(2);
        assertThat(project(unchanged, "bank").path("balanceCents").asLong()).isEqualTo(500);
        assertMoney(token, 500, 1000, 0, 0, 1000);
        JsonNode stocks = ok(post("/ledger/money/transfer", Map.of(
                "name", "股票", "type", "stock", "amountCents", 200), token));
        long stock = project(stocks, "stock").path("id").asLong();
        assertThat(project(stocks, "bank").path("balanceCents").asLong()).isEqualTo(500);
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
        long cash = project(ok(get("/ledger/money", token)), "cash").path("id").asLong();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> split = () -> {
                start.await();
                return post("/ledger/money/transfer", Map.of("fromProjectId", cash, "name", "银行", "type", "bank", "amountCents", 800), token)
                        .getStatusCode().value();
            };
            var first = pool.submit(split);
            var second = pool.submit(split);
            start.countDown();
            assertThat(java.util.List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(15, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        assertMoney(token, 200, 1000, 0, 0, 1000);
        assertThat(ok(get("/ledger/money", token)).path("projects")).hasSize(2);
    }

    private JsonNode project(JsonNode summary, String type) {
        for (JsonNode project : summary.path("projects"))
            if (type.equals(project.path("type").asText())) return project;
        throw new AssertionError(type);
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
        assertThat(value.path("projects").get(0).path("type").asText()).isEqualTo("cash");
        assertThat(value.path("projects").get(0).path("balanceCents").asLong()).isEqualTo(cash);
        long projectTotal = 0;
        for (JsonNode project : value.path("projects")) projectTotal += project.path("balanceCents").asLong();
        assertThat(projectTotal).isEqualTo(held);
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
