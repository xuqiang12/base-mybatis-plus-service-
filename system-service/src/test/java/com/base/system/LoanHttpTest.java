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
    @Autowired private JdbcTemplate jdbc;

    @Test
    void selectedAccountChangesOnceAndReturnsProtectedSavingRecords() {
        String token = account();
        salary(token);
        JsonNode money = ok(post("/ledger/money/transfer", Map.of("type", "bank", "name", "借贷银行卡",
                "amountCents", 20000), token));
        long bank = money.path("projects").get(1).path("id").asLong();
        JsonNode lent = ok(post("/ledger/loan/create", Map.of("direction", "lend", "name", "张三",
                "amountCents", 10000, "date", "2026-10-20", "projectId", bank, "note", "用户备注"), token));
        JsonNode record = lent.path("record");
        assertThat(record.path("projectId").asLong()).isEqualTo(bank);
        assertThat(record.path("note").asText()).isEqualTo("用户备注");
        JsonNode saving = lent.path("householdRecord");
        assertThat(saving.path("loanId").asLong()).isEqualTo(record.path("id").asLong());
        assertThat(saving.path("savingEffect").asText()).isEqualTo("add");
        assertThat(saving.path("projectId").asLong()).isEqualTo(bank);
        assertThat(saving.path("date").asText()).isEqualTo("2026-10-01");
        assertThat(saving.path("note").asText()).isEqualTo("借给张三100元");
        JsonNode received = ok(post("/ledger/loan/create", Map.of("direction", "receive", "name", "张三",
                "friendId", record.path("friendId").asLong(), "amountCents", 3000,
                "date", "2026-10-21", "projectId", bank), token));
        assertThat(received.path("householdRecord").path("savingEffect").asText()).isEqualTo("subtract");
        assertThat(received.path("householdRecord").path("note").asText()).isEqualTo("收回张三30元");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        JsonNode summary = ok(http.exchange("/ledger/money", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class));
        assertThat(summary.path("cashCents").asLong()).isEqualTo(80000);
        assertThat(summary.path("projects").get(1).path("balanceCents").asLong()).isEqualTo(13000);
        assertThat(summary.path("lentCents").asLong()).isEqualTo(7000);
        assertThat(summary.path("totalCents").asLong()).isEqualTo(100000);
        assertThat(jdbc.queryForObject("SELECT saving_transfer FROM relations.ledger_household_item WHERE id=?",
                Boolean.class, saving.path("id").asLong())).isFalse();
        assertThat(post("/ledger/household/update", Map.of("id", saving.path("id").asLong(), "type", "saving",
                "categoryId", saving.path("categoryId").asLong(), "amountCents", 1,
                "date", "2026-10-01", "projectId", bank), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("/ledger/household/delete", Map.of("id", saving.path("id").asLong()), token)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void insufficientSelectedAccountRollsBackFriendLoanSavingAndMonth() {
        String token = account();
        salary(token);
        long bank = ok(post("/ledger/money/project/create", Map.of("type", "bank", "name", "空银行卡"), token))
                .path("projects").get(1).path("id").asLong();
        String family = jdbc.queryForObject("SELECT family_id FROM relations.ledger_money_project WHERE id=?", String.class, bank);
        long householdCount = jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_household_item WHERE family_id=?", Long.class, family);
        ResponseEntity<JsonNode> response = post("/ledger/loan/create", Map.of("direction", "lend", "name", "失败亲友",
                "amountCents", 100, "date", "2026-11-20", "projectId", bank), token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().toString()).contains("空银行卡余额不足");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_friend WHERE family_id=?", Long.class, family)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_loan WHERE family_id=?", Long.class, family)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_household_item WHERE family_id=?", Long.class, family)).isEqualTo(householdCount);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_household_month WHERE family_id=? AND month_value='2026-11'", Long.class, family)).isZero();
        assertThat(post("/ledger/loan/create", Map.of("direction", "borrow", "name", "跨家庭",
                "amountCents", 100, "date", "2026-10-20", "projectId", bank), account()).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void borrowAndPayUseSelectedAccountAndProtectLoanOnlyReferences() {
        String token = account();
        long bank = ok(post("/ledger/money/project/create", Map.of("type", "bank", "name", "借入账户"), token))
                .path("projects").get(1).path("id").asLong();
        JsonNode borrowed = ok(post("/ledger/loan/create", Map.of("direction", "borrow", "name", "李四",
                "amountCents", 10000, "date", "2026-10-20", "projectId", bank), token));
        assertThat(borrowed.path("record").path("projectId").asLong()).isEqualTo(bank);
        assertThat(borrowed.path("householdRecord").isNull()).isTrue();
        ok(post("/ledger/loan/create", Map.of("direction", "pay", "name", "李四", "friendId", borrowed.path("record").path("friendId").asLong(),
                "amountCents", 10000, "date", "2026-10-21", "projectId", bank), token));
        assertThat(post("/ledger/money/project/delete", Map.of("id", bank), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void repaymentCannotExceedOutstandingBalanceAndBlocksFriendDelete() {
        String token = account();
        salary(token);
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
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        JsonNode data = ok(http.exchange("/ledger/data", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class));
        assertThat(data.path("loanRecords")).hasSize(2);
        assertThat(data.path("householdRecords").findValues("loanId").stream().filter(id -> !id.isNull())).hasSize(2);
    }

    @Test
    void concurrentRepaymentsCannotSpendTheSameOutstandingBalance() throws Exception {
        String token = account();
        salary(token);
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

    @Test
    void sameNameNeedsConfirmationAndConfirmedCreationStillRejectsAnExactDuplicate() {
        String token = account();
        JsonNode brother = ok(post("/ledger/loan/create", Map.of("direction", "borrow", "name", "张三",
                "appellation", "兄弟", "amountCents", 100, "date", "2026-10-29"), token));
        var request = Map.of("direction", "borrow", "name", " 张三 ", "appellation", " ",
                "amountCents", 200, "date", "2026-10-29");
        ResponseEntity<JsonNode> first = post("/ledger/loan/create", request, token);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode confirmation = first.getBody().path("data");
        assertThat(confirmation.path("reason").asText()).isEqualTo("LOAN_FRIEND_CONFIRM_REQUIRED");
        assertThat(confirmation.path("canCreateNew").asBoolean()).isTrue();
        assertThat(confirmation.path("friends")).hasSize(1);
        assertThat(confirmation.path("friends").get(0).path("id")).isEqualTo(brother.path("friend").path("id"));
        JsonNode before = data(token);
        assertThat(before.path("friends")).hasSize(1);
        assertThat(before.path("loanRecords")).hasSize(1);
        JsonNode created = ok(post("/ledger/loan/create", Map.of("direction", "borrow", "name", " 张三 ",
                "appellation", " ", "allowSameName", true, "amountCents", 200, "date", "2026-10-29"), token));
        assertThat(created.path("friend").path("appellation").asText()).isEmpty();
        assertThat(created.path("friend").path("id")).isNotEqualTo(brother.path("friend").path("id"));
        ResponseEntity<JsonNode> repeated = post("/ledger/loan/create", Map.of("direction", "borrow", "name", "张三",
                "allowSameName", true, "amountCents", 300, "date", "2026-10-29"), token);
        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode exact = repeated.getBody().path("data");
        assertThat(exact.path("canCreateNew").asBoolean()).isFalse();
        assertThat(exact.path("friends")).hasSize(1);
        assertThat(exact.path("friends").get(0).path("id")).isEqualTo(created.path("friend").path("id"));
        assertThat(data(token).path("friends")).hasSize(2);
        assertThat(data(token).path("loanRecords")).hasSize(2);
    }

    @Test
    void allSameNameCandidatesAreReturnedAndYesUsesTheChosenFriendWithoutCreatingOne() {
        String token = account();
        JsonNode first = ok(post("/friend/create", Map.of("name", "张三", "appellation", "兄弟"), token));
        JsonNode second = ok(post("/friend/create", Map.of("name", "张三", "appellation", "同事"), token));
        var request = Map.of("direction", "borrow", "name", "张三", "appellation", "同学",
                "amountCents", 100, "date", "2026-10-29");
        ResponseEntity<JsonNode> response = post("/ledger/loan/create", request, token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().path("data").path("friends")).hasSize(2);
        assertThat(response.getBody().path("data").path("canCreateNew").asBoolean()).isTrue();
        assertThat(data(token).path("loanRecords")).isEmpty();
        JsonNode saved = ok(post("/ledger/loan/create", Map.of("direction", "borrow", "name", "张三",
                "friendId", second.path("id").asLong(), "amountCents", 100, "date", "2026-10-29"), token));
        assertThat(saved.path("record").path("friendId")).isEqualTo(second.path("id"));
        assertThat(saved.path("friend").isNull()).isTrue();
        assertThat(data(token).path("friends")).hasSize(2);
        assertThat(saved.path("record").path("friendId")).isNotEqualTo(first.path("id"));
        String otherFamily = account();
        ok(post("/ledger/loan/create", request, otherFamily));
    }

    @Test
    void identicalNonemptyAppellationAlsoDisablesNewAndConfirmationDoesNotChangeMoney() {
        String token = account();
        salary(token);
        ok(post("/ledger/loan/create", Map.of("direction", "lend", "name", "张三", "appellation", "兄弟",
                "amountCents", 100, "date", "2026-10-29"), token));
        JsonNode before = data(token);
        ResponseEntity<JsonNode> duplicate = post("/ledger/loan/create", Map.of("direction", "lend", "name", "张三",
                "appellation", " 兄弟 ", "allowSameName", true, "amountCents", 200, "date", "2026-10-29"), token);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().path("data").path("canCreateNew").asBoolean()).isFalse();
        JsonNode after = data(token);
        assertThat(after.path("friends")).isEqualTo(before.path("friends"));
        assertThat(after.path("loanRecords")).isEqualTo(before.path("loanRecords"));
        assertThat(after.path("householdRecords")).isEqualTo(before.path("householdRecords"));
    }

    @Test
    void concurrentConfirmedCreationsCannotCreateTwoIdenticalFriends() throws Exception {
        String token = account();
        ok(post("/friend/create", Map.of("name", "张三", "appellation", "兄弟"), token));
        var body = Map.of("direction", "borrow", "name", "张三", "allowSameName", true,
                "amountCents", 100, "date", "2026-10-29");
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> action = () -> { start.await(); return post("/ledger/loan/create", body, token).getStatusCode().value(); };
            var first = pool.submit(action);
            var second = pool.submit(action);
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(data(token).path("friends")).hasSize(2);
        assertThat(data(token).path("loanRecords")).hasSize(1);
    }

    private JsonNode data(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return ok(http.exchange("/ledger/data", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class));
    }

    private String account() {
        String email = UUID.randomUUID() + "@example.test";
        ok(post("/auth/email/register", Map.of("email", email, "code", "123456",
                "nickname", "测试家人", "password", "loan-test-password"), null));
        return ok(post("/auth/login", Map.of("account", email, "password", "loan-test-password"), null))
                .path("token").asText();
    }

    private void salary(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        JsonNode data = ok(http.exchange("/ledger/data", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class));
        for (JsonNode category : data.path("householdCategories")) {
            if ("income".equals(category.path("type").asText())) {
                ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", category.path("id").asLong(),
                        "amountCents", 100000, "date", "2026-10-01"), token));
                return;
            }
        }
        throw new AssertionError("工资分类不存在");
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
