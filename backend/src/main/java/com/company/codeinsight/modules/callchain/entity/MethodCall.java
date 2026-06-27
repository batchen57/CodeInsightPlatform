package com.company.codeinsight.modules.callchain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 方法调用链路实体类
 * 对应数据库中的 ci_method_call 表，记录 AST 静态解析得到的类内方法与方法之间的调用关系明细。
 * 由反编译任务在 PARSING_CODE 阶段批量写入，作为后续入口识别和模块整体归纳的数据基础。
 */
@Data
@TableName("ci_method_call")
public class MethodCall {

    /**
     * 自增主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属反编译分析任务 ID
     */
    private Long taskId;

    /**
     * 源文件相对路径（如 src/main/java/com/demo/UserController.java）
     */
    private String filePath;

    /**
     * 调用方所属 Java 类名称
     */
    private String className;

    /**
     * 调用方所在的方法名称
     */
    private String callerMethod;

    /**
     * 被调依赖在调用方类中所声明的字段/变量名称（含类型信息，如 userService:UserService）
     */
    private String dependencyName;

    /**
     * 实际被调用的目标方法名称
     */
    private String targetMethod;

    /**
     * 调用表达式原始文本（如 userService.findById(id)）
     */
    private String expression;

    /**
     * 调用所在源文件行号（1-indexed）
     */
    private Integer lineNumber;

    /**
     * 记录创建时间
     */
    private LocalDateTime createdAt;
}