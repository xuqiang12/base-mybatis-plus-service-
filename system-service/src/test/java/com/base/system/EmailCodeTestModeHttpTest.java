package com.base.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SystemApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:emailCodeTestMode;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "app.email-code.test-mode=true", "app.email-code.code-secret=", "app.email-code.from=",
                "spring.mail.host="})
@ActiveProfiles("test")
class EmailCodeTestModeHttpTest {

    private static final String PASSWORD = "test-registration-password";

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void registersWithFixedCodeWithoutSendingEmailAndThenLogsIn() {
        String email = "fixed-code@example.test";
        var registered = http.postForEntity("/auth/email/register",
                Map.of("email", "  Fixed-Code@Example.Test  ", "code", "123456",
                        "password", PASSWORD, "nickname", "  测试用户  "), JsonNode.class);

        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT nickname FROM relations.sys_user WHERE email=?", String.class, email))
                .isEqualTo("测试用户");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_email_code WHERE email=?", Integer.class, email))
                .isZero();
        var login = http.postForEntity("/auth/login", Map.of("account", email, "password", PASSWORD), JsonNode.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().path("data").path("token").asText()).isNotBlank();
    }

    @Test
    void rejectsOtherCodesWithoutCreatingAccount() {
        String email = "wrong-fixed-code@example.test";
        var response = http.postForEntity("/auth/email/register",
                Map.of("email", email, "code", "654321", "password", PASSWORD, "nickname", "测试用户"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_user WHERE email=?", Integer.class, email))
                .isZero();
    }

    @Test
    void stillRejectsInvalidRegistrationParameters() {
        var response = http.postForEntity("/auth/email/register",
                Map.of("email", "invalid", "code", "123456", "password", PASSWORD, "nickname", "测试用户"), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        response = http.postForEntity("/auth/email/register",
                Map.of("email", "invalid-nickname@example.test", "code", "123456", "password", PASSWORD, "nickname", " "), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        response = http.postForEntity("/auth/email/register",
                Map.of("email", "invalid-password@example.test", "code", "123456", "password", "密".repeat(25), "nickname", "测试用户"), JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void doesNotOverwriteExistingAccount() {
        jdbc.update("INSERT INTO relations.sys_user(username,email,password) VALUES ('fixed-existing','fixed-existing@example.test','original-hash')");
        var response = http.postForEntity("/auth/email/register",
                Map.of("email", "fixed-existing@example.test", "code", "123456", "password", PASSWORD, "nickname", "测试用户"), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT password FROM relations.sys_user WHERE username='fixed-existing'", String.class))
                .isEqualTo("original-hash");
    }
}
