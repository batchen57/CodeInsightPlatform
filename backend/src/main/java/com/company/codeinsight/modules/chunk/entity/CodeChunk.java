package com.company.codeinsight.modules.chunk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 代码切片实体类
 * 对应数据库中的 ci_chunk 表，记录源文件静态扫描切割出来的文件级、类级、方法级代码分析最小分析单元。
 */
@Data
@TableName("ci_chunk")
public class CodeChunk {

    /**
     * 自增主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属的分析任务 ID
     */
    private Long taskId;

    /**
     * 该切片所对应的源文件相对路径
     */
    private String filePath;

    /**
     * 该切片所属于的 Java 类名称
     */
    private String className;

    /**
     * 该切片所属于的 Java 方法名称（若为方法级切片）
     */
    private String methodName;

    /**
     * 切片类型：FILE-文件级别, CLASS-类级别, METHOD-方法级别, DIFF-代码变更级别
     */
    private String chunkType;

    /**
     * 切片源码正文内容的 MD5 哈希校验指纹，用于判断内容是否发生改变以支持增量扫描
     */
    private String contentHash;

    /**
     * 代码在源文件中的起始行号（1-indexed）
     */
    private Integer startLine;

    /**
     * 代码在源文件中的结束行号（1-indexed）
     */
    private Integer endLine;

    /**
     * 对该切片源码的 Token 用量预估值，用于模型超限检测及分包路由
     */
    private Integer tokenEstimate;

    /**
     * 状态：PENDING-待分析, ANALYZED-分析完成, FAILED-分析失败
     */
    private String status;

    /**
     * 大模型分析出错时的失败异常日志
     */
    private String errorReason;

    /**
     * 记录创建时间
     */
    private LocalDateTime createdAt;
}

