package com.base.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SystemApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:moneyTimeline;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "app.email-code.test-mode=true"})
@ActiveProfiles("test")
class MoneyTimelineHttpTest {
    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void monthsUseShanghaiOperationTimeAndCountsIncludeUnloadedLogs() {
        String token = account();
        String family = ok(get("/family/state", token)).path("family").path("id").asText();
        for (int i = 0; i < 23; i++) seedLog(family, "2026-10-20T01:00:00Z", "工资", "本月收入", "收入 → 银行卡", 10000);
        seedLog(family, "2026-09-30T16:00:00Z", "工资", "北京时间十月", "收入 → 现金", 10000);
        seedLog(family, "2026-09-30T15:59:59Z", "工资", "北京时间九月", "收入 → 现金", 10000);
        JsonNode months = ok(get("/ledger/money/timeline/months", token));
        assertThat(months).hasSize(2);
        assertThat(months.get(0).path("month").asText()).isEqualTo("2026-10");
        assertThat(months.get(0).path("count").asInt()).isEqualTo(24);
        assertThat(months.get(1).path("month").asText()).isEqualTo("2026-09");
        assertThat(months.get(1).path("count").asInt()).isEqualTo(1);
        JsonNode october = ok(get("/ledger/money/timeline?month=2026-10", token));
        assertThat(october.path("items")).hasSize(20);
        JsonNode octoberMore = ok(get("/ledger/money/timeline?month=2026-10&beforeId=" + october.path("nextCursor").asLong(), token));
        assertThat(octoberMore.path("items")).hasSize(4);
        JsonNode september = ok(get("/ledger/money/timeline?month=2026-09", token));
        assertThat(september.path("items")).hasSize(1);
        assertThat(september.path("items").get(0).path("note").asText()).isEqualTo("北京时间九月");
        assertThat(get("/ledger/money/timeline?month=2026-13", token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ok(get("/ledger/money/timeline/months", account()))).isEmpty();
        assertThat(get("/ledger/money/timeline/months", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void keywordSearchCoversAllMonthsTitleNoteFlowAndExactOperationAmount() {
        String token = account();
        String family = ok(get("/family/state", token)).path("family").path("id").asText();
        seedLog(family, "2026-08-15T01:00:00Z", "工资", "徐强工资", "收入 → 工商银行卡", 400000);
        seedLog(family, "2026-09-15T01:00:00Z", "张三", "还款", "张三 → 现金", 400001);
        seedLog(family, "2026-10-15T01:00:00Z", "补贴", "100%_!完成", "收入 → 现金", 1400000);
        for (int i = 0; i < 25; i++) seedLog(family, "2026-10-20T01:00:00Z", "其他", "其他收入", "收入 → 现金", 100);
        for (String keyword : List.of("工资", "徐强", "工商", "4000", "4000.00", "4,000.00")) {
            JsonNode months = ok(getKeyword("/ledger/money/timeline/months", keyword, token));
            assertThat(months).hasSize(1);
            assertThat(months.get(0).path("month").asText()).isEqualTo("2026-08");
            assertThat(months.get(0).path("count").asInt()).isEqualTo(1);
            JsonNode results = ok(getKeyword("/ledger/money/timeline?month=2026-08", keyword, token)).path("items");
            assertThat(results).hasSize(1);
            assertThat(results.get(0).path("amountCents").asLong()).isEqualTo(400000);
            assertThat(results.get(0).path("totalCents").asLong()).isEqualTo(41200000);
        }
        assertThat(ok(getKeyword("/ledger/money/timeline", "张三", token)).path("items")).hasSize(1);
        for (String keyword : List.of("%", "_", "!"))
            assertThat(ok(getKeyword("/ledger/money/timeline", keyword, token)).path("items")).hasSize(1);
        assertThat(ok(getKeyword("/ledger/money/timeline/months", "没有这个备注", token))).isEmpty();
        assertThat(ok(getKeyword("/ledger/money/timeline", "4000", account())).path("items")).isEmpty();
    }

    private void seedLog(String family, String createdAt, String title, String note, String flow, long amount) {
        jdbc.update("INSERT INTO relations.ledger_money_log(family_id,type,action,amount_cents,title,note,flow,"
                        + "total_cents,savings_cents,account_cents,created_at) VALUES (?,'income','create',?,?,?,?,41200000,40000000,20000000,?)",
                family, amount, title, note, flow, java.sql.Timestamp.from(java.time.Instant.parse(createdAt)));
    }

    private ResponseEntity<JsonNode> getKeyword(String path, String keyword, String token) {
        return http.exchange(path + (path.contains("?") ? "&" : "?") + "keyword={keyword}", HttpMethod.GET,
                new HttpEntity<>(headers(token)), JsonNode.class, keyword);
    }

    @Test
    void snapshotsTrackIncomeSavingTransfersAndOpeningBalancesWithoutChangingOlderEntries() {
        String token = account();
        assertThat(timeline(token).path("items")).isEmpty();
        long bank = ok(post("/ledger/money/project/create", Map.of("type", "bank", "name", "银行卡",
                "initialBalanceCents", 20000000), token)).path("projects").get(1).path("id").asLong();
        JsonNode categories = ok(get("/ledger/data", token));
        long income = category(categories, "income");
        long saving = category(categories, "saving");
        ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", income,
                "amountCents", 1200000, "date", "2026-10-27", "note", "徐强工资"), token));
        JsonNode salaryLog = timeline(token).path("items").get(0);
        assertSnapshot(salaryLog, "income", 21200000, 20000000, 20000000);
        ok(post("/ledger/household/save", Map.of("type", "saving", "categoryId", saving,
                "projectId", bank, "amountCents", 400000, "date", "2026-10-28", "note", "10月存款"), token));
        JsonNode saved = timeline(token).path("items");
        assertThat(saved).hasSize(3);
        assertSnapshot(saved.get(0), "saving", 21200000, 20400000, 20400000);
        assertThat(saved.get(0).path("flow").asText()).isEqualTo("现金 → 银行卡");
        assertThat(saved.get(1)).isEqualTo(salaryLog);
        ok(post("/ledger/money/transfer", Map.of("fromProjectId", bank, "amountCents", 100000), token));
        assertSnapshot(timeline(token).path("items").get(0), "transfer", 21200000, 20300000, 20300000);
        assertThat(timeline(token).path("items").get(2)).isEqualTo(salaryLog);
    }

    @Test
    void editsAndDeletesAppendLogsAndFailedOperationsLeaveNoLogOrBalanceChange() {
        String token = account();
        JsonNode categories = ok(get("/ledger/data", token));
        long income = category(categories, "income");
        long expense = category(categories, "expense");
        long id = ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", income,
                "amountCents", 10000, "date", "2026-10-01"), token)).path("id").asLong();
        JsonNode original = timeline(token).path("items").get(0);
        ok(post("/ledger/household/update", Map.of("id", id, "type", "income", "categoryId", income,
                "amountCents", 12000, "date", "2026-10-01", "note", "补正工资"), token));
        JsonNode changed = timeline(token).path("items");
        assertThat(changed.get(0).path("action").asText()).isEqualTo("update");
        assertThat(changed.get(0).path("totalCents").asLong()).isEqualTo(12000);
        assertThat(changed.get(1)).isEqualTo(original);
        assertThat(post("/ledger/household/save", Map.of("type", "expense", "categoryId", expense,
                "amountCents", 15000, "date", "2026-10-01"), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(timeline(token).path("items")).isEqualTo(changed);
        ok(post("/ledger/household/delete", Map.of("id", id), token));
        JsonNode deleted = timeline(token).path("items").get(0);
        assertThat(deleted.path("action").asText()).isEqualTo("delete");
        assertThat(deleted.path("totalCents").asLong()).isZero();
        assertThat(timeline(token).path("items")).hasSize(3);
    }

    @Test
    void loansAreLoggedOnceAndReceivablesRemainPartOfSavingsRatherThanAccountBalances() {
        String token = account();
        long bank = ok(post("/ledger/money/project/create", Map.of("type", "bank", "name", "银行卡",
                "initialBalanceCents", 10000), token)).path("projects").get(1).path("id").asLong();
        JsonNode loan = ok(post("/ledger/loan/create", Map.of("direction", "lend", "name", "张三",
                "amountCents", 3000, "date", "2026-10-01", "projectId", bank), token)).path("record");
        JsonNode logs = timeline(token).path("items");
        assertThat(logs).hasSize(2);
        assertSnapshot(logs.get(0), "lend", 10000, 10000, 7000);
        ok(post("/ledger/loan/create", Map.of("direction", "receive", "friendId", loan.path("friendId").asLong(),
                "name", "张三", "amountCents", 1000, "date", "2026-10-02", "projectId", bank), token));
        assertThat(timeline(token).path("items")).hasSize(3);
        assertSnapshot(timeline(token).path("items").get(0), "receive", 10000, 10000, 8000);
        ok(post("/ledger/loan/create", Map.of("direction", "borrow", "name", "李四", "amountCents", 2000,
                "date", "2026-10-03", "projectId", bank), token));
        assertSnapshot(timeline(token).path("items").get(0), "borrow", 10000, 10000, 10000);
    }

    @Test
    void monthlyBatchCapturesEachOperationAndRollsBackEveryLogOnFailure() {
        String token = account();
        JsonNode categories = ok(get("/ledger/data", token));
        long income = category(categories, "income");
        long expense = category(categories, "expense");
        ok(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", List.of(
                Map.of("type", "income", "categoryId", income, "amountCents", 10000),
                Map.of("type", "expense", "categoryId", expense, "amountCents", 2000))), token));
        JsonNode logs = timeline(token).path("items");
        assertThat(logs).hasSize(2);
        assertSnapshot(logs.get(0), "expense", 8000, 0, 0);
        assertSnapshot(logs.get(1), "income", 10000, 0, 0);
        assertThat(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", List.of(
                Map.of("type", "income", "categoryId", income, "amountCents", 1000),
                Map.of("type", "expense", "categoryId", expense, "amountCents", 20000))), token)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(timeline(token).path("items")).isEqualTo(logs);
    }

    @Test
    void timelineUsesExclusiveCursorAndNeverExposesAnotherFamily() {
        String token = account();
        long income = category(ok(get("/ledger/data", token)), "income");
        for (int i = 0; i < 23; i++) ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", income,
                "amountCents", 100, "date", "2026-10-01"), token));
        JsonNode first = timeline(token);
        assertThat(first.path("items")).hasSize(20);
        long cursor = first.path("nextCursor").asLong();
        JsonNode second = ok(get("/ledger/money/timeline?beforeId=" + cursor, token));
        assertThat(second.path("items")).hasSize(3);
        assertThat(second.path("items").get(0).path("id").asLong()).isLessThan(cursor);
        assertThat(second.path("nextCursor").isNull()).isTrue();
        String other = account();
        assertThat(timeline(other).path("items")).isEmpty();
        assertThat(ok(get("/ledger/money/timeline?beforeId=" + cursor, other)).path("items")).isEmpty();
        assertThat(get("/ledger/money/timeline", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private void assertSnapshot(JsonNode row, String type, long total, long savings, long accounts) {
        assertThat(row.path("type").asText()).isEqualTo(type);
        assertThat(row.path("totalCents").asLong()).isEqualTo(total);
        assertThat(row.path("savingsCents").asLong()).isEqualTo(savings);
        assertThat(row.path("accountCents").asLong()).isEqualTo(accounts);
        assertThat(row.path("createdAt").asText()).isNotBlank();
    }

    private long category(JsonNode data, String type) {
        for (JsonNode row : data.path("householdCategories"))
            if (type.equals(row.path("type").asText())) return row.path("id").asLong();
        throw new AssertionError(type);
    }

    private JsonNode timeline(String token) { return ok(get("/ledger/money/timeline", token)); }

    private String account() {
        String email = UUID.randomUUID() + "@example.test";
        ok(post("/auth/email/register", Map.of("email", email, "code", "123456", "nickname", "测试家人",
                "password", "ledger-test-password"), null));
        return ok(post("/auth/login", Map.of("account", email, "password", "ledger-test-password"), null)).path("token").asText();
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
