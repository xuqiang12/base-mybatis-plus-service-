package com.base.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.base.system.modules.ledger.mapper.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 账簿、人情和家庭归属的 HTTP 契约测试。 */
@SpringBootTest(classes = SystemApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:ledgerQueries;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "app.email-code.test-mode=true"
        })
@ActiveProfiles("test")
class LedgerQueryHttpTest {
    @Autowired
    private TestRestTemplate http;

    @MockitoSpyBean private JdbcTemplate jdbc;
    @MockitoSpyBean private GiftMapper gifts;
    @MockitoSpyBean private LoanMapper loans;
    @MockitoSpyBean private BookMapper books;
    @MockitoSpyBean private HouseholdItemMapper householdItems;

    @Test
    void detailsPaginationSummariesAndOwnershipUseIndependentReads() {
        String owner = account();
        String other = account();
        JsonNode categories = ok(get("/ledger/data", owner)).path("householdCategories");
        long income = 0;
        for (JsonNode category : categories) if ("income".equals(category.path("type").asText())) { income = category.path("id").asLong(); break; }
        JsonNode saved = ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", income,
                "amountCents", 100000, "date", "2026-10-01"), owner));
        long id = saved.path("id").asLong();
        assertThat(ok(get("/ledger/household/detail?id=" + id, owner)).path("categoryName").asText()).isNotBlank();
        assertThat(get("/ledger/household/detail?id=" + id, other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        for (int i = 0; i < 21; i++) ok(post("/ledger/gift/create", Map.of("eventType", "birthday", "direction", "received",
                "name", "张三", "amountCents", 100, "date", "2026-10-02"), owner));
        JsonNode page = ok(get("/ledger/gift/list?period=2026-10", owner));
        assertThat(page.path("list")).hasSize(20);
        assertThat(page.path("total").asLong()).isEqualTo(21);
        assertThat(ok(get("/ledger/gift/list?period=2026-10&pageNum=2", owner)).path("list")).hasSize(1);
        assertThat(ok(get("/ledger/gift/summary?period=2026-10", owner)).path("receivedCents").asLong()).isEqualTo(2100);
        assertThat(ok(get("/ledger/household/summary?period=2026-10", owner)).path("incomeCents").asLong()).isEqualTo(102100);
        assertThat(ok(get("/ledger/gift/list", other)).path("total").asLong()).isZero();
        assertThat(get("/ledger/gift/list?pageSize=101", owner).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/ledger/household/list?period=2026-13", owner).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode recent = ok(get("/ledger/record/recent?limit=5", owner));
        assertThat(recent).hasSize(5);
        assertThat(recent.get(0).path("projectId").isNumber()).isTrue();
        assertThat(ok(get("/ledger/money/project/list", owner))).isNotEmpty();
        assertThat(ok(get("/ledger/household/month?month=2026-10", owner)).path("summary").path("incomeCents").asLong()).isEqualTo(102100);
    }

    @Test
    void statisticsAndProjectBalancesPreserveNetSavingAndLegacyDates() {
        String owner = account();
        JsonNode gift = ok(post("/ledger/gift/create", Map.of("eventType", "birthday", "direction", "received",
                "name", "亲友", "amountCents", 100000, "date", "2026-10-02"), owner)).path("record");
        JsonNode projects = ok(post("/ledger/money/project/create", Map.of("type", "bank", "name", "银行卡", "initialBalanceCents", 50000), owner)).path("projects");
        long bank = 0;
        for (JsonNode p : projects) if ("bank".equals(p.path("type").asText())) bank = p.path("id").asLong();
        long giftId = gift.path("id").asLong();
        ok(post("/ledger/gift/update", Map.of("id", giftId, "eventType", "birthday", "direction", "received", "name", "亲友",
                "amountCents", 100000, "date", "2026-10-02", "friendId", gift.path("friendId").asLong(), "projectId", bank), owner));
        long expense = 0;
        for (JsonNode c : ok(get("/ledger/category/list", owner))) if ("expense".equals(c.path("type").asText())) { expense = c.path("id").asLong(); break; }
        JsonNode record = ok(post("/ledger/household/save", Map.of("type", "expense", "categoryId", expense, "amountCents", 20000,
                "date", "2026-10-03", "projectId", bank), owner));
        jdbc.update("UPDATE relations.ledger_household_item SET record_date=NULL WHERE id=?", record.path("id").asLong());
        ok(post("/ledger/category/delete", Map.of("id", expense), owner));
        JsonNode detail = ok(get("/ledger/household/detail?id=" + record.path("id").asLong(), owner));
        assertThat(detail.path("date").asText()).isEqualTo("2026-10-01");
        assertThat(detail.path("categoryDeleted").asBoolean()).isTrue();
        JsonNode sum = ok(get("/ledger/household/summary?period=2026-10", owner));
        assertThat(sum.path("incomeCents").asLong()).isEqualTo(100000);
        assertThat(sum.path("expenseCents").asLong()).isEqualTo(20000);
        assertThat(sum.path("savingCents").asLong()).isEqualTo(80000);
        assertThat(sum.path("balanceCents").asLong()).isZero();
        assertThat(ok(get("/ledger/money/project/detail?id=" + bank, owner)).path("balanceCents").asLong()).isEqualTo(130000);
        assertThat(ok(get("/ledger/household/statistics?period=2026", owner)).path("months")).hasSize(12);
    }

    @Test
    void friendBookAndLoanPagesKeepBothOutstandingSidesAndMatchingFilters() {
        String owner = account(); String other = account();
        long book = ok(post("/ledger/book/create", Map.of("name", "生日账簿"), owner)).path("id").asLong();
        JsonNode gift = ok(post("/ledger/gift/create", Map.of("eventType", "birthday", "direction", "received", "bookId", book,
                "name", "双向亲友", "amountCents", 100000, "date", "2026-10-02"), owner)).path("record");
        long friend = gift.path("friendId").asLong();
        ok(post("/ledger/gift/create", Map.of("eventType", "birthday", "direction", "sent", "friendId", friend,
                "name", "双向亲友", "amountCents", 500, "date", "2025-09-02"), owner));
        for (var item : java.util.List.of(Map.of("direction", "lend", "amountCents", 10000), Map.of("direction", "borrow", "amountCents", 7000),
                Map.of("direction", "receive", "amountCents", 2000), Map.of("direction", "pay", "amountCents", 1000))) {
            ok(post("/ledger/loan/create", Map.of("direction", item.get("direction"), "amountCents", item.get("amountCents"),
                    "friendId", friend, "name", "双向亲友", "date", "2026-10-03"), owner));
        }
        JsonNode totals = ok(get("/ledger/loan/summary?friendId=" + friend, owner));
        assertThat(totals.path("receivableCents").asLong()).isEqualTo(8000);
        assertThat(totals.path("payableCents").asLong()).isEqualTo(6000);
        assertThat(totals.path("count").asLong()).isEqualTo(4);
        for (String side : java.util.List.of("receivable", "payable")) {
            JsonNode list = ok(get("/ledger/loan/summary/list?side=" + side + "&pageSize=1", owner));
            assertThat(list.path("total").asLong()).isEqualTo(1);
            assertThat(list.path("list").get(0).path("receivableCents").asLong()).isEqualTo(8000);
            assertThat(list.path("list").get(0).path("payableCents").asLong()).isEqualTo(6000);
        }
        JsonNode friends = ok(get("/friend/summary/list?name=双向", owner));
        assertThat(friends.path("list")).hasSize(1);
        assertThat(friends.path("list").get(0).path("receivedCents").asLong()).isEqualTo(100000);
        assertThat(friends.path("list").get(0).path("sentCents").asLong()).isEqualTo(500);
        assertThat(friends.path("list").get(0).path("lastDate").asText()).isEqualTo("2026-10-02");
        assertThat(friends.path("list").get(0).path("receivableCents").asLong()).isEqualTo(8000);
        assertThat(ok(get("/friend/summary/list?name=不存在", owner)).path("total").asLong()).isZero();
        assertThat(ok(get("/ledger/book/detail?id=" + book, owner)).path("receivedCents").asLong()).isEqualTo(100000);
        assertThat(ok(get("/ledger/book/list", owner)).path("list").get(0).path("recordCount").asLong()).isEqualTo(1);
        assertThat(ok(get("/ledger/gift/list?bookId=" + book + "&direction=received&period=2026", owner)).path("total").asLong()).isEqualTo(1);
        assertThat(ok(get("/ledger/gift/list?direction=sent&period=2026", owner)).path("total").asLong()).isZero();
        assertThat(ok(get("/ledger/loan/list?direction=receive&friendId=" + friend + "&period=2026-10", owner)).path("total").asLong()).isEqualTo(1);
        assertThat(ok(get("/ledger/household/summary?period=2026-10", owner)).path("count").asLong()).isEqualTo(1);
        assertThat(get("/ledger/gift/detail?id=" + gift.path("id").asLong(), other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/ledger/book/detail?id=" + book, other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/ledger/loan/summary?friendId=" + friend, other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/ledger/gift/list?direction=lend", owner).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/ledger/loan/summary/list?side=net", owner).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void historicalCollectionsAreIndependentlyPagedAndOwnerOnly() {
        String owner = account(); String inviter = account(); String other = account();
        String original = ok(get("/family/state", owner)).path("family").path("id").asText();
        ok(post("/ledger/book/create", Map.of("name", "旧账簿"), owner));
        JsonNode gift = ok(post("/ledger/gift/create", Map.of("eventType", "birthday", "direction", "received",
                "name", "历史亲友", "amountCents", 100000, "date", "2026-10-02"), owner)).path("record");
        ok(post("/ledger/loan/create", Map.of("direction", "borrow", "friendId", gift.path("friendId").asLong(),
                "name", "历史亲友", "amountCents", 1000, "date", "2026-10-03"), owner));
        String inviteFamily = ok(get("/family/state", inviter)).path("family").path("id").asText();
        var inviteHeaders = headers(inviter); inviteHeaders.set("X-Family-Id", inviteFamily);
        String invitation = ok(http.exchange("/family/invitation/share", HttpMethod.POST, new HttpEntity<>(Map.of(), inviteHeaders), JsonNode.class)).path("token").asText();
        var joinHeaders = headers(owner); joinHeaders.set("X-Family-Id", original);
        ok(http.exchange("/family/invitation/accept", HttpMethod.POST, new HttpEntity<>(Map.of("token", invitation), joinHeaders), JsonNode.class));
        for (String resource : java.util.List.of("friend", "book", "gift", "loan", "household")) {
            String path = "/ledger/history/" + resource + "/list?familyId=" + original + "&pageSize=1";
            JsonNode page = ok(get(path, owner));
            assertThat(page.path("list")).hasSize(1);
            assertThat(page.path("total").asLong()).isEqualTo(1);
            assertThat(ok(get(path + "&pageNum=2", owner)).path("list")).isEmpty();
            assertThat(get(path, inviter).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(get(path, other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(ok(get("/ledger/gift/list", owner)).path("total").asLong()).isZero();
    }

    @Test
    void legacySignedSavingAmountsRemainSignedInSummariesAndCategoryStatistics() {
        String owner = account();
        JsonNode categories = ok(get("/ledger/category/list", owner));
        long saving = 0;
        for (JsonNode category : categories) if ("saving".equals(category.path("type").asText())) saving = category.path("id").asLong();
        String family = ok(get("/family/state", owner)).path("family").path("id").asText();
        int creator = jdbc.queryForObject("SELECT user_id FROM relations.ledger_member WHERE family_id=?", Integer.class, family);
        jdbc.update("INSERT INTO relations.ledger_household_month(family_id,month_value) VALUES (?,'2025-01')", family);
        long month = jdbc.queryForObject("SELECT id FROM relations.ledger_household_month WHERE family_id=?", Long.class, family);
        long cash = ok(get("/ledger/money/project/list", owner)).get(0).path("id").asLong();
        jdbc.update("INSERT INTO relations.ledger_household_item(month_id,family_id,category_id,type,amount_cents,project_id,saving_effect,created_by,created_by_name) "
                + "VALUES (?,?,?,'saving',2000,?,'subtract',?,'测试')", month, family, saving, cash, creator);
        assertThat(ok(get("/ledger/household/summary?period=2025", owner)).path("savingCents").asLong()).isEqualTo(-2000);
        assertThat(ok(get("/ledger/household/statistics?period=2025", owner)).path("categories").get(0).path("amountCents").asLong()).isEqualTo(-2000);
    }

    @Test
    void householdPageIncludesWholeMonthTotalsDespiteLimitedRows() {
        String owner = account();
        long income = 0;
        for (JsonNode category : ok(get("/ledger/category/list", owner))) if ("income".equals(category.path("type").asText())) { income = category.path("id").asLong(); break; }
        for (int i = 0; i < 3; i++) ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", income,
                "amountCents", 1000, "date", "2026-10-02"), owner));
        JsonNode page = ok(get("/ledger/household/list?type=income&pageSize=1", owner));
        assertThat(page.path("list")).hasSize(1);
        assertThat(page.path("total").asLong()).isEqualTo(3);
        assertThat(page.path("monthSummaries")).hasSize(1);
        assertThat(page.path("monthSummaries").get(0).path("incomeCents").asLong()).isEqualTo(3000);
        assertThat(ok(get("/ledger/household/list?pageNum=5&pageSize=1", owner)).path("monthSummaries")).isEmpty();
    }

    @Test
    void independentReadsDoNotLoadWholeEntityCollectionsAndQueryCountIsFixed() {
        String owner = account();
        JsonNode gift = ok(post("/ledger/gift/create", Map.of("eventType", "birthday", "direction", "received",
                "name", "查询路径", "amountCents", 100000, "date", "2026-10-02"), owner)).path("record");
        ok(post("/friend/create", Map.of("name", "第二位亲友"), owner));
        ok(post("/friend/create", Map.of("name", "第三位亲友"), owner));
        clearInvocations(gifts, loans, books, householdItems);
        ok(get("/ledger/gift/detail?id=" + gift.path("id").asLong(), owner));
        ok(get("/ledger/gift/list", owner));
        ok(get("/ledger/gift/summary", owner));
        ok(get("/ledger/household/list", owner));
        ok(get("/ledger/household/statistics?period=2026", owner));
        ok(get("/ledger/record/recent", owner));
        ok(get("/ledger/money/project/list", owner));
        verifyNoInteractions(gifts, loans, books, householdItems);
        clearInvocations(jdbc);
        ok(get("/friend/summary/list?pageSize=1", owner));
        long one = mockingDetails(jdbc).getInvocations().stream().filter(call -> call.getMethod().getName().equals("query") && call.getMethod().getParameterTypes()[1] == org.springframework.jdbc.core.RowCallbackHandler.class).count();
        clearInvocations(jdbc);
        ok(get("/friend/summary/list?pageSize=100", owner));
        long hundred = mockingDetails(jdbc).getInvocations().stream().filter(call -> call.getMethod().getName().equals("query") && call.getMethod().getParameterTypes()[1] == org.springframework.jdbc.core.RowCallbackHandler.class).count();
        assertThat(one).isEqualTo(2);
        assertThat(hundred).isEqualTo(one);
    }

    @Test
    void queryPayloadsStayBoundedAtOneHundredOneThousandAndTenThousandRecords() throws Exception {
        String owner = account();
        JsonNode gift = ok(post("/ledger/gift/create", Map.of("eventType", "birthday", "direction", "received",
                "name", "体积样本", "amountCents", 100, "date", "2026-10-02"), owner)).path("record");
        String family = ok(get("/family/state", owner)).path("family").path("id").asText();
        long id = gift.path("id").asLong();
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var report = new StringBuilder("# 礼账查询规模验证（H2）\n\n仅在隔离 H2 测试库创建数据；耗时不是生产 PostgreSQL 性能结论。首次/二次为连续请求，未清理数据库或 JVM 缓存，不能视为严格冷缓存。字节数包含统一响应外壳，按 UTF-8 JSON 重新序列化。\n\n| 人情记录数 | 旧全量字节 | 详情字节 | 分页20条字节 | 全量首次/二次ms | 详情首次/二次ms | 分页首次/二次ms |\n| --- | --- | --- | --- | --- | --- | --- |\n");
        int previous = 1;
        int detailBytes = 0;
        for (int size : new int[] {100, 1000, 10000}) {
            jdbc.update("INSERT INTO relations.ledger_gift(family_id,event_type,direction,date,name,appellation,amount_cents,note,friend_id,created_by,created_by_name) "
                    + "SELECT g.family_id,g.event_type,g.direction,g.date,g.name,g.appellation,g.amount_cents,g.note,g.friend_id,g.created_by,g.created_by_name "
                    + "FROM relations.ledger_gift g CROSS JOIN SYSTEM_RANGE(1,?) WHERE g.id=?", size - previous, id);
            long start = System.nanoTime(); var old = get("/ledger/data", owner); long fullFirst = (System.nanoTime() - start) / 1000000;
            start = System.nanoTime(); get("/ledger/data", owner); long fullSecond = (System.nanoTime() - start) / 1000000;
            start = System.nanoTime(); var detail = get("/ledger/gift/detail?id=" + id, owner); long detailFirst = (System.nanoTime() - start) / 1000000;
            start = System.nanoTime(); get("/ledger/gift/detail?id=" + id, owner); long detailSecond = (System.nanoTime() - start) / 1000000;
            start = System.nanoTime(); var page = get("/ledger/gift/list", owner); long pageFirst = (System.nanoTime() - start) / 1000000;
            start = System.nanoTime(); get("/ledger/gift/list", owner); long pageSecond = (System.nanoTime() - start) / 1000000;
            int bytes = mapper.writeValueAsBytes(detail.getBody()).length;
            if (detailBytes != 0) assertThat(bytes).isEqualTo(detailBytes);
            detailBytes = bytes;
            assertThat(ok(page).path("list")).hasSize(20);
            assertThat(ok(page).path("total").asLong()).isEqualTo(size);
            assertThat(ok(old).path("records")).hasSize(size);
            report.append("| ").append(size).append(" | ").append(mapper.writeValueAsBytes(old.getBody()).length).append(" | ").append(bytes)
                    .append(" | ").append(mapper.writeValueAsBytes(page.getBody()).length).append(" | ").append(fullFirst).append('/').append(fullSecond)
                    .append(" | ").append(detailFirst).append('/').append(detailSecond).append(" | ").append(pageFirst).append('/').append(pageSecond).append(" |\n");
            previous = size;
        }
        report.append("\n同一详情为 1 次查询接口请求，列表为 1 次分页接口请求；两者均不发全量请求。SQL 页大小为20，详情体积在规模变化中严格相同。亲友汇总页的分组SQL恒为2条，不随页大小增加；另有现有亲友资料计数与分页SQL。\n");
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/礼账查询H2规模验证.md"), report);
    }

    @Test
    void recentRecordsKeepRecentlyEditedOldBusinessDatesAtTheTop() {
        String owner = account();
        JsonNode oldGift = ok(post("/ledger/gift/create", Map.of("eventType", "birthday", "direction", "received",
                "name", "旧日期新修改", "amountCents", 1000, "date", "2020-01-01"), owner)).path("record");
        ok(post("/ledger/gift/create", Map.of("eventType", "birthday", "direction", "received",
                "name", "新业务日期", "amountCents", 1000, "date", "2026-10-02"), owner));
        jdbc.update("UPDATE relations.ledger_gift SET updated_at=? WHERE id=?",
                java.sql.Timestamp.from(java.time.Instant.parse("2030-01-01T00:00:00Z")), oldGift.path("id").asLong());
        JsonNode recent = ok(get("/ledger/record/recent?limit=1", owner));
        assertThat(recent).hasSize(1);
        assertThat(recent.get(0).path("id")).isEqualTo(oldGift.path("id"));
        assertThat(recent.get(0).path("date").asText()).isEqualTo("2020-01-01");
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

