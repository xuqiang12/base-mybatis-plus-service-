package com.base.common.exception;

// 表示可向客户端说明的业务错误，状态码同时用于 HTTP 和响应 code。
public class BusinessException extends RuntimeException {
    private final int status;

    public BusinessException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() { return status; }
}
