package com.base.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SystemApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"business.enabled=false",
                "spring.datasource.url=jdbc:h2:mem:businessDisabled;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "app.email-code.test-mode=true"})
@ActiveProfiles("test")
class BusinessDisabledHttpTest {
    @Autowired TestRestTemplate http;
    @Autowired ApplicationContext context;

    @Test
    void disablingBusinessLeavesLoginAndFamilyAvailable() {
        assertThat(context.containsBean("businessController")).isFalse();
        assertThat(context.containsBean("businessCustomerService")).isFalse();
        String email = UUID.randomUUID() + "@example.test";
        var register = http.postForEntity("/auth/email/register", Map.of("email", email, "code", "123456",
                "nickname", "家庭", "password", "family-test-password"), JsonNode.class);
        assertThat(register.getStatusCode()).isEqualTo(HttpStatus.OK);
        var login = http.postForEntity("/auth/login", Map.of("account", email, "password", "family-test-password"), JsonNode.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        var headers = new HttpHeaders();
        headers.setBearerAuth(login.getBody().path("data").path("token").asText());
        assertThat(http.exchange("/friend/list", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.exchange("/business/customer/list", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
