package com.base.foundation;

import com.base.common.result.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import com.base.common.BaseCommonConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// 使用真实 HTTP 验证公共请求链，探针接口仅存在于测试环境。
@SpringBootTest(classes = FoundationHttpTest.ProbeApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"springdoc.api-docs.enabled=false"})
class FoundationHttpTest {
    @Autowired
    private TestRestTemplate http;

    @Test
    void wrapsSuccessAndFormatsDatesInShanghai() {
        var response = http.getForEntity("/probe/value", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("code", 0).containsEntry("message", "success");
        assertThat(((Map<?, ?>) response.getBody().get("data")).get("createdAt"))
                .isEqualTo("2026-07-03 15:29:23");
    }

    @Test
    void wrapsStringWithoutDoubleWrappingExistingResponse() {
        assertThat(http.getForObject("/probe/text", Map.class)).containsEntry("data", "完成");
        assertThat(http.getForObject("/probe/wrapped", Map.class)).containsEntry("data", "已有响应");
    }

    @Test
    void validationFailureReturns400InUnifiedFormat() {
        var response = http.postForEntity("/probe/value", Map.of("name", ""), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", 400).containsEntry("data", null);
    }

    @Test
    void missingResourceAndUnexpectedErrorKeepStatusAndHideInternals() {
        var missing = http.getForEntity("/probe/missing", Map.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).containsEntry("code", 404).containsEntry("data", null);
        var failed = http.getForEntity("/probe/error", Map.class);
        assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(failed.getBody()).containsEntry("code", 500).containsEntry("data", null);
        assertThat(failed.getBody().toString()).doesNotContain("private database detail");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({BaseCommonConfiguration.class, ProbeController.class})
    static class ProbeApplication {}

    @RestController
    static class ProbeController {
        @GetMapping("/probe/value")
        Map<String, Object> value() { return Map.of("createdAt", Instant.parse("2026-07-03T07:29:23Z")); }
        @GetMapping("/probe/text")
        String text() { return "完成"; }
        @GetMapping("/probe/wrapped")
        ApiResponse<String> wrapped() { return ApiResponse.success("已有响应"); }
        @PostMapping("/probe/value")
        Input create(@Valid @RequestBody Input input) { return input; }
        @GetMapping("/probe/missing")
        void missing() { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在"); }
        @GetMapping("/probe/error")
        void error() { throw new IllegalStateException("private database detail"); }
    }

    record Input(@NotBlank(message = "名称不能为空") String name) {}
}
