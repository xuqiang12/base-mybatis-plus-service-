package com.base.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

// 记录方法、路径、HTTP 状态和耗时，不记录请求正文或认证信息。
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestLogFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            log.info("{} {} status={} duration={}ms", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), (System.nanoTime() - start) / 1_000_000);
        }
    }
}
