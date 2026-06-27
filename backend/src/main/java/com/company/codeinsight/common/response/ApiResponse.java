package com.company.codeinsight.common.response;

import lombok.Data;

/**
 * 统一接口返回对象
 * 规范化后端向前端返回的数据格式，统一包含状态码、响应消息以及泛型数据载荷。
 *
 * @param <T> 数据载荷的类型
 */
@Data
public class ApiResponse<T> {
    
    // 状态码：0 代表处理成功，非 0 代表发生相应故障/业务错误
    private int code;
    
    // 提示信息，如 "success" 或具体的故障描述描述
    private String message;
    
    // 具体的业务数据载荷，可为单实体、列表、分页等
    private T data;

    public ApiResponse() {}

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 快捷生成操作成功的响应对象（带数据）
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    /**
     * 快捷生成操作成功的响应对象（无数据）
     */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(0, "success", null);
    }

    /**
     * 快捷生成带自定义错误码的响应对象响应对象
     */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /**
     * 快捷生成带默认错误码(400)的响应对象
     */
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(400, message, null);
    }
}

