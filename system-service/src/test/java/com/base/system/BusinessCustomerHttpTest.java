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
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes = SystemApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:businessCustomers;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "app.email-code.test-mode=true"})
@ActiveProfiles("test")
class BusinessCustomerHttpTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @Test
    void customersBelongToLoginAccountWithoutBookSetup() {
        String owner = account(), other = account();
        assertThat(ok(get("/business/customer/list", owner)).path("total").asInt()).isZero();
        assertThat(post("/business/book/create", Map.of("name", "多余账套", "requestId", "unused"), owner).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/business/book/list", owner).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        String key = UUID.randomUUID().toString();
        var request = Map.of("type", "farmer", "name", "张三", "appellation", "大叔", "requestId", key);
        JsonNode created = ok(post("/business/customer/create", request, owner));
        long customer = created.path("id").asLong();
        assertThat(created.has("bookId")).isFalse();
        assertThat(ok(post("/business/customer/create", request, owner)).path("id").asLong()).isEqualTo(customer);
        assertThat(post("/business/customer/create", Map.of("type", "farmer", "name", "李四", "requestId", key), owner).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        String list = "/business/customer/list";
        assertThat(ok(get(list + "?type=farmer&keyword=大叔", owner)).path("total").asInt()).isEqualTo(1);
        assertThat(ok(get(list + "?type=merchant", owner)).path("total").asInt()).isZero();
        ok(post("/business/customer/create", Map.of("type", "merchant", "name", "化肥厂", "requestId", UUID.randomUUID().toString()), owner));
        assertThat(ok(get(list + "?pageSize=1&pageNum=2", owner)).path("list").size()).isEqualTo(1);
        ok(post("/business/customer/update", Map.of("id", customer, "type", "farmer", "name", "张三改", "appellation", "叔叔"), owner));
        String detail = "/business/customer/detail?id=" + customer;
        assertThat(ok(get(detail, owner)).path("name").asText()).isEqualTo("张三改");
        assertThat(get(detail, other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ok(get(list, other)).path("total").asInt()).isZero();
        assertThat(post("/business/customer/update", Map.of("id", customer, "type", "farmer", "name", "越权"), other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        JsonNode otherCustomer = ok(post("/business/customer/create", Map.of("type", "farmer", "name", "李四", "requestId", key, "bookId", "999", "ownerId", "999"), other));
        assertThat(get("/business/customer/detail?id=" + otherCustomer.path("id").asText(), owner).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ok(get(list + "?bookId=999&ownerId=999", other)).path("total").asInt()).isEqualTo(1);
        assertThat(get(list, null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // 加入同一家庭也不共享生意数据。
        ok(get("/friend/list", owner)); ok(get("/friend/list", other));
        int ownerId = ok(get("/auth/userInfo", owner)).path("userInfo").path("id").asInt();
        int otherId = ok(get("/auth/userInfo", other)).path("userInfo").path("id").asInt();
        String familyId = jdbc.queryForObject("SELECT family_id FROM relations.ledger_member WHERE user_id=?", String.class, ownerId);
        jdbc.update("UPDATE relations.ledger_member SET family_id=? WHERE user_id=?", familyId, otherId);
        assertThat(get(detail, other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get(list + "?pageNum=0", owner).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(get("/business/customer/detail?id=0", owner).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/business/customer/create", Map.of("type", "invalid", "name", " ", "requestId", "bad"), owner).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ok(get("/friend/list", owner)).path("total").asInt()).isZero();
    }

    @Test
    void outstandingTracksPartialPaymentsOffsetsAndSeasons() {
        String token = account();
        long customer = ok(post("/business/customer/create", Map.of("type", "farmer", "name", "张三", "requestId", "zhang"), token)).path("id").asLong();
        int user = ok(get("/auth/userInfo", token)).path("userInfo").path("id").asInt();
        long fertilizer = trade(user, customer, "sale", "fertilizer", 100000, "2026春");
        long grain = trade(user, customer, "purchase", "grain", 120000, "2026夏");
        long receive = settlement(user, customer, "receive", 20000);
        allocate(user, customer, receive, fertilizer, 20000);
        long receive2 = settlement(user, customer, "receive", 30000);
        allocate(user, customer, receive2, fertilizer, 30000);
        assertBalances(token, customer, 50000, -120000, false);
        long offset = settlement(user, customer, "offset", 50000);
        allocate(user, customer, offset, fertilizer, 50000);
        allocate(user, customer, offset, grain, 50000);
        assertBalances(token, customer, 0, -70000, false);
        long pay = settlement(user, customer, "pay", 70000);
        allocate(user, customer, pay, grain, 70000);
        assertBalances(token, customer, 0, 0, true);
        jdbc.update("UPDATE business.biz_settlement SET status='void' WHERE id=?", pay);
        assertBalances(token, customer, 0, -70000, false);
        jdbc.update("UPDATE business.biz_settlement SET status='posted' WHERE id=?", pay);
        // 新季继续记账，旧季原单及分配关系保留；双向相等并不等于结清。
        trade(user, customer, "sale", "fertilizer", 60000, "2026秋");
        trade(user, customer, "purchase", "grain", 60000, "2026秋");
        assertBalances(token, customer, 60000, -60000, false);
        long voidItem = trade(user, customer, "sale", "fertilizer", 90000, "2026秋");
        jdbc.update("UPDATE business.biz_trade SET status='void' WHERE id=(SELECT trade_id FROM business.biz_trade_item WHERE id=?)", voidItem);
        assertBalances(token, customer, 60000, -60000, false);
        long otherCustomer = ok(post("/business/customer/create", Map.of("type", "farmer", "name", "李四", "requestId", "li"), token)).path("id").asLong();
        long otherSettlement = settlement(user, otherCustomer, "receive", 1);
        assertThatThrownBy(() -> allocate(user, otherCustomer, otherSettlement, fertilizer, 1))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void customerCrudValidatesFieldsAndDeleteIsOwnedAndIdempotent() {
        String owner = account(), other = account();
        String key = UUID.randomUUID().toString();
        var request = Map.of("type", "merchant", "name", " 化肥厂 ", "appellation", " 经理 ",
                "phone", " 010-12345678 ", "remark", " 老客户 ", "requestId", key);
        JsonNode created = ok(post("/business/customer/create", request, owner));
        long id = created.path("id").asLong();
        assertThat(created.path("name").asText()).isEqualTo("化肥厂");
        assertThat(created.path("appellation").asText()).isEqualTo("经理");
        assertThat(created.path("phone").asText()).isEqualTo("010-12345678");
        assertThat(created.path("remark").asText()).isEqualTo("老客户");
        assertThat(post("/business/customer/delete", Map.of("id", id), other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/business/customer/delete", Map.of("id", id), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/business/customer/delete", Map.of("id", 0), owner).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/business/customer/delete", Map.of(), owner).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/business/customer/update", Map.of("id", id, "type", "farmer", "name", " "), owner).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/business/customer/update", Map.of("id", id, "type", "farmer", "name", "长".repeat(31)), owner).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/business/customer/update", Map.of("id", id, "type", "farmer", "name", "张三", "remark", "长".repeat(201)), owner).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        JsonNode updated = ok(post("/business/customer/update", Map.of("id", id, "type", "farmer", "name", " 张三 "), owner));
        assertThat(updated.path("name").asText()).isEqualTo("张三");
        assertThat(updated.path("type").asText()).isEqualTo("farmer");
        assertThat(updated.path("appellation").asText()).isEmpty();
        assertThat(updated.path("phone").asText()).isEmpty();
        assertThat(updated.path("remark").asText()).isEmpty();
        assertThat(ok(get("/business/customer/list?type=merchant", owner)).path("total").asInt()).isZero();
        ok(post("/business/customer/delete", Map.of("id", id), owner));
        ok(post("/business/customer/delete", Map.of("id", id), owner));
        assertThat(post("/business/customer/delete", Map.of("id", id), other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ok(get("/business/customer/list", owner)).path("total").asInt()).isZero();
        assertThat(get("/business/customer/detail?id=" + id, owner).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/business/customer/update", Map.of("id", id, "type", "farmer", "name", "恢复"), owner).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(post("/business/customer/create", request, owner).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM business.biz_customer WHERE id=?", Boolean.class, id)).isTrue();
        assertThat(ok(post("/business/customer/create", Map.of("type", "farmer", "name", "张三", "requestId", UUID.randomUUID().toString()), owner)).path("id").asLong()).isNotEqualTo(id);
        assertThat(ok(get("/friend/list", owner)).path("total").asInt()).isZero();
    }

    @Test
    void deleteRequiresBothDirectionsSettledAndPreservesAllHistory() {
        String token = account();
        long customer = ok(post("/business/customer/create", Map.of("type", "farmer", "name", "结算客户", "requestId", "delete-history"), token)).path("id").asLong();
        int user = ok(get("/auth/userInfo", token)).path("userInfo").path("id").asInt();
        long sale = trade(user, customer, "sale", "fertilizer", 60000, "2026春");
        assertThat(post("/business/customer/delete", Map.of("id", customer), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        long purchase = trade(user, customer, "purchase", "grain", 60000, "2026秋");
        assertBalances(token, customer, 60000, -60000, false);
        assertThat(post("/business/customer/delete", Map.of("id", customer), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        long offset = settlement(user, customer, "offset", 60000);
        allocate(user, customer, offset, sale, 60000);
        // 仅结清应收，应付仍在时也不能删。
        assertThat(post("/business/customer/delete", Map.of("id", customer), token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        allocate(user, customer, offset, purchase, 60000);
        assertBalances(token, customer, 0, 0, true);
        var trades = jdbc.queryForList("SELECT * FROM business.biz_trade WHERE customer_id=? ORDER BY id", customer);
        var items = jdbc.queryForList("SELECT * FROM business.biz_trade_item WHERE customer_id=? ORDER BY id", customer);
        var settlements = jdbc.queryForList("SELECT * FROM business.biz_settlement WHERE customer_id=? ORDER BY id", customer);
        var allocations = jdbc.queryForList("SELECT * FROM business.biz_settlement_item WHERE customer_id=? ORDER BY id", customer);
        ok(post("/business/customer/delete", Map.of("id", customer), token));
        assertThat(ok(get("/business/customer/list", token)).path("total").asInt()).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM business.biz_trade WHERE customer_id=? ORDER BY id", customer)).isEqualTo(trades);
        assertThat(jdbc.queryForList("SELECT * FROM business.biz_trade_item WHERE customer_id=? ORDER BY id", customer)).isEqualTo(items);
        assertThat(jdbc.queryForList("SELECT * FROM business.biz_settlement WHERE customer_id=? ORDER BY id", customer)).isEqualTo(settlements);
        assertThat(jdbc.queryForList("SELECT * FROM business.biz_settlement_item WHERE customer_id=? ORDER BY id", customer)).isEqualTo(allocations);
    }

    private void assertBalances(String token, long customer, long fertilizer, long grain, boolean settled) {
        JsonNode detail = ok(get("/business/customer/detail?id=" + customer, token));
        assertThat(detail.path("fertilizerBalanceCents").asLong()).isEqualTo(fertilizer);
        assertThat(detail.path("grainBalanceCents").asLong()).isEqualTo(grain);
        assertThat(detail.path("balanceCents").asLong()).isEqualTo(fertilizer + grain);
        assertThat(detail.path("settled").asBoolean()).isEqualTo(settled);
        JsonNode row = ok(get("/business/customer/list", token)).path("list").get(0);
        assertThat(row).isEqualTo(detail);
    }
    private long trade(int user, long customer, String direction, String category, long amount, String season) {
        jdbc.update("INSERT INTO business.biz_trade(owner_id,customer_id,direction,biz_date,season,created_by) VALUES (?,?,?,DATE '2026-06-01',?,?)", user, customer, direction, season, user);
        long id = jdbc.queryForObject("SELECT MAX(id) FROM business.biz_trade WHERE owner_id=?", Long.class, user);
        jdbc.update("INSERT INTO business.biz_trade_item(owner_id,customer_id,trade_id,category,product_name,quantity,unit,unit_price,amount_cents) VALUES (?,?,?,?,?,1,'袋',?,?)", user, customer, id, category, category, amount / 100.0, amount);
        return jdbc.queryForObject("SELECT MAX(id) FROM business.biz_trade_item WHERE owner_id=?", Long.class, user);
    }
    private long settlement(int user, long customer, String kind, long amount) {
        jdbc.update("INSERT INTO business.biz_settlement(owner_id,customer_id,kind,amount_cents,biz_date,created_by) VALUES (?,?,?,?,DATE '2026-06-05',?)", user, customer, kind, amount, user);
        return jdbc.queryForObject("SELECT MAX(id) FROM business.biz_settlement WHERE owner_id=?", Long.class, user);
    }
    private void allocate(int user, long customer, long settlement, long item, long amount) {
        jdbc.update("INSERT INTO business.biz_settlement_item(owner_id,customer_id,settlement_id,trade_item_id,amount_cents) VALUES (?,?,?,?,?)", user, customer, settlement, item, amount);
    }
    private String account() {
        String email = UUID.randomUUID() + "@example.test";
        ok(post("/auth/email/register", Map.of("email", email, "code", "123456", "nickname", "生意测试", "password", "business-test-password"), null));
        return ok(post("/auth/login", Map.of("account", email, "password", "business-test-password"), null)).path("token").asText();
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
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return headers;
    }
}
