package com.base.common.web;

import com.base.common.exception.BusinessException;
import com.base.common.result.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.ErrorResponse;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

// 统一返回业务和框架错误，内部异常只写入服务端日志。
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException e) {
        return failure(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> validation(BindException e) {
        var error = e.getBindingResult().getFieldError();
        return failure(400, error == null ? "请求参数不合法" : error.getDefaultMessage());
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> invalid(Exception e) {
        return failure(400, "请求参数不合法");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception e) {
        if (e instanceof ErrorResponse error) {
            int status = error.getStatusCode().value();
            return failure(status, status == 404 ? "记录或接口不存在" : "请求处理失败");
        }
        log.error("请求处理发生未预期异常", e);
        return failure(500, "服务器错误");
    }

    private ResponseEntity<ApiResponse<Void>> failure(int status, String message) {
        return ResponseEntity.status(status).body(ApiResponse.failure(status, message));
    }
}
