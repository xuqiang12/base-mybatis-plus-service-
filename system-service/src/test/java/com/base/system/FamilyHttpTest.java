package com.base.system;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SystemApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:familyOnly;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
            "app.email-code.test-mode=true"
        })
@ActiveProfiles("test")
class FamilyHttpTest {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;

    @Test
    void registrationInitializesOnlyFamilyMembership() {
        String token = account();
        JsonNode state = state(token);
        assertThat(state.path("family").path("members").size()).isEqualTo(1);
        assertThat(state.path("localUsers").size()).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='relations' "
                        + "AND table_name IN ('ledger_invitation','ledger_friend','ledger_book','ledger_gift',"
                        + "'ledger_loan','ledger_category','ledger_household')", Integer.class)).isZero();
    }

    @Test
    void sharedCardJoinsTwoAccountsAndLeavingRestoresSingleFamilies() {
        String a = account(), b = account(), originalA = family(a), originalB = family(b);
        String invitation = share(a);
        assertThat(ok(get("/family/invitation?token=" + invitation, b)).path("inviterName").asText())
                .isEqualTo("测试家人");
        assertThat(post("/family/invitation/accept", Map.of("token", invitation), a, originalA)
                .getStatusCode().value()).isEqualTo(400);
        ok(post("/family/invitation/accept", Map.of("token", invitation), b, originalB));
        String shared = family(a);
        assertThat(shared).isNotIn(originalA, originalB);
        assertThat(family(b)).isEqualTo(shared);
        assertThat(state(a).path("family").path("members").size()).isEqualTo(2);
        assertThat(state(a).has("invitation")).isFalse();
        assertThat(get("/family/invitation?token=" + invitation, b).getStatusCode().value()).isEqualTo(400);
        assertThat(post("/family/invitation/share", Map.of(), a, shared).getStatusCode().value()).isEqualTo(400);
        ok(post("/family/leave", Map.of(), b, shared));
        assertThat(family(a)).isEqualTo(shared);
        assertThat(family(b)).isNotEqualTo(shared);
        assertThat(state(a).path("family").path("members").size()).isEqualTo(1);
        assertThat(state(b).path("family").path("members").size()).isEqualTo(1);
        assertThat(post("/family/leave", Map.of(), b, family(b)).getStatusCode().value()).isEqualTo(400);
        // Leaving never makes the original shared card valid again.
        assertThat(get("/family/invitation?token=" + invitation, b).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void signedInvitationRejectsTamperingLoginTokensAndStaleFamily() {
        String a = account(), b = account(), fb = family(b);
        String invitation = share(a);
        String[] parts = invitation.split("\\.");
        String tampered = parts[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"family-invitation\",\"familyId\":\"other\",\"inviterId\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                + "." + parts[2];
        for (String invalid : List.of("invalid", tampered, a)) {
            assertThat(post("/family/invitation/accept", Map.of("token", invalid), b, fb)
                    .getStatusCode().value()).isEqualTo(400);
        }
        assertThat(get("/family/state", invitation).getStatusCode().value()).isEqualTo(401);
        assertThat(get("/family/invitation?token=" + invitation, null).getStatusCode().value()).isEqualTo(401);
        assertThat(post("/family/invitation/accept", Map.of(), b, fb).getStatusCode().value()).isEqualTo(400);
        assertThat(post("/family/invitation/accept", Map.of("token", invitation), b, "stale")
                .getStatusCode().value()).isEqualTo(409);
        assertThat(family(b)).isEqualTo(fb);
    }

    @Test
    void multipleCardsRemainUsableUntilMembershipChanges() {
        String a = account(), b = account(), c = account();
        String first = share(a);
        share(a);
        assertThat(ok(get("/family/invitation?token=" + first, b)).path("familyName").asText()).isEqualTo("我的家庭");
        ok(post("/family/invitation/accept", Map.of("token", first), b, family(b)));
        String shared = family(b);
        assertThat(post("/family/invitation/accept", Map.of("token", share(c)), b, shared)
                .getStatusCode().value()).isEqualTo(400);
        assertThat(family(a)).isEqualTo(shared);
    }

    @Test
    void concurrentAcceptanceAllowsOnlyOneNewMember() throws Exception {
        String a = account(), b = account(), c = account(), fb = family(b), fc = family(c);
        String invitation = share(a);
        var start = new CountDownLatch(1);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var first = threads.submit(() -> {
                start.await();
                return post("/family/invitation/accept", Map.of("token", invitation), b, fb).getStatusCode().value();
            });
            var second = threads.submit(() -> {
                start.await();
                return post("/family/invitation/accept", Map.of("token", invitation), c, fc).getStatusCode().value();
            });
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 400);
        }
        assertThat(state(a).path("family").path("members").size()).isEqualTo(2);
    }

    String share(String token) {
        return ok(post("/family/invitation/share", Map.of(), token, family(token))).path("token").asText();
    }

    @Test
    void extraBusinessAndHistoryEndpointsAreNotExposed() {
        String token = account(), family = family(token);
        for (String path : List.of("/friend/create", "/gift-book/create", "/gift-record/create",
                "/loan-record/create", "/household-category/create", "/household-record/create",
                "/family/history/clear", "/family/history/publish", "/family/invitation/create")) {
            assertThat(post(path, Map.of("name", "测试", "date", "2026-10-19"), token, family)
                    .getStatusCode().value()).as(path).isEqualTo(404);
        }
        for (String path : List.of("/ledger/data", "/family/history/list", "/family/history/data?id=test")) {
            assertThat(get(path, token).getStatusCode().value()).as(path).isEqualTo(404);
        }
    }

    String account() {
        String email = UUID.randomUUID() + "@example.test";
        ok(post("/auth/email/register", Map.of("email", email, "code", "123456",
                "nickname", "测试家人", "password", "family-test-password"), null, null));
        return ok(post("/auth/login", Map.of("account", email, "password", "family-test-password"), null, null))
                .path("token").asText();
    }

    JsonNode state(String token) { return ok(get("/family/state", token)); }
    String family(String token) { return state(token).path("family").path("id").asText(); }
    JsonNode ok(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode()).withFailMessage(response.toString()).isEqualTo(HttpStatus.OK);
        return response.getBody().path("data");
    }
    ResponseEntity<JsonNode> get(String path, String token) {
        return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token, null)), JsonNode.class);
    }
    ResponseEntity<JsonNode> post(String path, Object body, String token, String family) {
        return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(token, family)), JsonNode.class);
    }
    HttpHeaders headers(String token, String family) {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        if (family != null) headers.set("X-Family-Id", family);
        return headers;
    }
}
