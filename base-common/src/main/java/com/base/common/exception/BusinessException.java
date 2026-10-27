package com.base.common.exception;

// 表示可向客户端说明的业务错误，状态码同时用于 HTTP 和响应 code。
public class BusinessException extends RuntimeException {
    private final int status;
    private final Object data;

    public BusinessException(int status, String message) {
        this(status, message, null);
    }

    public BusinessException(int status, String message, Object data) {
        super(message);
        this.status = status;
        this.data = data;
    }

    public int getStatus() { return status; }
    public Object getData() { return data; }
}
