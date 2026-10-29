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
    void householdCreationTimeIsReturnedAndPreservedAfterEditing() {
        String token = account();
        long income = findCategory(ok(get("/ledger/data", token)).path("householdCategories"), "income", "工资");
        JsonNode salary = ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", income,
                "amountCents", 80000, "date", "2026-10-01"), token));
        String createdAt = salary.path("createdAt").asText();
        assertThat(createdAt).isNotBlank();
        java.time.LocalDateTime.parse(createdAt, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        jdbc.update("UPDATE relations.ledger_household_item SET created_at = ? WHERE id = ?",
                java.sql.Timestamp.from(java.time.Instant.parse("2026-10-01T01:02:03Z")), salary.path("id").asLong());
        createdAt = "2026-10-01 09:02:03";
        JsonNode changed = ok(post("/ledger/household/update", Map.of("id", salary.path("id").asLong(),
                "type", "income", "categoryId", income, "amountCents", 100000,
                "date", "2026-10-01", "note", "调整金额"), token));
        assertThat(changed.path("createdAt").asText()).isEqualTo(createdAt);
        assertThat(ok(get("/ledger/data", token)).path("householdRecords").get(0).path("createdAt").asText())
                .isEqualTo(createdAt);
        JsonNode month = ok(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", List.of(
                Map.of("categoryId", income, "type", "income", "amountCents", 20000))), token));
        assertThat(month.path("items")).hasSize(2);
        for (JsonNode item : month.path("items")) {
            assertThat(item.path("createdAt").asText()).isNotBlank();
            java.time.LocalDateTime.parse(item.path("createdAt").asText(),
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }
    }

    @Test
    void recordMonthCannotBeChangedByUpdate() {
        String token = account();
        long income = findCategory(ok(get("/ledger/data", token)).path("householdCategories"), "income", "工资");
        JsonNode salary = ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", income,
                "amountCents", 80000, "date", "2026-10-01"), token));
        var rejected = post("/ledger/household/update", Map.of("id", salary.path("id").asLong(), "type", "income",
                "categoryId", income, "amountCents", 100000, "date", "2026-09-01"), token);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rejected.getBody().path("message").asText()).contains("月份不可修改");
        assertThat(ok(get("/ledger/data", token)).path("householdRecords").get(0)).isEqualTo(salary);
        assertThat(ok(get("/ledger/money", token)).path("cashCents").asLong()).isEqualTo(80000);
    }

    @Test
    void editingIncomeUsesOnlyTheDifferenceAndInsufficientReductionRollsBack() {
        String token = account();
        JsonNode data = ok(get("/ledger/data", token));
        long income = findCategory(data.path("householdCategories"), "income", "工资");
        long expense = findCategory(data.path("householdCategories"), "expense", "餐饮");
        JsonNode accounts = ok(post("/ledger/money/project/create", Map.of("type", "bank", "name", "已有存款",
                "initialBalanceCents", 20000), token));
        long cash = accounts.path("projects").get(0).path("id").asLong();
        long bank = accounts.path("projects").get(1).path("id").asLong();
        long id = ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", income,
                "amountCents", 80000, "date", "2026-10-01"), token)).path("id").asLong();
        ok(post("/ledger/household/save", Map.of("type", "expense", "categoryId", expense,
                "amountCents", 80000, "date", "2026-10-01"), token));
        ok(post("/ledger/household/update", Map.of("id", id, "type", "income", "categoryId", income,
                "amountCents", 100000, "date", "2026-10-01"), token));
        assertThat(ok(get("/ledger/money", token)).path("cashCents").asLong()).isEqualTo(20000);
        ok(post("/ledger/household/update", Map.of("id", id, "type", "income", "categoryId", income,
                "amountCents", 80000, "date", "2026-10-01"), token));
        var rejected = post("/ledger/household/update", Map.of("id", id, "type", "income", "categoryId", income,
                "amountCents", 60000, "date", "2026-10-01"), token);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rejected.getBody().path("data").path("shortfallCents").asLong()).isEqualTo(20000);
        assertThat(jdbc.queryForObject("SELECT amount_cents FROM relations.ledger_household_item WHERE id=?", Long.class, id))
                .isEqualTo(80000);
        assertThat(ok(get("/ledger/money", token)).path("cashCents").asLong()).isZero();
        ok(post("/ledger/money/transfer", Map.of("fromProjectId", bank, "toProjectId", cash, "amountCents", 20000), token));
        ok(post("/ledger/household/update", Map.of("id", id, "type", "income", "categoryId", income,
                "amountCents", 60000, "date", "2026-10-01"), token));
        assertThat(ok(get("/ledger/money", token)).path("cashCents").asLong()).isZero();
        assertThat(ok(get("/ledger/data", token)).path("householdRecords")).hasSize(2);
    }

    @Test
    void deletingSpentIncomeReportsCashShortfallAndCanRetryAfterTransfer() {
        String token = account();
        JsonNode data = ok(get("/ledger/data", token));
        long income = findCategory(data.path("householdCategories"), "income", "工资");
        long expense = findCategory(data.path("householdCategories"), "expense", "餐饮");
        JsonNode accounts = ok(post("/ledger/money/project/create", Map.of("type", "bank", "name", "已有存款",
                "initialBalanceCents", 2000000), token));
        long cash = accounts.path("projects").get(0).path("id").asLong();
        long bank = accounts.path("projects").get(1).path("id").asLong();
        JsonNode salary = ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", income,
                "amountCents", 2000000, "date", "2026-10-01"), token));
        ok(post("/ledger/household/save", Map.of("type", "expense", "categoryId", expense,
                "amountCents", 2000000, "date", "2026-10-01"), token));
        var rejected = post("/ledger/household/delete", Map.of("id", salary.path("id").asLong()), token);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode problem = rejected.getBody().path("data");
        assertThat(problem.path("reason").asText()).isEqualTo("INSUFFICIENT_BALANCE");
        assertThat(problem.path("projectId").asLong()).isEqualTo(cash);
        assertThat(problem.path("shortfallCents").asLong()).isEqualTo(2000000);
        assertThat(ok(get("/ledger/data", token)).path("householdRecords")).hasSize(2);
        assertThat(ok(get("/ledger/money", token)).path("cashCents").asLong()).isZero();
        ok(post("/ledger/money/transfer", Map.of("fromProjectId", bank, "toProjectId", cash, "amountCents", 2000000), token));
        ok(post("/ledger/household/delete", Map.of("id", salary.path("id").asLong()), token));
        assertThat(ok(get("/ledger/data", token)).path("householdRecords")).hasSize(1);
        assertThat(ok(get("/ledger/money", token)).path("heldCents").asLong()).isZero();
    }

    @Test
    void incomeExpenseConversionUpdatesSameRecordAndRollsBackOnAccountShortfall() {
        String token = account();
        JsonNode data = ok(get("/ledger/data", token));
        long income = findCategory(data.path("householdCategories"), "income", "工资");
        long expense = findCategory(data.path("householdCategories"), "expense", "餐饮");
        JsonNode accounts = ok(post("/ledger/money/project/create", Map.of("type", "bank", "name", "已有存款",
                "initialBalanceCents", 2000000), token));
        long cash = accounts.path("projects").get(0).path("id").asLong();
        long bank = accounts.path("projects").get(1).path("id").asLong();
        JsonNode salary = ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", income,
                "amountCents", 2000000, "date", "2026-10-01"), token));
        long id = salary.path("id").asLong();
        var rejected = post("/ledger/household/update", Map.of("id", id, "type", "expense", "categoryId", expense,
                "projectId", bank, "amountCents", 3000000, "date", "2026-10-01", "note", "误记修正"), token);
        assertThat(rejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(rejected.getBody().path("data").path("projectId").asLong()).isEqualTo(bank);
        assertThat(rejected.getBody().path("data").path("shortfallCents").asLong()).isEqualTo(1000000);
        assertThat(ok(get("/ledger/data", token)).path("householdRecords").get(0)).isEqualTo(salary);
        JsonNode changed = ok(post("/ledger/household/update", Map.of("id", id, "type", "expense", "categoryId", expense,
                "projectId", bank, "amountCents", 500000, "date", "2026-10-01", "note", "误记修正"), token));
        assertThat(changed.path("id").asLong()).isEqualTo(id);
        assertThat(changed.path("type").asText()).isEqualTo("expense");
        JsonNode money = ok(get("/ledger/money", token));
        assertThat(money.path("cashCents").asLong()).isZero();
        assertThat(money.path("heldCents").asLong()).isEqualTo(1500000);
        JsonNode restored = ok(post("/ledger/household/update", Map.of("id", id, "type", "income", "categoryId", income,
                "projectId", cash, "amountCents", 1000000, "date", "2026-10-01", "note", "恢复收入"), token));
        assertThat(restored.path("id").asLong()).isEqualTo(id);
        assertThat(restored.path("projectId").asLong()).isEqualTo(cash);
        assertThat(ok(get("/ledger/data", token)).path("householdRecords")).hasSize(1);
        assertThat(ok(get("/ledger/money", token)).path("heldCents").asLong()).isEqualTo(3000000);
    }

    @Test
    void repeatedSalaryEntriesAppendAndFailedBatchKeepsBothOriginals() {
        String token = account();
        JsonNode data = ok(get("/ledger/data", token));
        long income = findCategory(data.path("householdCategories"), "income", "工资");
        long expense = findCategory(data.path("householdCategories"), "expense", "餐饮");
        JsonNode mine = ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", income,
                "amountCents", 1119000, "date", "2026-10-01", "note", "我的工资"), token));
        JsonNode split = ok(post("/ledger/money/transfer", Map.of("type", "bank", "name", "农业银行", "amountCents", 10000), token));
        long cash = split.path("projects").get(0).path("id").asLong();
        JsonNode month = ok(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", List.of(
                Map.of("categoryId", income, "type", "income", "amountCents", 800000, "projectId", cash, "note", "对象的工资"))), token));
        assertThat(month.path("items")).hasSize(2);
        assertThat(month.path("items").findValues("id")).doesNotHaveDuplicates();
        assertThat(month.path("items").findValuesAsText("note")).containsExactlyInAnyOrder("我的工资", "对象的工资");
        JsonNode money = ok(get("/ledger/money", token));
        assertThat(money.path("heldCents").asLong()).isEqualTo(1919000);
        assertThat(money.path("cashCents").asLong()).isEqualTo(1909000);
        assertThat(money.path("projects").get(1).path("balanceCents").asLong()).isEqualTo(10000);
        assertThat(money.path("savingsCents").asLong()).isEqualTo(10000);
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
        assertThat(ok(get("/ledger/money", token)).path("cashCents").asLong()).isEqualTo(1910000);
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
        ok(deleteUsed);
        JsonNode afterDelete = ok(get("/ledger/data", token));
        assertThat(findCategoryNode(afterDelete.path("householdCategories"), expenseCategory).path("deleted").asBoolean()).isTrue();
        assertThat(afterDelete.path("householdRecords")).isEqualTo(after.path("householdRecords"));

        String other = account();
        ResponseEntity<JsonNode> crossFamily = post("/ledger/household/update", Map.of(
                "id", firstId, "type", "expense", "categoryId", expenseCategory,
                "amountCents", 1, "date", "2026-10-01", "note", ""), other);
        assertThat(crossFamily.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void softDeletingCategoryPreservesHistoryAndAllowsSameNameReplacement() {
        String token = account();
        JsonNode data = ok(get("/ledger/data", token));
        long income = findCategory(data.path("householdCategories"), "income", "工资");
        long expense = findCategory(data.path("householdCategories"), "expense", "餐饮");
        ok(post("/ledger/household/save", Map.of("type", "income", "categoryId", income,
                "amountCents", 100000, "date", "2026-10-01"), token));
        JsonNode record = ok(post("/ledger/household/save", Map.of("type", "expense", "categoryId", expense,
                "amountCents", 1000, "date", "2026-10-01"), token));

        ok(post("/ledger/category/delete", Map.of("id", expense), token));

        JsonNode after = ok(get("/ledger/data", token));
        JsonNode deleted = findCategoryNode(after.path("householdCategories"), expense);
        assertThat(deleted.path("deleted").asBoolean()).isTrue();
        assertThat(after.path("householdRecords")).contains(record);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM relations.ledger_category WHERE id=?",
                Boolean.class, expense)).isTrue();

        JsonNode replacement = ok(post("/ledger/category/create", Map.of("type", "expense", "name", "餐饮", "icon", "fork"), token));
        assertThat(replacement.path("id").asLong()).isNotEqualTo(expense);
        assertThat(post("/ledger/household/save", Map.of("type", "expense", "categoryId", expense,
                "amountCents", 1000, "date", "2026-10-02"), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(post("/ledger/household/month/save", Map.of("month", "2026-10", "items", List.of(
                Map.of("type", "expense", "categoryId", expense, "amountCents", 1000))), token)
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode edited = ok(post("/ledger/household/update", Map.of("id", record.path("id").asLong(),
                "type", "expense", "categoryId", expense, "amountCents", 1200, "date", "2026-10-01", "note", "保留原分类"), token));
        assertThat(edited.path("categoryId").asLong()).isEqualTo(expense);
        assertThat(edited.path("amountCents").asLong()).isEqualTo(1200);
        JsonNode newRecord = ok(post("/ledger/household/save", Map.of("type", "expense", "categoryId", replacement.path("id").asLong(),
                "amountCents", 1000, "date", "2026-10-02"), token));
        assertThat(post("/ledger/household/update", Map.of("id", newRecord.path("id").asLong(), "type", "expense", "categoryId", expense,
                "amountCents", 1000, "date", "2026-10-02"), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode moved = ok(post("/ledger/category/move", Map.of("id", replacement.path("id").asLong()), token));
        assertThat(findCategoryNode(moved, expense).path("deleted").asBoolean()).isTrue();
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

    private JsonNode findCategoryNode(JsonNode categories, long id) {
        for (JsonNode category : categories) {
            if (id == category.path("id").asLong()) return category;
        }
        throw new AssertionError("category not found: " + id);
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
