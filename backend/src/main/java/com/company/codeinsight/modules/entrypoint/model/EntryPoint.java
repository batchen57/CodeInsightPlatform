package com.company.codeinsight.modules.entrypoint.model;

import lombok.Data;

/**
 * 反编译项目入口 DTO
 * 标识一个被 EntryPointDiscoveryService 识别出来的入口类（Controller / 定时任务 / MQ Listener / main() 等）。
 */
@Data
public class EntryPoint {

    /**
     * 全限定类名（如 com.demo.controller.UserController）
     */
    private String className;

    /**
     * 源文件相对路径（如 src/main/java/com/demo/controller/UserController.java）
     * 来源：ci_method_call.file_path（项 1 已落表）
     */
    private String filePath;

    /**
     * 入口类型枚举值：CONTROLLER / SCHEDULED_JOB / MQ_LISTENER / COMPONENT / APPLICATION / MAIN
     */
    private String entryType;

    /**
     * 触发的注解简称（如 RestController / RabbitListener），便于审计
     */
    private String annotation;

    /**
     * 备注：RequestMapping 一级路径 / 队列名 等附加信息
     */
    private String remark;
}