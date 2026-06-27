package com.company.codeinsight.common.exception;

import com.company.codeinsight.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局统一异常处理器
 * 通过 Spring Web AOP 拦截所有 Controller 抛出的异常，封装为标准的 ApiResponse 返回给前端，
 * 避免向用户暴露敏感的控制台堆栈轨迹，同时规范化错误日志输出。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 拦截并处理自定义的业务异常 (BusinessException)
     * 通常由开发人员在校验不通过、限流拦截或降级兜底时主动抛出。
     *
     * @param e 自定义业务异常对象
     * @return 统一封装的错误响应实体
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<?> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /**
     * 拦截并处理由 @RequestBody 注解标记的 DTO 参数校验校验失败异常 (@Valid / @Validated)
     *
     * @param e 参数校验异常
     * @return 状态码为 400 的参数校验错误信息
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<?> handleValidationException(MethodArgumentNotValidException e) {
        // 拼接全部校验不通过的属性字段对应的错误提示语 (例如: "用户名不能为空, 密码强度不足")
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Validation exception: {}", msg);
        return ApiResponse.error(400, msg);
    }

    /**
     * 拦截并处理由表单提交/QueryParams 参数绑定校验校验失败的异常 (BindException)
     *
     * @param e 表单数据绑定异常
     * @return 状态码为 400 的错误提示语
     */
    @ExceptionHandler(BindException.class)
    public ApiResponse<?> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        log.warn("Bind exception: {}", msg);
        return ApiResponse.error(400, msg);
    }

    /**
     * 拦截最底层的系统级未捕获异常 (Exception)
     * 用于处理如 NullPointerException, Database Exception 等意料之外的系统运行时故障。
     *
     * @param e 运行时未捕获系统异常
     * @return 状态码为 500 的系统级错误响应
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleException(Exception e) {
        log.error("System error", e);
        return ApiResponse.error(500, "System error: " + e.getMessage());
    }
}

