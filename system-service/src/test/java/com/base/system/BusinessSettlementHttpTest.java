package com.base.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(classes=SystemApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"spring.datasource.url=jdbc:h2:mem:businessSettlement;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.email-code.test-mode=true"})
@ActiveProfiles("test")
class BusinessSettlementHttpTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @Test
    void customerBillsCombinePendingRecordsAndKeepSettledOriginalsInHistory() {
        String token=login(),other=login();long customer=customer(token),sale=sale(token,customer),purchase=purchase(token,customer),saleItem=saleItem(token,sale);
        long pending=ok(post("/business/grain-weighing/create",Map.of("customerId",customer,"grain","水稻","grossWeightKg","1500","tareWeightKg","500","requestId",UUID.randomUUID().toString()),token)).path("id").asLong();
        var first=ok(get("/business/customer/bills?customerId="+customer+"&pageSize=1",token));
        assertThat(first.path("total").asLong()).isEqualTo(3);
        assertThat(first.path("list").size()).isEqualTo(1);
        assertThat(first.path("fertilizerCents").asLong()).isEqualTo(120000);
        assertThat(first.path("grainWeight").decimalValue()).isEqualByComparingTo("3000");
        var records=ok(get("/business/customer/bills?customerId="+customer,token)).path("list");
        assertThat(records.findValuesAsText("kind")).containsExactlyInAnyOrder("sale","purchase","weighing");
        var weighing=java.util.stream.StreamSupport.stream(records.spliterator(),false).filter(r->r.path("kind").asText().equals("weighing")).findFirst().orElseThrow();
        assertThat(weighing.path("id").asLong()).isEqualTo(pending);
        assertThat(weighing.path("remainingCents").isNull()).isTrue();
        assertThat(ok(get("/business/customer/bills?customerId="+customer+"&settled=true",token)).path("total").asLong()).isZero();
        ok(post("/business/settlement/create",settlement(customer,"offset",List.of(saleItem),List.of(purchase),120000,null),token));
        var active=ok(get("/business/customer/bills?customerId="+customer,token));
        assertThat(active.path("total").asLong()).isEqualTo(2);
        assertThat(active.path("fertilizerCents").asLong()).isZero();
        var history=ok(get("/business/customer/bills?customerId="+customer+"&settled=true",token));
        assertThat(history.path("total").asLong()).isEqualTo(1);
        assertThat(history.path("list").get(0).path("id").asLong()).isEqualTo(saleItem);
        assertThat(history.path("list").get(0).path("totalCents").asLong()).isEqualTo(120000);
        assertThat(ok(get("/business/settlement/list?customerId="+customer+"&tradeId="+sale,token)).path("total").asLong()).isEqualTo(1);
        assertThat(ok(get("/business/settlement/list?customerId="+customer+"&tradeId="+pending+"000",token)).path("total").asLong()).isZero();
        assertThat(get("/business/customer/bills?customerId="+customer,other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/business/customer/bills?customerId="+customer+"&pageNum=0",token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void billRowsSplitFertilizerItemsAndSettleOnlyTheSelectedItem() {
        String token=login();long customer=customer(token);
        long compound=ok(post("/business/product/create",Map.of("name","复合肥","bagWeightKg","50","requestId",UUID.randomUUID().toString()),token)).path("id").asLong();
        long urea=ok(post("/business/product/create",Map.of("name","尿素","bagWeightKg","50","requestId",UUID.randomUUID().toString()),token)).path("id").asLong();
        long sale=ok(post("/business/fertilizer-sale/create",Map.of("customerId",customer,"bizDate","2026-11-02","items",List.of(
                Map.of("productId",compound,"quantity","3","unitPrice","120"),
                Map.of("productId",urea,"quantity","5","unitPrice","120")),"requestId",UUID.randomUUID().toString()),token)).path("id").asLong();
        long purchase=purchase(token,customer);
        var before=ok(get("/business/customer/bills?customerId="+customer,token));
        assertThat(before.path("total").asLong()).isEqualTo(3);
        var rows=before.path("list");
        var fertilizers=java.util.stream.StreamSupport.stream(rows.spliterator(),false).filter(r->r.path("kind").asText().equals("sale")).toList();
        assertThat(fertilizers).hasSize(2);
        var compoundRow=fertilizers.stream().filter(r->r.path("title").asText().equals("复合肥")).findFirst().orElseThrow();
        var ureaRow=fertilizers.stream().filter(r->r.path("title").asText().equals("尿素")).findFirst().orElseThrow();
        assertThat(compoundRow.path("quantity").decimalValue()).isEqualByComparingTo("3");
        assertThat(ureaRow.path("quantity").decimalValue()).isEqualByComparingTo("5");
        assertThat(compoundRow.path("remainingCents").asLong()).isEqualTo(36000);
        assertThat(ureaRow.path("remainingCents").asLong()).isEqualTo(60000);
        var grain=java.util.stream.StreamSupport.stream(rows.spliterator(),false).filter(r->r.path("kind").asText().equals("purchase")).findFirst().orElseThrow();
        assertThat(grain.path("unitPrice").decimalValue()).isEqualByComparingTo("1.5");
        assertThat(compoundRow.path("tradeId").asLong()).isEqualTo(sale);
        long account=account(token);
        long receipt=ok(post("/business/settlement/create",settlement(customer,"receive",List.of(compoundRow.path("id").asLong()),List.of(),36000,account),token)).path("id").asLong();
        var after=ok(get("/business/customer/bills?customerId="+customer,token));
        var remaining=java.util.stream.StreamSupport.stream(after.path("list").spliterator(),false).filter(r->r.path("kind").asText().equals("sale")).toList();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).path("id").asLong()).isEqualTo(ureaRow.path("id").asLong());
        assertThat(remaining.get(0).path("remainingCents").asLong()).isEqualTo(60000);
        assertThat(after.path("fertilizerCents").asLong()).isEqualTo(60000);
        assertThat(ok(get("/business/customer/bills?customerId="+customer+"&settled=true",token)).path("total").asLong()).isEqualTo(1);
        assertThat(get("/business/customer/bills?customerId="+customer,login()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void recordingAndPricingCreateDebtWithoutAnyCashSettlement() {
        String token=login();long customer=customer(token);
        long sale=sale(token,customer),purchase=purchase(token,customer);int owner=owner(token);
        assertThat(ok(get("/business/fertilizer-sale/list?customerId="+customer,token)).path("list").get(0).path("id").asLong()).isEqualTo(sale);
        assertThat(ok(get("/business/grain-purchase/detail?id="+purchase,token)).path("paidCents").asLong()).isZero();
        var profile=ok(get("/business/customer/detail?id="+customer,token));
        assertThat(profile.path("fertilizerBalanceCents").asLong()).isEqualTo(120000);
        assertThat(profile.path("grainBalanceCents").asLong()).isEqualTo(-150000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_settlement WHERE owner_id=?",Long.class,owner)).isZero();
    }

    @Test
    void receivingFertilizerCanRecordOnlyTheAmountAndUsesTheServerDate() {
        String token=login();long customer=customer(token),sale=sale(token,customer);int owner=owner(token);
        var body=new HashMap<String,Object>();
        body.put("customerId",customer);body.put("kind","receive");body.put("saleIds",List.of(saleItem(token,sale)));
        body.put("purchaseIds",List.of());body.put("amountCents",120000);body.put("requestId",UUID.randomUUID().toString());
        var saved=ok(post("/business/settlement/create",body,token));
        assertThat(saved.path("accountName").isNull()).isTrue();
        assertThat(saved.path("bizDate").asText()).isEqualTo(jdbc.queryForObject("SELECT CURRENT_DATE",java.sql.Date.class).toString());
        assertThat(jdbc.queryForObject("SELECT account_id FROM business.biz_settlement WHERE owner_id=? AND id=?",Long.class,owner,saved.path("id").asLong())).isNull();
    }

    @Test
    void payingGrainRecordsTheAmountWithoutAnAccountAndRetriesDoNotPayTwice() {
        String token=login();long customer=customer(token),purchase=purchase(token,customer);int owner=owner(token);
        var body=settlement(customer,"pay",List.of(),List.of(purchase),150000,null);
        var saved=ok(post("/business/settlement/create",body,token));
        assertThat(saved.path("accountName").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT account_id FROM business.biz_settlement WHERE owner_id=? AND id=?",Long.class,owner,saved.path("id").asLong())).isNull();
        assertThat(ok(post("/business/settlement/create",body,token)).path("id")).isEqualTo(saved.path("id"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_settlement WHERE owner_id=?",Long.class,owner)).isEqualTo(1);
        assertThat(ok(get("/business/grain-purchase/detail?id="+purchase,token)).path("paidCents").asLong()).isEqualTo(150000);
        assertThat(ok(get("/business/grain-purchase/detail?id="+purchase,token)).path("remainingCents").asLong()).isZero();
        body.put("requestId",UUID.randomUUID().toString());
        assertThat(post("/business/settlement/create",body,token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void offsetAllocatesBothSidesWithoutCashThenOnlyTheRemainderIsPaid() {
        String token=login();long customer=customer(token),sale=sale(token,customer),purchase=purchase(token,customer);
        var body=settlement(customer,"offset",List.of(saleItem(token,sale)),List.of(purchase),120000,null);
        body.remove("bizDate");
        var saved=ok(post("/business/settlement/create",body,token));
        assertThat(saved.path("amountCents").asLong()).isEqualTo(120000);
        assertThat(saved.path("accountName").isNull()).isTrue();
        assertThat(saved.path("allocations").size()).isEqualTo(2);
        assertThat(saved.path("bizDate").asText()).isEqualTo(jdbc.queryForObject("SELECT CURRENT_DATE",java.sql.Date.class).toString());
        long unpriced=ok(post("/business/grain-weighing/create",Map.of("customerId",customer,"grain","小麦","grossWeightKg","700","tareWeightKg","200","requestId",UUID.randomUUID().toString()),token)).path("id").asLong();
        assertThat(post("/business/grain-purchase/reprice",Map.of("customerId",customer,"weighingIds",List.of(unpriced),"purchaseIds",List.of(purchase),"unitPrice","2","requestId",UUID.randomUUID().toString()),token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var s=ok(get("/business/fertilizer-sale/detail?id="+sale,token));
        assertThat(s.path("receivedCents").asLong()).isZero();
        assertThat(s.path("offsetCents").asLong()).isEqualTo(120000);
        assertThat(s.path("remainingCents").asLong()).isZero();
        var p=ok(get("/business/grain-purchase/detail?id="+purchase,token));
        assertThat(p.path("paidCents").asLong()).isZero();
        assertThat(p.path("offsetCents").asLong()).isEqualTo(120000);
        assertThat(p.path("remainingCents").asLong()).isEqualTo(30000);
        assertThat(ok(post("/business/settlement/create",body,token)).path("id")).isEqualTo(saved.path("id"));
        ok(post("/business/settlement/create",settlement(customer,"pay",List.of(),List.of(purchase),30000,account(token)),token));
        assertThat(ok(get("/business/customer/detail?id="+customer,token)).path("settled").asBoolean()).isTrue();
        var history=ok(get("/business/settlement/list?customerId="+customer,token));
        assertThat(history.path("total").asLong()).isEqualTo(2);
        assertThat(history.path("list").get(0).path("kind").asText()).isEqualTo("pay");
        assertThat(history.path("list").get(1).path("kind").asText()).isEqualTo("offset");
    }

    @Test
    void receivingSelectedSalesDoesNotClearOtherSalesAndRejectsStaleAmounts() {
        String token=login();long customer=customer(token),first=sale(token,customer),second=sale(token,customer),account=account(token);
        var firstItem=saleItem(token,first);
        var bodyItem=saleItem(token,first);
        var wrong=settlement(customer,"receive",List.of(firstItem),List.of(),119999,account);
        assertThat(post("/business/settlement/create",wrong,token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var body=settlement(customer,"receive",List.of(bodyItem),List.of(),120000,account);
        var saved=ok(post("/business/settlement/create",body,token));
        assertThat(ok(post("/business/settlement/create",body,token)).path("id")).isEqualTo(saved.path("id"));
        assertThat(ok(get("/business/fertilizer-sale/detail?id="+first,token)).path("remainingCents").asLong()).isZero();
        assertThat(ok(get("/business/fertilizer-sale/detail?id="+second,token)).path("remainingCents").asLong()).isEqualTo(120000);
        body.put("amountCents",1);
        assertThat(post("/business/settlement/create",body,token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void settlementsRejectOtherCustomersOwnersAccountsAndInvalidDirections() {
        String token=login(),other=login();long customer=customer(token),sale=sale(token,customer),purchase=purchase(token,customer),account=account(token);
        for(var body:List.of(
                settlement(customer,"receive",List.of(saleItem(token,sale)),List.of(),120000,account(other)),
                settlement(customer,"receive",List.of(saleItem(token, sale(token,customer(token)))),List.of(),120000,account),
                settlement(customer,"receive",List.of(saleItem(other, sale(other,customer(other)))),List.of(),120000,account),
                settlement(customer,"receive",List.of(purchase),List.of(),150000,account),
                settlement(customer,"offset",List.of(saleItem(token,sale)),List.of(purchase),120000,account),
                settlement(customer,"offset",List.of(saleItem(token,sale),saleItem(token,sale)),List.of(purchase),120000,null),
                settlement(customer,"offset",List.of(saleItem(token,sale)),List.of(),120000,null)))
            assertThat(post("/business/settlement/create",body,token).getStatusCode().is4xxClientError()).isTrue();
        assertThat(get("/business/settlement/list?customerId="+customer,other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/business/fertilizer-sale/list?customerId="+customer,other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_settlement WHERE owner_id=?",Long.class,owner(token))).isZero();
    }

    @Test
    void concurrentOffsetsCannotSettleTheSameDebtTwiceAndFailuresRollBackBothSides() {
        String token=login();long customer=customer(token),sale=sale(token,customer),purchase=purchase(token,customer);int owner=owner(token);
        jdbc.execute("ALTER TABLE business.biz_settlement_item ADD CONSTRAINT offset_test_failure CHECK(owner_id <> "+owner+")");
        try {
            assertThat(post("/business/settlement/create",settlement(customer,"offset",List.of(saleItem(token,sale)),List.of(purchase),120000,null),token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_settlement WHERE owner_id=?",Long.class,owner)).isZero();
        } finally { jdbc.execute("ALTER TABLE business.biz_settlement_item DROP CONSTRAINT offset_test_failure"); }
        var saleItem=saleItem(token,sale);
        var first=CompletableFuture.supplyAsync(()->post("/business/settlement/create",settlement(customer,"offset",List.of(saleItem),List.of(purchase),120000,null),token));
        var second=CompletableFuture.supplyAsync(()->post("/business/settlement/create",settlement(customer,"offset",List.of(saleItem),List.of(purchase),120000,null),token));
        assertThat(List.of(first.join().getStatusCode(),second.join().getStatusCode())).containsExactlyInAnyOrder(HttpStatus.OK,HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_settlement WHERE owner_id=?",Long.class,owner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT SUM(amount_cents) FROM business.biz_settlement_item WHERE owner_id=?",Long.class,owner)).isEqualTo(240000);
    }

    private long sale(String token,long customer) {
        long product=ok(post("/business/product/create",Map.of("name","尿素","bagWeightKg","50","salePrice","120","requestId",UUID.randomUUID().toString()),token)).path("id").asLong();
        return ok(post("/business/fertilizer-sale/create",Map.of("customerId",customer,"bizDate","2026-11-02","items",List.of(Map.of("productId",product,"quantity","10","unitPrice","120")),"requestId",UUID.randomUUID().toString()),token)).path("id").asLong();
    }
    private long saleItem(String token,long trade) {
        int owner=owner(token);
        return jdbc.queryForObject("SELECT id FROM business.biz_trade_item WHERE owner_id=? AND trade_id=? AND category='fertilizer' ORDER BY id LIMIT 1",Long.class,owner,trade);
    }
    private long purchase(String token,long customer) {
        long weighing=ok(post("/business/grain-weighing/create",Map.of("customerId",customer,"grain","小麦","grossWeightKg","1000","tareWeightKg","500","requestId",UUID.randomUUID().toString()),token)).path("id").asLong();
        return ok(post("/business/grain-purchase/create",Map.of("customerId",customer,"weighingIds",List.of(weighing),"unitPrice","1.5","bizDate","2026-11-02","requestId",UUID.randomUUID().toString()),token)).path("id").asLong();
    }
    private Map<String,Object> settlement(long customer,String kind,List<Long> sales,List<Long> purchases,long amount,Long account) {
        var body=new HashMap<String,Object>();body.put("customerId",customer);body.put("kind",kind);body.put("saleIds",sales);body.put("purchaseIds",purchases);body.put("amountCents",amount);body.put("bizDate","2026-11-02");body.put("requestId",UUID.randomUUID().toString());if(account!=null)body.put("accountId",account);return body;
    }
    private long customer(String token) { return ok(post("/business/customer/create",Map.of("name","张三","requestId",UUID.randomUUID().toString()),token)).path("id").asLong(); }
    private long account(String token) { return ok(post("/business/account/create",Map.of("name","生意现金"),token)).path("id").asLong(); }
    private int owner(String token) { return ok(get("/auth/userInfo",token)).path("userInfo").path("id").asInt(); }
    private String login() {
        String email=UUID.randomUUID()+"@example.test";
        ok(post("/auth/email/register",Map.of("email",email,"code","123456","nickname","往来测试","password","business-test-password"),null));
        return ok(post("/auth/login",Map.of("account",email,"password","business-test-password"),null)).path("token").asText();
    }
    private JsonNode ok(ResponseEntity<JsonNode> response) { assertThat(response.getStatusCode()).withFailMessage(response.toString()).isEqualTo(HttpStatus.OK);return response.getBody().path("data"); }
    private ResponseEntity<JsonNode> get(String path,String token) { return http.exchange(path,HttpMethod.GET,new HttpEntity<>(headers(token)),JsonNode.class); }
    private ResponseEntity<JsonNode> post(String path,Object body,String token) { return http.exchange(path,HttpMethod.POST,new HttpEntity<>(body,headers(token)),JsonNode.class); }
    private HttpHeaders headers(String token) { var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);if(token!=null)headers.setBearerAuth(token);return headers; }
}
