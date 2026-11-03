package com.base.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SystemApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:businessProducts;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "app.email-code.test-mode=true"})
@ActiveProfiles("test")
class BusinessProductHttpTest {
    @Autowired TestRestTemplate http;

    @Test
    void createsProductWithMetadataAndWithoutACommonPrice() {
        String token = login();
        var request = fields();
        var created = ok(post("/business/product/create", request, token));
        assertThat(created.path("name").asText()).isEqualTo("复合肥");
        assertThat(created.path("brand").asText()).isEqualTo("史丹利");
        assertThat(created.path("specification").asText()).isEqualTo("15-15-15");
        assertThat(created.path("remark").asText()).isEqualTo("常用肥料");
        assertThat(created.hasNonNull("salePrice")).isFalse();
        assertThat(created.path("bagWeightKg").asDouble()).isEqualTo(50);
        var list = ok(get("/business/product/list", token));
        assertThat(list.get(0).path("brand").asText()).isEqualTo("史丹利");
        assertThat(ok(post("/business/product/create", request, token)).path("id")).isEqualTo(created.path("id"));
        request.put("brand", "另一品牌");
        assertThat(post("/business/product/create", request, token).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void optionalFieldsCanBeClearedAndRemainOwnedByTheAccount() {
        String owner = login(), other = login();
        var request = fields();
        request.put("salePrice", "150.25");
        var created = ok(post("/business/product/create", request, owner));
        assertThat(created.path("salePrice").asDouble()).isEqualTo(150.25);
        assertThat(ok(get("/business/product/list", other)).size()).isZero();
        var update = new HashMap<String, Object>();
        update.put("id", created.path("id").asText());
        update.put("name", "复合肥");
        update.put("bagWeightKg", "40");
        update.put("brand", ""); update.put("specification", ""); update.put("remark", ""); update.put("salePrice", null);
        assertThat(post("/business/product/update", update, other).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        var saved = ok(post("/business/product/update", update, owner));
        assertThat(saved.path("brand").asText()).isEmpty();
        assertThat(saved.path("specification").asText()).isEmpty();
        assertThat(saved.path("remark").asText()).isEmpty();
        assertThat(saved.hasNonNull("salePrice")).isFalse();
    }

    @Test
    void rejectsInvalidWeightsPricesAndOverlongMetadata() {
        String token = login();
        for (String weight : List.of("0", "-1", "1.0001")) {
            var request = fields(); request.put("bagWeightKg", weight);
            assertThat(post("/business/product/create", request, token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        for (String price : List.of("0", "-1", "1.001")) {
            var request = fields(); request.put("salePrice", price);
            assertThat(post("/business/product/create", request, token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        var request = fields(); request.put("brand", "品".repeat(51));
        assertThat(post("/business/product/create", request, token).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Map<String, Object> fields() {
        var request = new HashMap<String, Object>();
        request.put("name", " 复合肥 "); request.put("brand", " 史丹利 ");
        request.put("specification", " 15-15-15 "); request.put("remark", " 常用肥料 ");
        request.put("bagWeightKg", "50"); request.put("requestId", UUID.randomUUID().toString());
        return request;
    }
    private String login() {
        String email = UUID.randomUUID() + "@example.test";
        ok(post("/auth/email/register", Map.of("email", email, "code", "123456", "nickname", "商品测试", "password", "business-test-password"), null));
        return ok(post("/auth/login", Map.of("account", email, "password", "business-test-password"), null)).path("token").asText();
    }
    private JsonNode ok(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode()).withFailMessage(response.toString()).isEqualTo(HttpStatus.OK);
        return response.getBody().path("data");
    }
    private ResponseEntity<JsonNode> get(String path, String token) { return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), JsonNode.class); }
    private ResponseEntity<JsonNode> post(String path, Object body, String token) { return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(token)), JsonNode.class); }
    private HttpHeaders headers(String token) {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return headers;
    }
}
