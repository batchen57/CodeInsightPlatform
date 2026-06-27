package com.company.codeinsight.modules.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.codeinsight.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

/**
 * 反编译及扫描分析任务实体类
 * 对应数据库中的 ci_task 表，管理任务状态机迁移（DRAFT, PENDING, PULLING_CODE, PARSING, SPLITTING, AI_ANALYZING, GENERATING_DOC, COMPLETED, FAILED 等）、任务进度百分比、时长等。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ci_task")
public class DecompileTask extends BaseEntity {

    /**
     * 自增主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统 ID
     */
    private Long systemId;

    /**
     * 关联的 Git 代码库 ID
     */
    private Long repositoryId;

    /**
     * 运行任务时所选定的 AI 提示词模板版本号
     */
    private Integer promptVersion;

    /**
     * 运行任务时所选定的 AI 大模型唯一标识标识
     */
    private String modelName;

    /**
     * 任务当前状态：DRAFT-草稿, PENDING-排队等待中, PULLING_CODE-拉取代码中, PARSING-代码解析中, SPLITTING-切片进行中, AI_ANALYZING-AI分析归纳中, GENERATING_DOC-生成文档中, COMPLETED-任务完成, FAILED-分析失败
     */
    private String status;

    /**
     * 任务分析类型：INITIAL-全量初始化, INCREMENTAL-增量差分分析
     */
    private String type;

    /**
     * 任务执行完成度百分比数值（范围：0 ~ 100）
     */
    private Integer progress;

    /**
     * 本次分析生成的本地调试/运行详细控制台输出日志的存储 URI 路径
     */
    private String logUri;

    /**
     * 任务执行异常失败的具体错误日志原因
     */
    private String errorReason;

    /**
     * 任务运行的总耗时时长（单位：毫秒）
     */
    private Long durationMs;

    /**
     * 任务启动时间
     */
    private LocalDateTime startedAt;

    /**
     * 任务结束时间（完成或失败）
     */
    private LocalDateTime endedAt;

    /**
     * 任务级入口扫描配置 JSON 字符串（整体序列化 EntryPointConfig）
     * null 表示使用系统默认行为（注解驱动 Controller/JOB/MQ 等）
     */
    @TableField("entry_scan_config")
    private String entryScanConfig;
}

