package com.base.common.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

// 与 Node 一致，将瞬时时间按北京时间返回。
@Configuration
public class JacksonConfig {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer responseDates() {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return builder -> builder.serializers(new LocalDateTimeSerializer(format), new JsonSerializer<Instant>() {
            @Override
            public Class<Instant> handledType() { return Instant.class; }
            @Override
            public void serialize(Instant value, JsonGenerator json, SerializerProvider provider) throws IOException {
                json.writeString(format.withZone(ZoneId.of("Asia/Shanghai")).format(value));
            }
        });
    }
}
