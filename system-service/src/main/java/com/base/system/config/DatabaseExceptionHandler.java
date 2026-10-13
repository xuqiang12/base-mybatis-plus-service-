package com.base.system.config;

import com.base.common.result.ApiResponse;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(0)
public class DatabaseExceptionHandler {
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> integrity(DataIntegrityViolationException e) {
        return ResponseEntity.badRequest().body(ApiResponse.failure(400, "数据已存在或关联数据不合法"));
    }
}
