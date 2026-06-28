package com.company.codeinsight.modules.push.enums;

/**
 * 推送任务状态枚举
 *
 * <pre>
 * PENDING    - 已入队，等待调度执行
 * PROCESSING - 正在被调度器执行中
 * SUCCESS    - 推送成功
 * FAILED     - 推送失败（已达到最大重试次数）
 * </pre>
 */
public enum PushTaskStatus {

    /** 已入队等待调度 */
    PENDING,

    /** 正在执行推送 */
    PROCESSING,

    /** 推送成功 */
    SUCCESS,

    /** 推送失败（达到最大重试次数） */
    FAILED
}
