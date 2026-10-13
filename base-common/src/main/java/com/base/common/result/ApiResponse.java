// 定义业务服务共用的响应结构和成功返回方式。
package com.base.common.result;

public record ApiResponse<T>(int code, String message, T data) {

    // 将业务数据封装为统一的成功响应。
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    // 返回不携带内部异常信息的失败结果。
    public static ApiResponse<Void> failure(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }
}
