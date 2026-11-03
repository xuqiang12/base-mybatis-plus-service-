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

@SpringBootTest(classes = SystemApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:businessFertilizer;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "app.email-code.test-mode=true"})
@ActiveProfiles("test")
class BusinessFertilizerHttpTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @Test
    void catalogAndAccountsAreOwnedAndDeletedProductsKeepSnapshots() {
        String owner=login(), other=login();
        long product=product(owner,"尿素","50","120");
        account(owner);
        assertThat(ok(get("/business/product/list",other)).size()).isZero();
        assertThat(ok(get("/business/account/list",other)).size()).isZero();
        assertThat(post("/business/product/update",Map.of("id",product,"name","越权","bagWeightKg","50","salePrice","120"),other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        long customer=customer(owner);
        var sale=sale(customer,product,"2026-06-30");
        var saved=ok(post("/business/fertilizer-sale/create",sale,owner));
        assertThat(saved.path("totalCents").asLong()).isEqualTo(120000);
        assertThat(saved.path("receivedCents").asLong()).isZero();
        assertThat(saved.path("remainingCents").asLong()).isEqualTo(120000);
        assertThat(saved.path("season").asText()).isEqualTo("2026年春耕账期");
        assertThat(ok(get("/business/customer/detail?id="+customer,owner)).path("fertilizerBalanceCents").asLong()).isEqualTo(120000);
        ok(post("/business/product/update",Map.of("id",product,"name","尿素改名","bagWeightKg","40","salePrice","150"),owner));
        ok(post("/business/product/delete",Map.of("id",product),owner));
        var detail=ok(get("/business/fertilizer-sale/detail?id="+saved.path("id").asText(),owner));
        assertThat(detail.path("items").get(0).path("productName").asText()).isEqualTo("尿素");
        assertThat(detail.path("items").get(0).path("bagWeightKg").asDouble()).isEqualTo(50);
        assertThat(detail.path("items").get(0).path("unitPrice").asDouble()).isEqualTo(120);
        assertThat(get("/business/fertilizer-sale/detail?id="+saved.path("id").asText(),other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ok(post("/business/fertilizer-sale/create",sale,owner)).path("id")).isEqualTo(saved.path("id"));
        assertThat(post("/business/fertilizer-sale/create",sale(customer,product,"2026-07-01"),owner).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(get("/business/product/list",null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void invalidOwnershipLeavesNoSaleAndConcurrentRecordingRetriesAreIdempotent() {
        String owner=login(), other=login();
        long customer=customer(owner), product=product(owner,"复合肥","40","120");
        int user=ok(get("/auth/userInfo",owner)).path("userInfo").path("id").asInt();
        for (var bad : List.of(
                sale(customer(other),product,"2026-07-01"),
                sale(customer,product(other,"外人肥料","50","120"),"2026-07-01"))) {
            assertThat(post("/business/fertilizer-sale/create",bad,owner).getStatusCode().is4xxClientError()).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_trade WHERE owner_id=?",Long.class,user)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_settlement WHERE owner_id=?",Long.class,user)).isZero();
        var request=sale(customer,product,"2026-07-01");
        var a=CompletableFuture.supplyAsync(()->ok(post("/business/fertilizer-sale/create",request,owner)));
        var b=CompletableFuture.supplyAsync(()->ok(post("/business/fertilizer-sale/create",request,owner)));
        assertThat(a.join().path("id")).isEqualTo(b.join().path("id"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_trade WHERE owner_id=?",Long.class,user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business.biz_settlement WHERE owner_id=?",Long.class,user)).isZero();
        request.put("remark","不同内容");
        assertThat(post("/business/fertilizer-sale/create",request,owner).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ok(get("/business/customer/detail?id="+customer,owner)).path("settled").asBoolean()).isFalse();
    }

    @Test
    void multipleProductsRoundPerLineAndRecordingKeepsAllSalesUnpaid() {
        String token=login(); long customer=customer(token), p=product(token,"尿素","50","120"), q=product(token,"磷肥","40","2.35");
        var old=sale(customer,p,"2026-06-30");
        ok(post("/business/fertilizer-sale/create",old,token));
        var request=sale(customer,p,"2027-01-01");
        request.put("items",List.of(Map.of("productId",p,"quantity","1","unitPrice","1.25"),Map.of("productId",q,"quantity","2","unitPrice","2.35")));
        var result=ok(post("/business/fertilizer-sale/create",request,token));
        assertThat(result.path("totalCents").asLong()).isEqualTo(595);
        assertThat(result.path("remainingCents").asLong()).isEqualTo(595);
        assertThat(result.path("season").asText()).isEqualTo("2027年春耕账期");
        assertThat(ok(get("/business/customer/detail?id="+customer,token)).path("fertilizerBalanceCents").asLong()).isEqualTo(120595);
        long id=result.path("id").asLong();
        assertThat(jdbc.queryForObject("SELECT COALESCE(SUM(s.amount_cents),0) FROM business.biz_settlement_item s JOIN business.biz_trade_item i ON i.id=s.trade_item_id WHERE i.trade_id=?",Long.class,id)).isZero();
        var invalid=sale(customer,p,"2026-07-01");
        invalid.put("items",List.of(Map.of("productId",p,"quantity","0.5","unitPrice","120")));
        assertThat(post("/business/fertilizer-sale/create",invalid,token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        invalid.put("items",List.of(Map.of("productId",p,"quantity","1","unitPrice","0.001")));
        assertThat(post("/business/fertilizer-sale/create",invalid,token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Map<String,Object> sale(long customer,long product,String date) {
        var request=new HashMap<String,Object>();
        request.put("customerId",customer); request.put("bizDate",date);
         request.put("remark",""); request.put("requestId",UUID.randomUUID().toString());
        request.put("items",List.of(Map.of("productId",product,"quantity","10","unitPrice","120"))); return request;
    }

    private long product(String token,String name,String weight,String price) { return ok(post("/business/product/create",Map.of("name",name,"bagWeightKg",weight,"salePrice",price,"requestId",UUID.randomUUID().toString()),token)).path("id").asLong(); }
    private long customer(String token) { return ok(post("/business/customer/create",Map.of("name","张三","requestId",UUID.randomUUID().toString()),token)).path("id").asLong(); }
    private long account(String token) { return ok(post("/business/account/create",Map.of("name","生意现金"),token)).path("id").asLong(); }
    private String login() {
        String email=UUID.randomUUID()+"@example.test";
        ok(post("/auth/email/register",Map.of("email",email,"code","123456","nickname","化肥测试","password","business-test-password"),null));
        return ok(post("/auth/login",Map.of("account",email,"password","business-test-password"),null)).path("token").asText();
    }
    private JsonNode ok(ResponseEntity<JsonNode> response) { assertThat(response.getStatusCode()).withFailMessage(response.toString()).isEqualTo(HttpStatus.OK); return response.getBody().path("data"); }
    private ResponseEntity<JsonNode> get(String path,String token) { return http.exchange(path,HttpMethod.GET,new HttpEntity<>(headers(token)),JsonNode.class); }
    private ResponseEntity<JsonNode> post(String path,Object body,String token) { return http.exchange(path,HttpMethod.POST,new HttpEntity<>(body,headers(token)),JsonNode.class); }
    private HttpHeaders headers(String token) { var headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);if(token!=null) headers.setBearerAuth(token);return headers; }
}
