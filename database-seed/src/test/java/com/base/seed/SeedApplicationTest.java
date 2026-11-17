package com.base.seed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class SeedApplicationTest {
    @Test
    void standaloneMainFinishesAndClosesPoolAndRepeatNeedsNoPassword(CapturedOutput output) throws Exception {
        String url = "jdbc:h2:mem:seed-main;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        SeedApplication.main(new String[]{"--spring.datasource.url=" + url,
                "--spring.datasource.username=sa", "--spring.datasource.password=",
                "--app.seed-admin.email=main@example.test", "--app.seed-admin.password=seed-test-password"});
        assertThat(output).contains("执行 2 个新模块版本", "Shutdown completed");

        SeedApplication.main(new String[]{"--spring.datasource.url=" + url,
                "--spring.datasource.username=sa", "--spring.datasource.password="});
        assertThat(output).contains("执行 0 个新模块版本");
        assertThat(output).doesNotContain("Tomcat started", "Netty started");
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM relations.sys_user")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getInt(1)).isEqualTo(1);
        }
    }
}
