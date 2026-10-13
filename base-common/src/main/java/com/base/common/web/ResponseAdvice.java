package com.base.common.web;

import com.base.common.result.ApiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

// 统一包装普通业务返回，已包装响应和接口文档不重复处理。
@RestControllerAdvice
@RequiredArgsConstructor
public class ResponseAdvice implements ResponseBodyAdvice<Object> {
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter type, Class<? extends HttpMessageConverter<?>> converter) {
        return type.getDeclaringClass().getPackageName().startsWith("com.base.");
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter type, MediaType mediaType,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse<?>) { return body; }
        ApiResponse<?> result = ApiResponse.success(body);
        if (StringHttpMessageConverter.class.isAssignableFrom(converter)) {
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            try {
                return objectMapper.writeValueAsString(result);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("响应序列化失败", e);
            }
        }
        return result;
    }
}
