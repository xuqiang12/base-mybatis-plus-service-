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
        properties={"spring.datasource.url=jdbc:h2:mem:businessGrain;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","app.email-code.test-mode=true"})
@ActiveProfiles("test")
class BusinessGrainHttpTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @Test
    void weighingNeedsNoPriceCreatesNoDebtAndKeepsTheOriginalReadings() {
        String token=login();long customer=customer(token);int owner=owner(token);
        var body=weighing(customer,"小麦","1500","1000");
        var saved=ok(post("/business/grain-weighing/create",body,token));
        assertThat(saved.path("grossWeightKg").decimalValue()).isEqualByComparingTo("1500");
        assertThat(saved.path("tareWeightKg").decimalValue()).isEqualByComparingTo("1000");
        assertThat(saved.path("netWeightKg").decimalValue()).isEqualByComparingTo("500");
        assertThat(saved.path("weight").decimalValue()).isEqualByComparingTo("1000");
        assertThat(saved.path("purchaseId").isNull()).isTrue();
        assertThat(ok(post("/business/grain-weighing/create",body,token)).path("id")).isEqualTo(saved.path("id"));
        var profile=ok(get("/business/customer/detail?id="+customer,token));
        assertThat(profile.path("grainBalanceCents").asLong()).isZero();
        assertThat(profile.path("payableCents").asLong()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_trade WHERE owner_id=?",Long.class,owner)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_settlement WHERE owner_id=?",Long.class,owner)).isZero();
        var created=jdbc.queryForMap("SELECT created_at,biz_date,remark FROM business.biz_grain_weighing WHERE id=?",saved.path("id").asLong());
        assertThat(created.get("created_at")).isNotNull();
        assertThat(created.get("biz_date").toString()).isEqualTo(jdbc.queryForObject("SELECT CURRENT_DATE",java.sql.Date.class).toString());
        assertThat(created.get("remark")).isEqualTo("");
        body.put("grossWeightKg","1600");
        assertThat(post("/business/grain-weighing/create",body,token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void pendingWeightsHaveCustomerScopedPaginationAndTotals() {
        String token=login(),other=login();long customer=customer(token);
        weigh(token,customer,"小麦","1500","1000");weigh(token,customer,"小麦","1100","1000");weigh(token,customer,"水稻","1200","1000");
        weigh(token,customer(token),"小麦","9000","0");
        var page=ok(get("/business/grain-weighing/list?customerId="+customer+"&pageNum=2&pageSize=2",token));
        assertThat(page.path("total").asLong()).isEqualTo(3);
        assertThat(page.path("list").size()).isEqualTo(1);
        assertThat(page.path("pageNum").asInt()).isEqualTo(2);
        assertThat(page.path("pendingNetWeightKg").decimalValue()).isEqualByComparingTo("800");
        assertThat(get("/business/grain-weighing/list?customerId="+customer,other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void tareHistoryOnlyReturnsTheLatestSavedReadingForTheCustomer() {
        String token=login(),other=login();long customer=customer(token),otherCustomer=customer(token);
        weigh(token,customer,"小麦","1500","1200");
        weigh(token,customer,"小麦","1600","1200");
        var latest=weighing(customer,"水稻","1700","1180");
        long priced=ok(post("/business/grain-weighing/create",latest,token)).path("id").asLong();
        ok(post("/business/grain-purchase/create",pricing(customer,List.of(priced),"1.2"),token));
        weigh(token,otherCustomer,"小麦","1800","1100");
        long foreignCustomer=customer(other);
        weigh(other,foreignCustomer,"小麦","1900","900");
        var history=ok(get("/business/grain-weighing/tare-history?customerId="+customer,token));
        assertThat(history.size()).isEqualTo(1);
        assertThat(history.get(0).path("customerId").asLong()).isEqualTo(customer);
        assertThat(history.get(0).path("customerName").asText()).isEqualTo("张三");
        assertThat(history.get(0).path("tareWeightKg").decimalValue()).isEqualByComparingTo("1180");
        assertThat(history.get(0).path("lastUsedDate").asText()).isEqualTo(jdbc.queryForObject("SELECT CURRENT_DATE",java.sql.Date.class).toString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_grain_weighing WHERE owner_id=? AND customer_id=?",Long.class,owner(token),customer)).isEqualTo(3);
        assertThat(get("/business/grain-weighing/tare-history?customerId="+otherCustomer,token).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/business/grain-weighing/tare-history?customerId="+customer,other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void batchPricingUsesSelectedNetWeightsAndKeepsUnselectedCarsPending() {
        String token=login();long customer=customer(token);
        long first=weigh(token,customer,"小麦","1500","1000"),second=weigh(token,customer,"小麦","1800","1000");
        weigh(token,customer,"水稻","2000","1000");
        var body=pricing(customer,List.of(first,second),"1.20");body.remove("bizDate");body.put("weight","1");body.put("totalCents",1);
        var saved=ok(post("/business/grain-purchase/create",body,token));
        assertThat(saved.path("totalCents").asLong()).isEqualTo(312000);
        assertThat(saved.path("bizDate").asText()).isEqualTo(jdbc.queryForObject("SELECT CURRENT_DATE",java.sql.Date.class).toString());
        assertThat(saved.path("paidCents").asLong()).isZero();
        assertThat(saved.path("remainingCents").asLong()).isEqualTo(312000);
        assertThat(saved.path("weight").decimalValue()).isEqualByComparingTo("2600");
        assertThat(saved.path("weighings").size()).isEqualTo(2);
        assertThat(saved.path("weighings").get(0).path("grossWeightKg").decimalValue()).isEqualByComparingTo("1500");
        var stored=ok(get("/business/grain-purchase/detail?id="+saved.path("id").asText(),token));
        assertThat(stored.path("weighings").get(0).path("bizDate").asText()).isEqualTo(jdbc.queryForObject("SELECT CURRENT_DATE",java.sql.Date.class).toString());
        assertThat(stored.path("weighings").get(0).path("purchaseId")).isEqualTo(saved.path("id"));
        var pending=ok(get("/business/grain-weighing/list?customerId="+customer,token));
        assertThat(pending.path("total").asLong()).isEqualTo(1);
        assertThat(pending.path("pendingNetWeightKg").decimalValue()).isEqualByComparingTo("1000");
        var profile=ok(get("/business/customer/detail?id="+customer,token));
        assertThat(profile.path("grainBalanceCents").asLong()).isEqualTo(-312000);
        assertThat(profile.path("fertilizerBalanceCents").asLong()).isZero();
        assertThat(ok(get("/business/grain-purchase/list?customerId="+customer,token)).path("list").size()).isEqualTo(1);
    }

    @Test
    void repricingCombinesPendingPricedAndUnpricedGrainAtTheNewUnitPrice() {
        String token=login();long customer=customer(token);
        long first=weigh(token,customer,"小麦","1500","1000");
        long old=ok(post("/business/grain-purchase/create",pricing(customer,List.of(first),"1.2"),token)).path("id").asLong();
        long second=weigh(token,customer,"小麦","1800","1000");
        var body=new HashMap<String,Object>();
        body.put("customerId",customer);body.put("purchaseIds",List.of(old));body.put("weighingIds",List.of(second));
        body.put("unitPrice","1.5");body.put("requestId",UUID.randomUUID().toString());
        var saved=ok(post("/business/grain-purchase/reprice",body,token));
        assertThat(saved.path("id").asLong()).isNotEqualTo(old);
        assertThat(saved.path("weight").decimalValue()).isEqualByComparingTo("2600");
        assertThat(saved.path("unitPrice").decimalValue()).isEqualByComparingTo("1.5");
        assertThat(saved.path("totalCents").asLong()).isEqualTo(390000);
        assertThat(ok(get("/business/customer/bills?customerId="+customer,token)).path("total").asLong()).isEqualTo(1);
        var original=ok(get("/business/grain-purchase/detail?id="+old,token));
        assertThat(original.path("unitPrice").decimalValue()).isEqualByComparingTo("1.2");
        assertThat(original.path("totalCents").asLong()).isEqualTo(120000);
        assertThat(original.path("repricedIntoId")).isEqualTo(saved.path("id"));
        assertThat(saved.path("weighings").size()).isEqualTo(2);
        assertThat(saved.path("sourcePricings").get(0).path("id").asLong()).isEqualTo(old);
        assertThat(ok(post("/business/grain-purchase/reprice",body,token)).path("id")).isEqualTo(saved.path("id"));
        body.put("unitPrice","1.6");
        assertThat(post("/business/grain-purchase/reprice",body,token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        long third=weigh(token,customer,"小麦","1100","1000");
        var next=Map.of("customerId",customer,"purchaseIds",List.of(saved.path("id").asLong()),"weighingIds",List.of(third),"unitPrice","2","requestId",UUID.randomUUID().toString());
        var repricedAgain=ok(post("/business/grain-purchase/reprice",next,token));
        assertThat(repricedAgain.path("weight").decimalValue()).isEqualByComparingTo("2800");
        assertThat(repricedAgain.path("totalCents").asLong()).isEqualTo(560000);
        assertThat(repricedAgain.path("weighings").size()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT trade_id FROM business.biz_grain_weighing WHERE id=?",Long.class,first)).isEqualTo(old);
    }

    @Test
    void repricingRejectsPaidGrainMixedVarietiesDuplicatesAndOtherOwners() {
        String token=login();long customer=customer(token);
        long first=weigh(token,customer,"小麦","1500","1000");
        long purchase=ok(post("/business/grain-purchase/create",pricing(customer,List.of(first),"1.2"),token)).path("id").asLong();
        long wheat=weigh(token,customer,"小麦","1800","1000"),rice=weigh(token,customer,"水稻","1800","1000");
        for(var ids:List.of(List.of(rice),List.of(wheat,wheat),List.of(first))) {
            var request=Map.of("customerId",customer,"purchaseIds",List.of(purchase),"weighingIds",ids,"unitPrice","1.5","requestId",UUID.randomUUID().toString());
            assertThat(post("/business/grain-purchase/reprice",request,token).getStatusCode().is4xxClientError()).isTrue();
        }
        var request=Map.of("customerId",customer,"purchaseIds",List.of(purchase),"weighingIds",List.of(wheat),"unitPrice","1.5","requestId",UUID.randomUUID().toString());
        assertThat(post("/business/grain-purchase/reprice",request,login()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // A historical partial allocation must block repricing just like a full payment.
        long paid=ok(post("/business/settlement/create",Map.of("customerId",customer,"kind","pay","saleIds",List.of(),"purchaseIds",List.of(purchase),"amountCents",120000,"accountId",account(token),"bizDate","2026-11-03","requestId",UUID.randomUUID().toString()),token)).path("id").asLong();
        jdbc.update("UPDATE business.biz_settlement_item SET amount_cents=10000 WHERE settlement_id=?",paid);
        jdbc.update("UPDATE business.biz_settlement SET amount_cents=10000 WHERE id=?",paid);
        assertThat(post("/business/grain-purchase/reprice",request,token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT status FROM business.biz_trade WHERE id=?",String.class,purchase)).isEqualTo("posted");
        assertThat(jdbc.queryForObject("SELECT trade_id FROM business.biz_grain_weighing WHERE id=?",Long.class,wheat)).isNull();
    }

    @Test
    void repricingRollsBackFailuresAndSerializesCompetingRequests() {
        String token=login();long customer=customer(token);int owner=owner(token);
        long first=weigh(token,customer,"小麦","1500","1000");
        long purchase=ok(post("/business/grain-purchase/create",pricing(customer,List.of(first),"1.2"),token)).path("id").asLong();
        long second=weigh(token,customer,"小麦","1800","1000");
        var body=new HashMap<String,Object>();body.put("customerId",customer);body.put("purchaseIds",List.of(purchase));body.put("weighingIds",List.of(second));body.put("unitPrice","1.5");body.put("requestId",UUID.randomUUID().toString());
        jdbc.execute("ALTER TABLE business.biz_trade ADD CONSTRAINT reprice_failure CHECK (owner_id <> "+owner+" OR repriced_into_id IS NULL)");
        try {
            assertThat(post("/business/grain-purchase/reprice",body,token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_trade WHERE owner_id=?",Long.class,owner)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM business.biz_trade WHERE id=?",String.class,purchase)).isEqualTo("posted");
            assertThat(jdbc.queryForObject("SELECT trade_id FROM business.biz_grain_weighing WHERE id=?",Long.class,second)).isNull();
        } finally { jdbc.execute("ALTER TABLE business.biz_trade DROP CONSTRAINT reprice_failure"); }
        var other=new HashMap<>(body);other.put("requestId",UUID.randomUUID().toString());
        var a=CompletableFuture.supplyAsync(()->post("/business/grain-purchase/reprice",body,token));
        var b=CompletableFuture.supplyAsync(()->post("/business/grain-purchase/reprice",other,token));
        assertThat(List.of(a.join().getStatusCode(),b.join().getStatusCode())).containsExactlyInAnyOrder(HttpStatus.OK,HttpStatus.CONFLICT);
    }

    @Test
    void mixedCustomersGrainsDuplicatesAndOtherAccountsCannotBePriced() {
        String token=login(),other=login();long customer=customer(token),otherCustomer=customer(token);
        long wheat=weigh(token,customer,"小麦","1500","1000"),rice=weigh(token,customer,"水稻","1500","1000");
        long different=weigh(token,otherCustomer,"小麦","1500","1000"),foreign=weigh(other,customer(other),"小麦","1500","1000");
        for(var ids:List.of(List.of(wheat,rice),List.of(wheat,different),List.of(wheat,foreign),List.of(wheat,wheat),List.<Long>of()))
            assertThat(post("/business/grain-purchase/create",pricing(customer,ids,"1.2"),token).getStatusCode().is4xxClientError()).isTrue();
        assertThat(post("/business/grain-purchase/create",pricing(customer(other),List.of(wheat),"1.2"),token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_trade WHERE owner_id=?",Long.class,owner(token))).isZero();
        assertThat(ok(get("/business/grain-weighing/list?customerId="+customer,token)).path("total").asLong()).isEqualTo(2);
        var saved=ok(post("/business/grain-purchase/create",pricing(customer,List.of(wheat),"1.2"),token));
        assertThat(post("/business/grain-purchase/create",pricing(customer,List.of(wheat),"1.2"),token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(get("/business/grain-purchase/detail?id="+saved.path("id").asText(),other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/business/grain-purchase/list?customerId="+customer,other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void concurrentPricingRetriesCreateOnlyOnePurchaseAndNoPayment() {
        String token=login();long customer=customer(token),id=weigh(token,customer,"小麦","1500","1000");
        var request=pricing(customer,List.of(id),"1.2");
        var first=CompletableFuture.supplyAsync(()->ok(post("/business/grain-purchase/create",request,token)));
        var second=CompletableFuture.supplyAsync(()->ok(post("/business/grain-purchase/create",request,token)));
        assertThat(first.join().path("id")).isEqualTo(second.join().path("id"));
        for(String table:List.of("biz_trade","biz_trade_item"))
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business."+table+" WHERE owner_id=?",Long.class,owner(token))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_settlement WHERE owner_id=?",Long.class,owner(token))).isZero();
        request.put("unitPrice","1.3");
        assertThat(post("/business/grain-purchase/create",request,token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void differentConcurrentPricingRequestsCannotClaimTheSameCar() {
        String token=login();long customer=customer(token),id=weigh(token,customer,"小麦","1500","1000");
        var first=CompletableFuture.supplyAsync(()->post("/business/grain-purchase/create",pricing(customer,List.of(id),"1.2"),token));
        var second=CompletableFuture.supplyAsync(()->post("/business/grain-purchase/create",pricing(customer,List.of(id),"1.3"),token));
        assertThat(List.of(first.join().getStatusCode(),second.join().getStatusCode())).containsExactlyInAnyOrder(HttpStatus.OK,HttpStatus.CONFLICT);
    }

    @Test
    void aFailedInvoiceRollsBackPricingAndReleasesTheSelectedCars() {
        String token=login();long customer=customer(token),id=weigh(token,customer,"小麦","1500","1000");int owner=owner(token);
        var request=pricing(customer,List.of(id),"1.2");
        jdbc.execute("ALTER TABLE business.biz_trade_item ADD CONSTRAINT grain_test_payment_failure CHECK (owner_id <> "+owner+")");
        try {
            assertThat(post("/business/grain-purchase/create",request,token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_trade WHERE owner_id=?",Long.class,owner)).isZero();
            assertThat(ok(get("/business/grain-weighing/list?customerId="+customer,token)).path("total").asLong()).isEqualTo(1);
        } finally { jdbc.execute("ALTER TABLE business.biz_trade_item DROP CONSTRAINT grain_test_payment_failure"); }
        assertThat(ok(post("/business/grain-purchase/create",request,token)).path("remainingCents").asLong()).isEqualTo(120000);
    }

    @Test
    void aggregateWeightsRoundOnceAndKeepSmallAndLargeReadingsPrecise() {
        String token=login();long customer=customer(token);
        long first=weigh(token,customer,"小麦","0.001","0"),second=weigh(token,customer,"小麦","999999999.999","999999999.998");
        var saved=ok(post("/business/grain-purchase/create",pricing(customer,List.of(first,second),"2.5"),token));
        assertThat(saved.path("netWeightKg").decimalValue()).isEqualByComparingTo("0.002");
        assertThat(saved.path("weight").decimalValue()).isEqualByComparingTo("0.004");
        assertThat(saved.path("totalCents").asLong()).isEqualTo(1);
        assertThat(saved.path("remainingCents").asLong()).isEqualTo(1);
    }

    @Test
    void retiredPaymentEndpointIsNotAvailable() {
        assertThat(post("/business/grain-purchase/pay", Map.of(), login()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void historicalPricedPurchasesRemainPayableWithoutFabricatedWeighings() {
        String token=login();long customer=customer(token);int owner=owner(token);
        jdbc.update("INSERT INTO business.biz_trade(owner_id,customer_id,direction,biz_date,season,created_by) VALUES (?,?,'purchase',DATE '2026-10-01','2026年秋耕账期',?)",owner,customer,owner);
        long id=jdbc.queryForObject("SELECT MAX(id) FROM business.biz_trade WHERE owner_id=?",Long.class,owner);
        jdbc.update("INSERT INTO business.biz_trade_item(owner_id,customer_id,trade_id,category,product_name,quantity,unit,unit_price,amount_cents,gross_weight_kg,tare_weight_kg) VALUES (?,?,?,'grain','小麦',1000,'斤',1.2,120000,1000,500)",owner,customer,id);
        var stored=ok(get("/business/grain-purchase/detail?id="+id,token));
        assertThat(stored.path("weighings").isEmpty()).isTrue();
        assertThat(stored.path("netWeightKg").decimalValue()).isEqualByComparingTo("500");
        assertThat(stored.path("grossWeightKg").decimalValue()).isEqualByComparingTo("1000");
        assertThat(stored.path("tareWeightKg").decimalValue()).isEqualByComparingTo("500");
        assertThat(stored.path("totalCents").asLong()).isEqualTo(120000);
        assertThat(ok(get("/business/grain-purchase/list?customerId="+customer,token)).path("list").size()).isEqualTo(1);
        ok(post("/business/settlement/create",Map.of("customerId",customer,"kind","pay","saleIds",List.of(),"purchaseIds",List.of(id),"amountCents",120000,"accountId",account(token),"bizDate","2026-11-03","requestId",UUID.randomUUID().toString()),token));
        assertThat(ok(get("/business/grain-purchase/detail?id="+id,token)).path("remainingCents").asLong()).isZero();
    }

    @Test
    void invalidWeightsAndDeletedCustomersAreRejectedAndPendingCustomersCannotBeDeleted() {
        String token=login();long customer=customer(token);
        for(var readings:List.of(List.of("0","0"),List.of("1","1"),List.of("1","2"),List.of("1","-1"),List.of("1.0001","0"),List.of("1","0.0001")))
            assertThat(post("/business/grain-weighing/create",weighing(customer,"小麦",readings.get(0),readings.get(1)),token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        var missing=weighing(customer,"小麦","1","0");missing.remove("tareWeightKg");
        assertThat(post("/business/grain-weighing/create",missing,token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/business/grain-weighing/create",weighing(customer,"小麦","1","0"),null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ok(post("/business/customer/delete",Map.of("id",customer),token));
        assertThat(post("/business/grain-weighing/create",weighing(customer,"小麦","1","0"),token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        long pending=customer(token);weigh(token,pending,"小麦","1","0");
        assertThat(post("/business/customer/delete",Map.of("id",pending),token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private Map<String,Object> weighing(long customer,String grain,String gross,String tare) {
        var request=new HashMap<String,Object>();request.put("customerId",customer);request.put("grain",grain);request.put("grossWeightKg",gross);request.put("tareWeightKg",tare);
        request.put("requestId",UUID.randomUUID().toString());return request;
    }
    private long weigh(String token,long customer,String grain,String gross,String tare) { return ok(post("/business/grain-weighing/create",weighing(customer,grain,gross,tare),token)).path("id").asLong(); }
    private Map<String,Object> pricing(long customer,List<Long> ids,String price) {
        var request=new HashMap<String,Object>();request.put("customerId",customer);request.put("weighingIds",ids);request.put("unitPrice",price);
        request.put("bizDate","2026-11-02");request.put("remark","");request.put("requestId",UUID.randomUUID().toString());
        return request;
    }
    private long customer(String token) { return ok(post("/business/customer/create",Map.of("name","张三","requestId",UUID.randomUUID().toString()),token)).path("id").asLong(); }
    private long account(String token) { return ok(post("/business/account/create",Map.of("name","生意现金"),token)).path("id").asLong(); }
    private int owner(String token) { return ok(get("/auth/userInfo",token)).path("userInfo").path("id").asInt(); }
    private String login() {
        String email=UUID.randomUUID()+"@example.test";
        ok(post("/auth/email/register",Map.of("email",email,"code","123456","nickname","收粮测试","password","business-test-password"),null));
        return ok(post("/auth/login",Map.of("account",email,"password","business-test-password"),null)).path("token").asText();
    }
    private JsonNode ok(ResponseEntity<JsonNode> response) { assertThat(response.getStatusCode()).withFailMessage(response.toString()).isEqualTo(HttpStatus.OK);return response.getBody().path("data"); }
    private ResponseEntity<JsonNode> get(String path,String token) { return http.exchange(path,HttpMethod.GET,new HttpEntity<>(headers(token)),JsonNode.class); }
    private ResponseEntity<JsonNode> post(String path,Object body,String token) { return http.exchange(path,HttpMethod.POST,new HttpEntity<>(body,headers(token)),JsonNode.class); }
    private HttpHeaders headers(String token) { var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);if(token!=null)headers.setBearerAuth(token);return headers; }
}
