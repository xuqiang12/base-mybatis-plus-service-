package com.base.system;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SystemApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:accountCancellation;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "app.email-code.test-mode=true"
        })
@ActiveProfiles("test")
class AccountCancellationHttpTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void cleanData() {
        for (String table : List.of("ledger_money_adjustment", "ledger_money_transfer", "ledger_household_item",
                "ledger_household_month", "ledger_money_log", "ledger_gift", "ledger_loan", "ledger_book",
                "ledger_friend", "ledger_category", "ledger_money_project", "ledger_member",
                "ledger_family", "sys_user_role", "sys_user")) {
            jdbc.update("DELETE FROM relations." + table);
        }
    }

    @Test
    void cancellationAnonymizesSharedDataInvalidatesTokenAndAllowsEmailReuse() {
        String email = UUID.randomUUID() + "@example.test";
        register(email, "原用户", "old-password");
        String token = login(email, "old-password");
        int userId = jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE email=?", Integer.class, email);
        String familyId = jdbc.queryForObject("SELECT family_id FROM relations.ledger_member WHERE user_id=?", String.class, userId);
        jdbc.update("UPDATE relations.ledger_family SET name=? WHERE id=?", "共同家庭", familyId);

        String otherEmail = UUID.randomUUID() + "@example.test";
        register(otherEmail, "家人", "other-password");
        int otherId = jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE email=?", Integer.class, otherEmail);
        jdbc.update("DELETE FROM relations.ledger_member WHERE user_id=?", otherId);
        jdbc.update("INSERT INTO relations.ledger_member(user_id,family_id) VALUES (?,?)", otherId, familyId);
        jdbc.update("INSERT INTO relations.ledger_gift(family_id,event_type,direction,date,name,appellation,amount_cents,note,created_by,created_by_name) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                familyId, "婚礼", "sent", "2026-11-09", "家人", "", 10000, "", userId, "原用户");

        var wrongPassword = post("/auth/account/cancel", Map.of("password", "wrong-password"), token);
        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("/auth/userInfo", token).getStatusCode()).isEqualTo(HttpStatus.OK);

        var cancelled = post("/auth/account/cancel", Map.of("password", "old-password"), token);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT is_deleted FROM relations.sys_user WHERE id=?", Boolean.class, userId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT email FROM relations.sys_user WHERE id=?", String.class, userId))
                .isEqualTo("deleted-" + userId + "@deleted.invalid");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_member WHERE user_id=?", Integer.class, userId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT created_by_name FROM relations.ledger_gift WHERE family_id=?", String.class, familyId))
                .isEqualTo("已注销用户");
        assertThat(get("/auth/userInfo", token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        register(email, "新账号", "new-password");
        String newToken = login(email, "new-password");
        JsonNode family = get("/family/state", newToken).getBody().path("data").path("family");
        assertThat(family.path("members").size()).isEqualTo(1);
        assertThat(family.path("id").asText()).isNotEqualTo(familyId);
    }

    @Test
    void cancellationDeletesDataFromASoleFamily() {
        String email = UUID.randomUUID() + "@example.test";
        register(email, "独立用户", "sole-password");
        String token = login(email, "sole-password");
        int userId = jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE email=?", Integer.class, email);
        String familyId = jdbc.queryForObject("SELECT family_id FROM relations.ledger_member WHERE user_id=?", String.class, userId);
        jdbc.update("INSERT INTO relations.ledger_gift(family_id,event_type,direction,date,name,appellation,amount_cents,note,created_by,created_by_name) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                familyId, "生日", "received", "2026-11-09", "朋友", "", 5000, "", userId, "独立用户");

        assertThat(post("/auth/account/cancel", Map.of("password", "sole-password"), token).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_family WHERE id=?", Integer.class, familyId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.ledger_gift WHERE family_id=?", Integer.class, familyId))
                .isZero();
    }

    private void register(String email, String nickname, String password) {
        assertThat(post("/auth/email/register", Map.of("email", email, "code", "123456",
                "nickname", nickname, "password", password), null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private String login(String email, String password) {
        var response = post("/auth/login", Map.of("account", email, "password", password), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().path("data").path("token").asText();
    }

    private ResponseEntity<JsonNode> get(String path, String token) {
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), JsonNode.class);
    }

    private ResponseEntity<JsonNode> post(String path, Object body, String token) {
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(token)), JsonNode.class);
    }

    private HttpHeaders headers(String token) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return headers;
    }
}
