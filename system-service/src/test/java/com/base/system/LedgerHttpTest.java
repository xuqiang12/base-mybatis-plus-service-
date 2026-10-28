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

    @Autowired private JdbcTemplate jdbc;

    @Test
    void recordTimesAreReturnedOnCreationEditingAndReloading() {
        String owner = account();
        JsonNode categories = ok(get("/ledger/data", owner)).path("householdCategories");
        long income = 0;
        for (JsonNode category : categories) {
            if ("income".equals(category.path("type").asText())) {
                income = category.path("id").asLong();
                break;
            }
        }
        JsonNode household = ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", income,
                "amountCents", 100000, "date", "2026-10-01"), owner));
        JsonNode gift = ok(post("/ledger/gift/create", Map.of("eventType", "birthday", "direction", "received",
                "name", "张三", "amountCents", 100, "date", "2026-10-02"), owner)).path("record");
        JsonNode loan = ok(post("/ledger/loan/create", Map.of("direction", "borrow", "name", "李四",
                "amountCents", 100, "date", "2026-10-03"), owner)).path("record");
        for (JsonNode record : java.util.List.of(household, gift, loan)) {
            assertRecordTimes(record);
        }
        JsonNode initial = ok(get("/ledger/data", owner));
        assertThat(initial.path("records").get(0).path("updatedAt")).isEqualTo(gift.path("updatedAt"));
        assertThat(initial.path("loanRecords").get(0).path("updatedAt")).isEqualTo(loan.path("updatedAt"));
        for (JsonNode record : initial.path("householdRecords")) assertRecordTimes(record);

        var oldTime = java.sql.Timestamp.from(java.time.Instant.parse("2026-10-01T01:02:03Z"));
        jdbc.update("UPDATE relations.ledger_gift SET created_at = ?, updated_at = ? WHERE id = ?",
                oldTime, oldTime, gift.path("id").asLong());
        jdbc.update("UPDATE relations.ledger_household_item SET created_at = ?, updated_at = ? WHERE id = ?",
                oldTime, oldTime, household.path("id").asLong());
        JsonNode changedGift = ok(post("/ledger/gift/update", Map.of("id", gift.path("id").asLong(),
                "eventType", "birthday", "direction", "received", "name", "张三", "amountCents", 200,
                "friendId", gift.path("friendId").asLong(), "date", "2026-10-02"), owner)).path("record");
        JsonNode changedHousehold = ok(post("/ledger/household/update", Map.of("id", household.path("id").asLong(),
                "type", "income", "categoryId", income, "amountCents", 100000,
                "date", "2026-10-01", "note", "调整备注"), owner));
        for (JsonNode record : java.util.List.of(changedGift, changedHousehold)) {
            assertRecordTimes(record);
            assertThat(record.path("createdAt").asText()).isEqualTo("2026-10-01 09:02:03");
            assertThat(record.path("updatedAt").asText()).isGreaterThan(record.path("createdAt").asText());
        }
        JsonNode reloaded = ok(get("/ledger/data", owner));
        assertThat(reloaded.path("records").get(0).path("updatedAt")).isEqualTo(changedGift.path("updatedAt"));
        for (JsonNode record : reloaded.path("householdRecords")) {
            if (record.path("id").asLong() == household.path("id").asLong()) {
                assertThat(record.path("updatedAt")).isEqualTo(changedHousehold.path("updatedAt"));
                assertThat(record.path("createdAt")).isEqualTo(changedHousehold.path("createdAt"));
            }
        }
    }

    private void assertRecordTimes(JsonNode record) {
        var format = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        assertThat(record.path("createdAt").asText()).isNotBlank();
        assertThat(record.path("updatedAt").asText()).isNotBlank();
        java.time.LocalDateTime.parse(record.path("createdAt").asText(), format);
        java.time.LocalDateTime.parse(record.path("updatedAt").asText(), format);
    }

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
