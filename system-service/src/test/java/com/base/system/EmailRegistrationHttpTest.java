package com.base.system;

import com.fasterxml.jackson.databind.JsonNode;
import com.base.common.exception.BusinessException;
import com.base.system.modules.auth.EmailCodeService;
import com.base.system.modules.auth.dto.EmailRegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.Map;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

// 仅替换外部 SMTP，注册、密码、手机号绑定、数据库和 JWT 均使用真实实现。
@SpringBootTest(classes = SystemApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.datasource.url=jdbc:h2:mem:emailRegistration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "app.email-code.from=sender@example.test", "app.email-code.code-secret=test-only-email-code-secret-at-least-32-chars"})
@ActiveProfiles("test")
class EmailRegistrationHttpTest {
    private static final String PASSWORD = "test-registration-password";
    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwords;
    @Autowired private EmailCodeService emailCodes;
    @MockitoBean private JavaMailSender mail;
    private final BlockingQueue<SimpleMailMessage> delivered = new LinkedBlockingQueue<>();

    @BeforeEach
    void prepareMail() {
        delivered.clear();
        doAnswer(call -> { delivered.add(new SimpleMailMessage(call.getArgument(0, SimpleMailMessage.class))); return null; })
                .when(mail).send(any(SimpleMailMessage.class));
    }

    @Test
    void registersVerifiedEmailWithPasswordAndThenLogsIn() throws Exception {
        String email = "first@example.test";
        var sent = send(email);
        assertThat(sent.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sent.getBody().toString()).doesNotContain("code_hash", "token");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_user WHERE email=?", Integer.class, email)).isZero();
        String code = receivedCode(email);
        String stored = jdbc.queryForObject("SELECT code_hash FROM relations.sys_email_code WHERE email=?", String.class, email);
        assertThat(stored).doesNotContain(code);
        var registered = post("/auth/email/register", Map.of("email", email, "code", code, "password", PASSWORD,
                "nickname", "  小明  ", "isSuperAdmin", true, "roleIds", List.of(1), "phone", "13800000001"), null);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(registered.getBody().path("data").asText()).isEqualTo("注册成功");
        assertThat(registered.getBody().toString()).doesNotContain("token", PASSWORD);
        var row = jdbc.queryForMap("SELECT id,password,phone,email_verified,is_super_admin FROM relations.sys_user WHERE email=?", email);
        assertThat(row.get("phone")).isNull();
        assertThat(row.get("email_verified")).isEqualTo(true);
        assertThat(row.get("is_super_admin")).isEqualTo(false);
        assertThat(passwords.matches(PASSWORD, (String) row.get("password"))).isTrue();
        assertThat(row.get("password")).isNotEqualTo(PASSWORD);
        var info = get("/auth/userInfo", token(email)).getBody().path("data");
        assertThat(info.path("userInfo").path("id").asInt()).isEqualTo(row.get("id"));
        assertThat(info.path("userInfo").path("email").asText()).isEqualTo(email);
        assertThat(info.path("userInfo").path("nickname").asText()).isEqualTo("小明");
        assertThat(jdbc.queryForObject("SELECT nickname FROM relations.sys_user WHERE email=?", String.class, email)).isEqualTo("小明");
        assertThat(info.path("permissions")).isEmpty();
        assertThat(register(email, code).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(passwordLogin(email, "wrong-password").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsInvalidNicknamesWithoutConsumingCode() throws Exception {
        String email = "invalid-nickname@example.test";
        assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.OK);
        String code = receivedCode(email);
        var missing = post("/auth/email/register", Map.of("email", email, "code", code, "password", PASSWORD), null);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missing.getBody().path("message").asText()).isEqualTo("昵称不能为空");
        for (String nickname : List.of("", " \t\n　 ", "名".repeat(101))) {
            var response = post("/auth/email/register", Map.of("email", email, "code", code,
                    "password", PASSWORD, "nickname", nickname), null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().path("message").asText()).isEqualTo(
                    nickname.length() > 100 ? "昵称不能超过100个字符" : "昵称不能为空");
        }
        assertThat(jdbc.queryForObject("SELECT used FROM relations.sys_email_code WHERE email=?", Boolean.class, email)).isFalse();
        assertThat(register(email, code).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void allowsDuplicateNicknamesAtMaximumLength() throws Exception {
        String nickname = "名".repeat(100);
        for (String email : List.of("same-nickname-a@example.test", "same-nickname-b@example.test")) {
            assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(post("/auth/email/register", Map.of("email", email, "code", receivedCode(email),
                    "password", PASSWORD, "nickname", nickname), null).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(get("/auth/userInfo", token(email)).getBody().path("data").path("userInfo").path("nickname").asText())
                    .isEqualTo(nickname);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_user WHERE nickname=?", Integer.class, nickname)).isEqualTo(2);
    }

    @Test
    void returnsReadableRegistrationValidationMessages() {
        var missingEmail = post("/auth/email/code", Map.of(), null);
        assertThat(missingEmail.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missingEmail.getBody().path("message").asText()).isEqualTo("邮箱不能为空");

        var invalidEmail = send("invalid");
        assertThat(invalidEmail.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidEmail.getBody().path("message").asText()).isEqualTo("邮箱格式不正确");

        var shortPassword = post("/auth/email/register",
                Map.of("email", "valid@example.test", "code", "012345", "password", "short", "nickname", "测试用户"), null);
        assertThat(shortPassword.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(shortPassword.getBody().path("message").asText()).isEqualTo("密码长度必须为8～72个字符");
    }

    @Test
    void rejectsUnsupportedPasswordsWithoutConsumingCode() throws Exception {
        String email = "password-characters@example.test";
        assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.OK);
        String code = receivedCode(email);
        for (String password : List.of("中文密码abc123", "abc123！@", "abc123😀", "abc defgh", "abc\tdefgh", "abc\ndefgh")) {
            var response = post("/auth/email/register", Map.of("email", email, "code", code,
                    "password", password, "nickname", "测试用户"), null);
            assertThat(response.getStatusCode()).as(password).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().path("message").asText())
                    .isEqualTo("密码仅支持英文字母、数字和英文半角符号，不能包含空格");
        }
        for (String password : List.of("Ab1!xyz", "a".repeat(73))) {
            var response = post("/auth/email/register", Map.of("email", email, "code", code,
                    "password", password, "nickname", "测试用户"), null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().path("message").asText()).isEqualTo("密码长度必须为8～72个字符");
        }
        assertThat(jdbc.queryForObject("SELECT used FROM relations.sys_email_code WHERE email=?", Boolean.class, email)).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_user WHERE email=?", Integer.class, email)).isZero();
        assertThat(register(email, code).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void registersAndLogsInWithPasswordsAtBothLengthBoundaries() throws Exception {
        for (String password : List.of("Ab1!@#~?", "a".repeat(68) + "Z9!~")) {
            String email = "password-boundary-" + password.length() + "@example.test";
            assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.OK);
            var response = post("/auth/email/register", Map.of("email", email, "code", receivedCode(email),
                    "password", password, "nickname", "测试用户"), null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(passwordLogin(email, password).getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Test
    void validatesRegistrationInServiceBeforeRunningBusinessLogic() {
        var request = new EmailRegisterRequest();
        request.setNickname("测试用户");
        request.setEmail("invalid");
        request.setCode("012345");
        request.setPassword(PASSWORD);

        assertThatThrownBy(() -> emailCodes.register(request))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(400);
                    assertThat(error.getMessage()).isEqualTo("邮箱格式不正确");
                });
    }

    @Test
    void logsInWithLegacyEmailField() throws Exception {
        String email = "legacy-login-field@example.test";
        createAccount(email);

        var response = post("/auth/login", Map.of("email", email, "password", PASSWORD), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("data").path("token").asText()).isNotBlank();
    }

    @Test
    void enforcesCooldownAndDailyLimits() throws Exception {
        String email = "limited@example.test";
        assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.OK);
        receivedCode(email);
        assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        allowResend(email);
        jdbc.update("UPDATE relations.sys_email_send_limit SET send_count=10 WHERE scope_key=?", "email:" + email);
        assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void enforcesIpLimitWithoutTrustingForwardedHeaders() throws Exception {
        assertThat(send("ip-seed@example.test").getStatusCode()).isEqualTo(HttpStatus.OK);
        receivedCode("ip-seed@example.test");
        jdbc.update("UPDATE relations.sys_email_send_limit SET send_count=50 WHERE scope_key LIKE 'ip:%'");
        try {
            var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Forwarded-For", "203.0.113.123");
            var response = http.exchange("/auth/email/code", HttpMethod.POST,
                    new HttpEntity<>(Map.of("email", "ip-blocked@example.test"), headers), JsonNode.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        } finally { jdbc.update("UPDATE relations.sys_email_send_limit SET send_count=0 WHERE scope_key LIKE 'ip:%'"); }
    }

    @Test
    void rejectsExpiredAndExhaustedCodes() throws Exception {
        String email = "expired@example.test";
        assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.OK);
        String code = receivedCode(email);
        jdbc.update("UPDATE relations.sys_email_code SET expires_at=DATEADD('MINUTE',-1,CURRENT_TIMESTAMP) WHERE email=?", email);
        assertThat(register(email, code).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        allowResend(email);
        assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.OK);
        code = receivedCode(email);
        String wrong = code.equals("000000") ? "000001" : "000000";
        for (int i = 0; i < 5; i++) { assertThat(register(email, wrong).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED); }
        assertThat(register(email, code).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(jdbc.queryForObject("SELECT failed_attempts FROM relations.sys_email_code WHERE email=?", Integer.class, email)).isEqualTo(5);
    }

    @Test
    void failedSendPreservesPreviousCodeAndCountsAttempt() throws Exception {
        String email = "mail-failed@example.test";
        assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.OK);
        String code = receivedCode(email);
        allowResend(email);
        doThrow(new MailSendException("external SMTP failure")).when(mail).send(any(SimpleMailMessage.class));
        var failed = send(email);
        assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(failed.getBody().toString()).doesNotContain("external SMTP");
        assertThat(register(email, code).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void registrationNeverResetsExistingOrDeletedAccountPassword() throws Exception {
        jdbc.update("INSERT INTO relations.sys_user(username,email,phone,password) VALUES ('legacy','Legacy@Example.Test','13800000022','original-hash')");
        jdbc.update("INSERT INTO relations.sys_user(username,email,phone,password,is_deleted) VALUES ('deleted','deleted@example.test','13800000023','deleted-hash',TRUE)");
        for (String email : List.of("legacy@example.test", "deleted@example.test")) {
            assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(register(email, receivedCode(email)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
        assertThat(jdbc.queryForObject("SELECT password FROM relations.sys_user WHERE username='legacy'", String.class)).isEqualTo("original-hash");
        assertThat(jdbc.queryForObject("SELECT password FROM relations.sys_user WHERE username='deleted'", String.class)).isEqualTo("deleted-hash");
    }

    @Test
    void onlyOneConcurrentRegistrationCanConsumeCode() throws Exception {
        String email = "concurrent@example.test";
        assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.OK);
        String code = receivedCode(email);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return register(email, code).getStatusCode().value(); });
            var b = executor.submit(() -> { start.await(); return register(email, code).getStatusCode().value(); });
            start.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 401);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_user WHERE email=?", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void normalizesEmailAndInvalidatesPreviousCodeOnResend() throws Exception {
        String email = "normalize@example.test";
        assertThat(send("  Normalize@Example.Test  ").getStatusCode()).isEqualTo(HttpStatus.OK);
        String old = receivedCode(email);
        allowResend(email);
        assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.OK);
        String latest = receivedCode(email);
        assertThat(latest).isNotEqualTo(old);
        assertThat(register(email, old).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(register("  NORMALIZE@EXAMPLE.TEST  ", latest).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(passwordLogin("NORMALIZE@EXAMPLE.TEST", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void concurrentSendsCannotBypassCooldown() throws Exception {
        String email = "concurrent-send@example.test";
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return send(email).getStatusCode().value(); });
            var b = executor.submit(() -> { start.await(); return send(email).getStatusCode().value(); });
            start.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 429);
        }
        assertThat(register(email, receivedCode(email)).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT send_count FROM relations.sys_email_send_limit WHERE scope_key=?", Integer.class, "email:" + email)).isEqualTo(1);
    }

    @Test
    void validatesRegistrationAndRemovesCodeOnlyLogin() throws Exception {
        assertThat(send("invalid").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(register("valid@example.test", "abc123").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(register("absent@example.test", "123456").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/auth/email/register", Map.of("email", "valid@example.test", "code", "123456", "nickname", "测试用户"), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/auth/email/register", Map.of("email", "valid@example.test", "code", "123456", "password", "short", "nickname", "测试用户"), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/auth/email/login", Map.of("email", "valid@example.test", "code", "123456"), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/user/register", Map.of("username", "unverified", "email", "unverified@example.test",
                "phone", "13800000024", "password", PASSWORD), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String email = "unicode-password@example.test";
        assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.OK);
        String code = receivedCode(email);
        assertThat(post("/auth/email/register", Map.of("email", email, "code", code, "password", "密".repeat(25), "nickname", "测试用户"), null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(register(email, code).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void firstSendFailureLeavesNoUsableCode() {
        String email = "first-failure@example.test";
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
        assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_email_code WHERE email=?", Integer.class, email)).isZero();
        assertThat(register(email, "123456").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void resetsDailyQuotaWhenDateChanges() throws Exception {
        String email = "next-day@example.test";
        assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.OK);
        receivedCode(email);
        allowResend(email);
        jdbc.update("UPDATE relations.sys_email_send_limit SET send_count=10,send_date=DATEADD('DAY',-1,CURRENT_DATE) WHERE scope_key=?", "email:" + email);
        assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(register(email, receivedCode(email)).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void bindsOnlyCurrentUsersPhoneAndEnablesPhonePasswordLogin() throws Exception {
        String owner = createAccount("phone-owner@example.test");
        createAccount("phone-other@example.test");
        int other = jdbc.queryForObject("SELECT id FROM relations.sys_user WHERE email='phone-other@example.test'", Integer.class);
        assertThat(passwordLogin("13800000030", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/user/bindPhone", Map.of("phone", "13800000030"), null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/user/bindPhone", Map.of("phone", "bad"), owner).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/user/bindPhone", Map.of("phone", "13800000030", "id", other), owner).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT phone FROM relations.sys_user WHERE id=?", String.class, other)).isNull();
        assertThat(get("/auth/userInfo", owner).getBody().path("data").path("userInfo").path("phone").asText()).isEqualTo("13800000030");
        assertThat(passwordLogin("13800000030", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(passwordLogin("13800000030", "wrong-password").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        String otherToken = token("phone-other@example.test");
        assertThat(post("/user/bindPhone", Map.of("phone", "13800000030"), otherToken).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(post("/user/bindPhone", Map.of("phone", "13800000031"), owner).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(passwordLogin("13800000030", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(passwordLogin("13800000031", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void concurrentPhoneBindingCannotAssignSameNumberToTwoUsers() throws Exception {
        String aToken = createAccount("bind-a@example.test");
        String bToken = createAccount("bind-b@example.test");
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return post("/user/bindPhone", Map.of("phone", "13800000040"), aToken).getStatusCode().value(); });
            var b = executor.submit(() -> { start.await(); return post("/user/bindPhone", Map.of("phone", "13800000040"), bToken).getStatusCode().value(); });
            start.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 400);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM relations.sys_user WHERE phone='13800000040'", Integer.class)).isEqualTo(1);
    }

    @Test
    void deletedUsersCannotLoginByEmailOrPhoneOrBindPhone() throws Exception {
        String email = "removed@example.test";
        String token = createAccount(email);
        assertThat(post("/user/bindPhone", Map.of("phone", "13800000050"), token).getStatusCode()).isEqualTo(HttpStatus.OK);
        jdbc.update("UPDATE relations.sys_user SET is_deleted=TRUE WHERE email=?", email);
        assertThat(passwordLogin(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(passwordLogin("13800000050", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/user/bindPhone", Map.of("phone", "13800000051"), token).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void preservesExactLoginForHistoricalCaseDistinctEmailAccounts() {
        jdbc.update("INSERT INTO relations.sys_user(username,email,phone,password) VALUES (?,?,?,?)",
                "case-upper", "CaseCollision@Example.Test", "13800000060", passwords.encode(PASSWORD));
        jdbc.update("INSERT INTO relations.sys_user(username,email,phone,password) VALUES (?,?,?,?)",
                "case-lower", "casecollision@example.test", "13800000061", passwords.encode("another-user-password"));
        assertThat(passwordLogin("CaseCollision@Example.Test", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(passwordLogin("casecollision@example.test", "another-user-password").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(passwordLogin("CaseCollision@Example.Test", "another-user-password").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(passwordLogin("CASECOLLISION@EXAMPLE.TEST", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void previousPasswordlessAccountsCannotLoginOrSetPasswordThroughRegistration() throws Exception {
        String email = "previous-passwordless@example.test";
        jdbc.update("INSERT INTO relations.sys_user(username,email,phone,email_verified) VALUES ('previous-passwordless',?,'13800000062',TRUE)", email);
        assertThat(passwordLogin(email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(passwordLogin("13800000062", PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(register(email, receivedCode(email)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jdbc.queryForObject("SELECT password FROM relations.sys_user WHERE email=?", String.class, email)).isNull();
    }

    private String createAccount(String email) throws Exception {
        assertThat(send(email).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(register(email, receivedCode(email)).getStatusCode()).isEqualTo(HttpStatus.OK);
        return token(email);
    }
    private String token(String account) {
        var response = passwordLogin(account, PASSWORD);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().path("data").path("token").asText();
    }
    private ResponseEntity<JsonNode> send(String email) { return post("/auth/email/code", Map.of("email", email), null); }
    private ResponseEntity<JsonNode> register(String email, String code) { return post("/auth/email/register", Map.of("email", email, "code", code, "password", PASSWORD, "nickname", "测试用户"), null); }
    private ResponseEntity<JsonNode> passwordLogin(String account, String password) { return post("/auth/login", Map.of("account", account, "password", password), null); }
    private ResponseEntity<JsonNode> post(String path, Object body, String token) { return http.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers(token)), JsonNode.class); }
    private ResponseEntity<JsonNode> get(String path, String token) { return http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers(token)), JsonNode.class); }
    private HttpHeaders headers(String token) {
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) { headers.setBearerAuth(token); }
        return headers;
    }
    private void allowResend(String email) {
        jdbc.update("UPDATE relations.sys_email_send_limit SET last_sent_at=DATEADD('MINUTE',-2,CURRENT_TIMESTAMP) WHERE scope_key=?", "email:" + email);
    }
    private String receivedCode(String email) throws Exception {
        var message = delivered.poll(2, TimeUnit.SECONDS);
        assertThat(message).isNotNull();
        assertThat(message.getTo()).containsExactly(email);
        var matcher = Pattern.compile("\\b[0-9]{6}\\b").matcher(message.getText());
        assertThat(matcher.find()).isTrue();
        return matcher.group();
    }
}
