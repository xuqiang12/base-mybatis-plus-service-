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
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 家庭收支分类、独立新增记录和家庭归属 HTTP 契约测试。 */
@SpringBootTest(classes = SystemApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:householdCrud;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "app.email-code.test-mode=true"
        })
@ActiveProfiles("test")
class HouseholdHttpTest {
    @Autowired
    private TestRestTemplate http;

    @Autowired private JdbcTemplate jdbc;

    @Test
    void repeatedSalaryEntriesAppendAndFailedBatchKeepsBothOriginals() {
        String token = account();
        JsonNode data = ok(get("/ledger/data", token));
        long income = findCategory(data.path("householdCategories"), "income", "工资");
        long expense = findCategory(data.path("householdCategories"), "expense", "餐饮");
        JsonNode mine = ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", income,
                "amountCents", 1119000, "date", "2026-10-01", "note", "我的工资"), token));
        JsonNode split = ok(post("/ledger/money/transfer", Map.of("type", "bank", "name", "农业银行", "amountCents", 10000), token));
        long bank = split.path("projects").get(1).path("id").asLong();
        JsonNode month = ok(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", List.of(
                Map.of("categoryId", income, "type", "income", "amountCents", 800000, "projectId", bank, "note", "对象的工资"))), token));
        assertThat(month.path("items")).hasSize(2);
        assertThat(month.path("items").findValues("id")).doesNotHaveDuplicates();
        assertThat(month.path("items").findValuesAsText("note")).containsExactlyInAnyOrder("我的工资", "对象的工资");
        JsonNode money = ok(get("/ledger/money", token));
        assertThat(money.path("heldCents").asLong()).isEqualTo(1919000);
        assertThat(money.path("cashCents").asLong()).isEqualTo(1109000);
        assertThat(money.path("projects").get(1).path("balanceCents").asLong()).isEqualTo(810000);
        assertThat(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", List.of(
                Map.of("categoryId", income, "type", "income", "amountCents", 1000),
                Map.of("categoryId", expense, "type", "expense", "amountCents", 9999999))), token)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode unchanged = ok(get("/ledger/household/month?month=2026-10", token));
        assertThat(unchanged.path("items")).isEqualTo(month.path("items"));
        assertThat(jdbc.queryForObject("SELECT amount_cents FROM relations.ledger_household_item WHERE id=?",
                Long.class, mine.path("id").asLong())).isEqualTo(1119000);
        JsonNode next = ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", income,
                "amountCents", 1000, "date", "2026-10-01", "note", "补发工资"), token));
        assertThat(next.path("id")).isNotEqualTo(mine.path("id"));
        assertThat(ok(get("/ledger/household/month?month=2026-10", token)).path("items")).hasSize(3);
        assertThat(ok(get("/ledger/money", token)).path("cashCents").asLong()).isEqualTo(1110000);
    }

    @Test
    void monthBatchAppendsAtomicallyAndReusesOneMonth() {
        String token = account();
        JsonNode data = ok(get("/ledger/data", token));
        long income = findCategory(data.path("householdCategories"), "income", "工资");
        long expense = findCategory(data.path("householdCategories"), "expense", "餐饮");
        var items = List.of(Map.of("categoryId", income, "type", "income", "amountCents", 500000),
                Map.of("categoryId", expense, "type", "expense", "amountCents", 12000));
        JsonNode first = ok(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", items), token));
        assertThat(first.path("items")).hasSize(2);
        long id = first.path("id").asLong();
        assertThat(id).isPositive();
        JsonNode second = ok(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", List.of(
                Map.of("categoryId", expense, "type", "expense", "amountCents", 15000))), token));
        assertThat(second.path("id").asLong()).isEqualTo(id);
        assertThat(second.path("items")).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_household_month WHERE id=?", Integer.class, id)).isEqualTo(1);
        assertThat(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", List.of(
                Map.of("categoryId", income, "type", "income", "amountCents", 700000),
                Map.of("categoryId", expense, "type", "income", "amountCents", 20000))), token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode after = ok(get("/ledger/household/month?month=2026-10", token));
        assertThat(after.path("items").toString()).contains("500000", "15000").doesNotContain("700000");
        String other = account();
        assertThat(ok(get("/ledger/household/month?month=2026-10", other)).path("items")).isEmpty();
        assertThat(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", items), other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", List.of(
                Map.of("categoryId", expense, "type", "expense", "amountCents", 0))), token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ok(get("/ledger/household/month?month=2026-10", token)).path("items")).hasSize(3);
    }

    @Test
    void allocatingBalanceAppendsExpenseAndPreservesHistoricalSavings() {
        String token = account();
        JsonNode data = ok(get("/ledger/data", token));
        long income = findCategory(data.path("householdCategories"), "income", "工资");
        long expense = findCategory(data.path("householdCategories"), "expense", "餐饮");
        long saving = findCategory(data.path("householdCategories"), "saving", "存款");
        JsonNode first = ok(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", List.of(
                Map.of("categoryId", income, "type", "income", "amountCents", 1000000),
                Map.of("categoryId", expense, "type", "expense", "amountCents", 600000))), token));
        jdbc.update("INSERT INTO relations.ledger_household_item(month_id,family_id,category_id,type,amount_cents,project_id,created_by,created_by_name) "
                + "SELECT month_id,family_id,?,'saving',300000,project_id,created_by,created_by_name "
                + "FROM relations.ledger_household_item WHERE category_id=?", saving, income);
        JsonNode added = ok(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", List.of(
                Map.of("categoryId", expense, "type", "expense", "amountCents", 50000))), token));
        assertThat(added.path("id")).isEqualTo(first.path("id"));
        assertThat(added.path("items")).hasSize(4);
        assertThat(added.path("items").toString()).contains("600000", "300000", "50000");
        assertThat(ok(get("/ledger/money", token)).path("cashCents").asLong()).isEqualTo(350000);
    }

    @Test
    void categoryUpdateDoesNotRequireAnUnchangeableType() {
        String token = account();
        JsonNode data = ok(get("/ledger/data", token));
        long id = findCategory(data.path("householdCategories"), "expense", "餐饮");
        JsonNode updated = ok(post("/ledger/category/update", Map.of("id", id, "name", "伙食", "icon", "fork", "active", false), token));
        assertThat(updated.path("name").asText()).isEqualTo("伙食");
        assertThat(updated.path("active").asBoolean()).isFalse();
    }

    @Test
    void oneFamilyMonthAllowsRepeatedCategoriesAndEnforcesOwnership() {
        String token = account();
        JsonNode data = ok(get("/ledger/data", token));
        long expenseCategory = findCategory(data.path("householdCategories"), "expense", "餐饮");
        long incomeCategory = findCategory(data.path("householdCategories"), "income", "工资");
        long savingCategory = findCategory(data.path("householdCategories"), "saving", "存款");

        // 支出现在会校验资金来源，先记录真实收入。
        ok(post("/ledger/household/save", Map.of(
                "type", "income", "categoryId", incomeCategory, "amountCents", 500000,
                "date", "2026-10-20", "note", "工资"), token));

        JsonNode first = ok(post("/ledger/household/save", Map.of(
                "type", "expense", "categoryId", expenseCategory, "amountCents", 12000,
                "date", "2026-10-01", "note", "第一次填写"), token));
        long firstId = first.path("id").asLong();
        JsonNode second = ok(post("/ledger/household/save", Map.of(
                "type", "expense", "categoryId", expenseCategory, "amountCents", 15000,
                "date", "2026-10-20", "note", "补填"), token));
        assertThat(second.path("id").asLong()).isNotEqualTo(firstId);

        ok(post("/ledger/household/save", Map.of(
                "type", "income", "categoryId", incomeCategory, "amountCents", 500000,
                "date", "2026-10-20", "note", "工资"), token));
        ok(post("/ledger/household/save", Map.of(
                "type", "saving", "categoryId", savingCategory, "amountCents", 100000,
                "projectId", bankProject(token), "date", "2026-10-20", "note", "存款"), token));

        JsonNode after = ok(get("/ledger/data", token));
        assertThat(after.path("householdRecords")).hasSize(5);
        JsonNode expense = after.path("householdRecords").findValue("amountCents");
        assertThat(expense).isNotNull();
        assertThat(after.path("householdRecords").toString()).contains("15000", "500000", "100000");

        ResponseEntity<JsonNode> mismatch = post("/ledger/household/save", Map.of(
                "type", "income", "categoryId", expenseCategory, "amountCents", 1,
                "date", "2026-10-20", "note", ""), token);
        assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<JsonNode> deleteUsed = post("/ledger/category/delete", Map.of("id", expenseCategory), token);
        assertThat(deleteUsed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        String other = account();
        ResponseEntity<JsonNode> crossFamily = post("/ledger/household/update", Map.of(
                "id", firstId, "type", "expense", "categoryId", expenseCategory,
                "amountCents", 1, "date", "2026-10-01", "note", ""), other);
        assertThat(crossFamily.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private long bankProject(String token) {
        String family = ok(get("/family/state", token)).path("family").path("id").asText();
        jdbc.update("INSERT INTO relations.ledger_money_project(family_id,type,name) VALUES (?,'bank','农业银行')", family);
        return jdbc.queryForObject("SELECT id FROM relations.ledger_money_project WHERE family_id=? AND type='bank'", Long.class, family);
    }

    private long findCategory(JsonNode categories, String type, String name) {
        for (JsonNode category : categories) {
            if (type.equals(category.path("type").asText()) && name.equals(category.path("name").asText()))
                return category.path("id").asLong();
        }
        throw new AssertionError("category not found: " + type + "/" + name);
    }

    private String account() {
        String email = UUID.randomUUID() + "@example.test";
        ok(post("/auth/email/register", Map.of("email", email, "code", "123456",
                "nickname", "测试家人", "password", "household-test-password"), null));
        return ok(post("/auth/login", Map.of("account", email, "password", "household-test-password"), null))
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
