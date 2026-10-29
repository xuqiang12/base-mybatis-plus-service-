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
import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SystemApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:giftMoney;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "app.email-code.test-mode=true"
        })
@ActiveProfiles("test")
class GiftMoneyHttpTest {
    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void receivedAndSentGiftsUseSelectedAccountsAndCreateOneLinkedHouseholdRecordEach() {
        String token = account();
        JsonNode summary = ok(post("/ledger/money/project/create", Map.of("type", "bank", "name", "礼金银行卡"), token));
        long bank = summary.path("projects").get(1).path("id").asLong();
        JsonNode received = ok(post("/ledger/gift/create", gift("received", "张三", 50000, bank), token));
        long giftId = received.path("record").path("id").asLong();
        assertThat(received.path("record").path("projectId").asLong()).isEqualTo(bank);
        assertThat(received.path("householdRecord").path("giftId").asLong()).isEqualTo(giftId);
        assertThat(received.path("householdRecord").path("projectId").asLong()).isEqualTo(bank);
        assertThat(received.path("householdRecord").path("projectType").asText()).isEqualTo("bank");
        assertThat(received.path("householdRecord").path("type").asText()).isEqualTo("income");
        assertThat(received.path("householdRecord").path("date").asText()).isEqualTo("2026-09-23");
        assertThat(received.path("householdRecord").path("categoryId").isNull()).isTrue();
        assertThat(received.has("householdCategory")).isFalse();
        assertBalances(token, 0, 50000);

        JsonNode sent = ok(post("/ledger/gift/create", gift("sent", "李四", 20000, bank), token));
        assertThat(sent.path("householdRecord").path("type").asText()).isEqualTo("expense");
        assertThat(sent.path("householdRecord").path("categoryId").isNull()).isTrue();
        assertBalances(token, 0, 30000);
        JsonNode month = ok(get("/ledger/household/month?month=2026-09", token));
        assertThat(month.path("items")).hasSize(2);
        assertThat(month.path("items").findValuesAsText("projectType")).containsExactly("bank", "bank");
        assertThat(month.path("items").findValuesAsText("date")).containsOnly("2026-09-23");
        assertThat(ok(get("/ledger/data", token)).path("householdRecords")).hasSize(2);
        assertThat(ok(get("/ledger/money/timeline", token)).path("items")).hasSize(2);
        JsonNode log = ok(get("/ledger/money/timeline?keyword=张三", token)).path("items");
        assertThat(log).hasSize(1);
        assertThat(log.get(0).path("flow").asText()).contains("张三", "礼金银行卡");

        ok(post("/ledger/gift/create", gift("received", "王五", 100, bank), token));
        JsonNode data = ok(get("/ledger/data", token));
        assertThat(data.path("householdCategories").findValuesAsText("name")).doesNotContain("人情收礼", "人情送礼");
        assertThat(data.path("householdRecords")).hasSize(3);
        assertThat(data.path("householdRecords").findValuesAsText("projectType")).containsOnly("bank").hasSize(3);
        assertBalances(token, 0, 30100);
    }

    @Test
    void omittedAccountDefaultsToCashAndOldGiftsNeverCreateIncomeOnRead() {
        String token = account();
        ok(post("/ledger/gift/create", Map.of("eventType", "birthday", "direction", "received",
                "name", "新礼金", "amountCents", 1000, "date", "2026-09-23"), token));
        String family = jdbc.queryForObject("SELECT family_id FROM relations.ledger_gift WHERE name='新礼金'", String.class);
        jdbc.update("INSERT INTO relations.ledger_gift(family_id,event_type,direction,date,name,amount_cents,created_by,created_by_name) "
                + "SELECT family_id,'wedding','received',DATE '2025-01-01','旧礼金',90000,created_by,created_by_name "
                + "FROM relations.ledger_gift WHERE family_id=? AND name='新礼金'", family);
        assertBalances(token, 1000, 1000);
        JsonNode data = ok(get("/ledger/data", token));
        assertThat(data.path("records")).hasSize(2);
        assertThat(data.path("householdRecords")).hasSize(1);
        assertThat(data.path("householdRecords").get(0).path("projectType").asText()).isEqualTo("cash");
        assertThat(ok(get("/ledger/household/month?month=2025-01", token)).path("items")).isEmpty();
        assertBalances(token, 1000, 1000);
    }

    @Test
    void insufficientSourceRollsBackGiftFriendHouseholdAndMoneyLog() {
        String token = account();
        long bank = ok(post("/ledger/money/project/create", Map.of("type", "bank", "name", "空账户"), token))
                .path("projects").get(1).path("id").asLong();
        assertThat(post("/ledger/gift/create", gift("sent", "失败亲友", 1, bank), token).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        JsonNode data = ok(get("/ledger/data", token));
        assertThat(data.path("records")).isEmpty();
        assertThat(data.path("friends")).isEmpty();
        assertThat(data.path("householdRecords")).isEmpty();
        assertThat(data.path("householdCategories").findValuesAsText("name")).doesNotContain("人情送礼");
        assertThat(ok(get("/ledger/money/timeline", token)).path("items")).isEmpty();
        assertBalances(token, 0, 0);
    }

    @Test
    void linkedHouseholdCannotBeChangedDeletedOrAccessedFromAnotherFamily() {
        String token = account();
        long cash = ok(get("/ledger/money", token)).path("projects").get(0).path("id").asLong();
        JsonNode created = ok(post("/ledger/gift/create", gift("received", "关联亲友", 50000, cash), token));
        long id = created.path("householdRecord").path("id").asLong();
        assertThat(id).isPositive();
        assertThat(post("/ledger/household/delete", Map.of("id", id), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("/ledger/household/update", Map.of("id", id, "type", "income", "categoryId",
                ok(get("/ledger/data", token)).path("householdCategories").get(0).path("id").asLong(), "date", "2026-09-23",
                "amountCents", 1), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        String other = account();
        assertThat(post("/ledger/gift/create", gift("received", "跨家庭", 100, cash), other).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/ledger/household/delete", Map.of("id", id), other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ok(get("/ledger/data", other)).path("records")).isEmpty();
        assertBalances(token, 50000, 50000);
    }

    @Test
    void editReplacesLinkedFlowAndMovesMonthWithoutChangingRecordIds() {
        String token = account();
        long cash = ok(get("/ledger/money", token)).path("projects").get(0).path("id").asLong();
        long bank = ok(post("/ledger/money/project/create", Map.of("type", "bank", "name", "银行卡"), token))
                .path("projects").get(1).path("id").asLong();
        ok(post("/ledger/gift/create", gift("received", "现金礼金", 10000, cash), token));
        long book = ok(post("/ledger/book/create", Map.of("name", "婚礼账簿"), token)).path("id").asLong();
        Map<String, Object> fields = new HashMap<>(gift("received", "待修改", 5000, bank));
        fields.put("bookId", book);
        JsonNode created = ok(post("/ledger/gift/create", fields, token));
        long id = created.path("record").path("id").asLong();
        fields.put("id", id);
        fields.put("friendId", created.path("friend").path("id").asLong());
        fields.put("direction", "sent");
        fields.put("date", "2026-10-24");
        fields.put("projectId", cash);
        fields.put("amountCents", 2000);
        fields.put("note", "已修改");
        JsonNode updated = ok(post("/ledger/gift/update", fields, token));
        assertThat(updated.path("record").path("id").asLong()).isEqualTo(id);
        assertThat(updated.path("record").path("bookId").isNull()).isTrue();
        assertThat(updated.path("record").path("createdBy")).isEqualTo(created.path("record").path("createdBy"));
        assertThat(updated.path("householdRecord").path("id")).isEqualTo(created.path("householdRecord").path("id"));
        assertThat(updated.path("householdRecord").path("date").asText()).isEqualTo("2026-10-24");
        assertThat(updated.path("householdRecord").path("projectType").asText()).isEqualTo("cash");
        assertThat(updated.path("householdRecord").path("categoryId").isNull()).isTrue();
        assertThat(ok(get("/ledger/household/month?month=2026-10", token)).path("items")).hasSize(1);
        assertThat(ok(get("/ledger/household/month?month=2026-09", token)).path("items")).hasSize(1);
        assertBalances(token, 8000, 8000);
        JsonNode log = ok(get("/ledger/money/timeline?keyword=待修改", token)).path("items");
        assertThat(log).hasSize(2);
        assertThat(log.findValuesAsText("action")).contains("create", "update");
        ok(post("/ledger/gift/delete", Map.of("id", id), token));
        assertBalances(token, 10000, 10000);
        assertThat(ok(get("/ledger/household/month?month=2026-10", token)).path("items")).isEmpty();
        assertThat(ok(get("/ledger/data", token)).path("friends")).hasSize(2);
        assertThat(ok(get("/ledger/money/timeline?keyword=待修改", token)).path("items").findValuesAsText("action"))
                .contains("create", "update", "delete");
    }

    @Test
    void insufficientEditAndDeleteRollBackAllLinkedChanges() {
        String token = account();
        long cash = ok(get("/ledger/money", token)).path("projects").get(0).path("id").asLong();
        JsonNode receipt = ok(post("/ledger/gift/create", gift("received", "收入", 10000, cash), token));
        ok(post("/ledger/gift/create", gift("sent", "支出", 8000, cash), token));
        long id = receipt.path("record").path("id").asLong();
        JsonNode before = ok(get("/ledger/data", token));
        Map<String, Object> fields = new HashMap<>(gift("received", "不应创建的亲友", 1000, cash));
        fields.put("id", id);
        fields.put("date", "2026-10-25");
        assertThat(post("/ledger/gift/update", fields, token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("/ledger/gift/delete", Map.of("id", id), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ok(get("/ledger/data", token))).isEqualTo(before);
        assertThat(ok(get("/ledger/money/timeline", token)).path("items")).hasSize(2);
        assertBalances(token, 2000, 2000);
    }

    @Test
    void legacyEditAndDeleteNeverCreateMoneyFlowOrRecreateDeletedFriend() {
        String token = account();
        long cash = ok(get("/ledger/money", token)).path("projects").get(0).path("id").asLong();
        JsonNode created = ok(post("/ledger/gift/create", gift("received", "参照", 1000, cash), token));
        long ref = created.path("record").path("id").asLong();
        jdbc.update("INSERT INTO relations.ledger_gift(family_id,event_type,direction,date,name,amount_cents,created_by,created_by_name) "
                + "SELECT family_id,'wedding','received',DATE '2025-01-01','旧礼金',90000,created_by,created_by_name "
                + "FROM relations.ledger_gift WHERE id=?", ref);
        long old = jdbc.queryForObject("SELECT id FROM relations.ledger_gift WHERE name='旧礼金' AND family_id="
                + "(SELECT family_id FROM relations.ledger_gift WHERE id=?)", Long.class, ref);
        Map<String, Object> fields = new HashMap<>(gift("sent", "旧礼金", 70000, cash));
        fields.put("id", old);
        JsonNode updated = ok(post("/ledger/gift/update", fields, token));
        assertThat(updated.path("record").path("projectId").isNull()).isTrue();
        assertThat(updated.path("record").path("friendId").isNull()).isTrue();
        assertThat(updated.path("householdRecord").isNull()).isTrue();
        assertBalances(token, 1000, 1000);
        ok(post("/ledger/gift/delete", Map.of("id", old), token));
        assertThat(ok(get("/ledger/data", token)).path("householdRecords")).hasSize(1);
        assertThat(ok(get("/ledger/money/timeline", token)).path("items")).hasSize(1);
        long friend = created.path("friend").path("id").asLong();
        ok(post("/friend/delete", Map.of("id", friend), token));
        fields = new HashMap<>(gift("received", "参照", 2000, cash));
        fields.put("id", ref);
        updated = ok(post("/ledger/gift/update", fields, token));
        assertThat(updated.path("record").path("friendId").isNull()).isTrue();
        assertThat(ok(get("/ledger/data", token)).path("friends")).isEmpty();
        ok(post("/ledger/gift/delete", Map.of("id", ref), token));
        assertBalances(token, 0, 0);
    }

    @Test
    void giftWritesRejectOtherFamiliesAndMissingRecords() {
        String token = account();
        long cash = ok(get("/ledger/money", token)).path("projects").get(0).path("id").asLong();
        long id = ok(post("/ledger/gift/create", gift("received", "家庭记录", 1000, cash), token))
                .path("record").path("id").asLong();
        String other = account();
        Map<String, Object> fields = new HashMap<>(gift("received", "家庭记录", 2000, cash));
        fields.put("id", id);
        assertThat(post("/ledger/gift/update", fields, other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/ledger/gift/delete", Map.of("id", id), other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        fields.put("id", Long.MAX_VALUE);
        assertThat(post("/ledger/gift/update", fields, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/ledger/gift/delete", Map.of("id", Long.MAX_VALUE), token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertBalances(token, 1000, 1000);
    }

    @Test
    void editCanChangeFriendAccountAndAmountAndRejectsForeignReferencesAtomically() {
        String token = account();
        long cash = ok(get("/ledger/money", token)).path("projects").get(0).path("id").asLong();
        JsonNode original = ok(post("/ledger/gift/create", gift("received", "原亲友", 10000, cash), token));
        long bank = ok(post("/ledger/money/project/create", Map.of("type", "bank", "name", "新去向"), token))
                .path("projects").get(1).path("id").asLong();
        long id = original.path("record").path("id").asLong();
        Map<String, Object> fields = new HashMap<>(gift("received", "新亲友", 20000, bank));
        fields.put("id", id);
        fields.put("date", "2026-10-25");
        JsonNode changed = ok(post("/ledger/gift/update", fields, token));
        assertThat(changed.path("friend").path("name").asText()).isEqualTo("新亲友");
        assertThat(changed.path("record").path("friendId")).isEqualTo(changed.path("friend").path("id"));
        assertThat(changed.path("householdRecord").path("projectId").asLong()).isEqualTo(bank);
        assertThat(ok(get("/ledger/household/month?month=2026-09", token)).path("items")).isEmpty();
        assertBalances(token, 0, 20000);
        JsonNode before = ok(get("/ledger/data", token));
        String other = account();
        long otherCash = ok(get("/ledger/money", other)).path("projects").get(0).path("id").asLong();
        JsonNode otherGift = ok(post("/ledger/gift/create", gift("received", "别家亲友", 100, otherCash), other));
        fields.put("friendId", otherGift.path("friend").path("id").asLong());
        assertThat(post("/ledger/gift/update", fields, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        fields.remove("friendId");
        fields.put("name", "不应留下");
        fields.put("projectId", otherCash);
        assertThat(post("/ledger/gift/update", fields, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        fields.put("projectId", bank);
        fields.put("bookId", ok(post("/ledger/book/create", Map.of("name", "别家账簿"), other)).path("id").asLong());
        assertThat(post("/ledger/gift/update", fields, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ok(get("/ledger/data", token))).isEqualTo(before);
        assertBalances(token, 0, 20000);
    }

    @Test
    void giftsStayIndependentOfSameNamedHouseholdCategories() {
        String token = account();
        long cash = ok(get("/ledger/money", token)).path("projects").get(0).path("id").asLong();
        long categoryId = ok(post("/ledger/category/create", Map.of("type", "income", "name", "人情收礼", "icon", "money"), token))
                .path("id").asLong();
        JsonNode ordinary = ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", categoryId,
                "amountCents", 500, "date", "2026-09-01"), token));
        JsonNode original = ok(post("/ledger/gift/create", gift("received", "原礼金", 1000, cash), token));
        assertThat(original.path("householdRecord").path("categoryId").isNull()).isTrue();
        ok(post("/ledger/category/delete", Map.of("id", categoryId), token));
        Map<String, Object> fields = new HashMap<>(gift("received", "原礼金", 2000, cash));
        fields.put("id", original.path("record").path("id").asLong());
        JsonNode edited = ok(post("/ledger/gift/update", fields, token));
        assertThat(edited.path("householdRecord").path("categoryId").isNull()).isTrue();
        JsonNode created = ok(post("/ledger/gift/create", gift("received", "分类删除后新增礼金", 1000, cash), token));
        assertThat(created.path("householdRecord").path("categoryId").isNull()).isTrue();
        JsonNode data = ok(get("/ledger/data", token));
        assertThat(data.path("householdCategories").findValuesAsText("name").stream().filter("人情收礼"::equals).count()).isEqualTo(1);
        assertThat(data.path("householdRecords")).contains(ordinary);
        assertBalances(token, 3500, 3500);
    }

    private Map<String, Object> gift(String direction, String name, long amount, long account) {
        return Map.of("eventType", "wedding", "direction", direction, "name", name,
                "amountCents", amount, "date", "2026-09-23", "projectId", account, "note", "婚礼礼金");
    }

    private void assertBalances(String token, long cash, long held) {
        JsonNode value = ok(get("/ledger/money", token));
        assertThat(value.path("cashCents").asLong()).isEqualTo(cash);
        assertThat(value.path("heldCents").asLong()).isEqualTo(held);
        assertThat(value.path("totalCents").asLong()).isEqualTo(held);
        assertThat(value.path("savingsCents").asLong()).isEqualTo(held - cash);
    }

    private String account() {
        String email = UUID.randomUUID() + "@example.test";
        ok(post("/auth/email/register", Map.of("email", email, "code", "123456",
                "nickname", "测试家人", "password", "ledger-test-password"), null));
        return ok(post("/auth/login", Map.of("account", email, "password", "ledger-test-password"), null))
                .path("token").asText();
    }

    private JsonNode ok(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
