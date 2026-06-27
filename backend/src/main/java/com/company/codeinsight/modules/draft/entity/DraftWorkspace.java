package com.company.codeinsight.modules.draft.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评审工作区实体类
 * 对应数据库中的 ci_draft_workspace 表，对应一次成功的反编译/静态分析任务所启动的待复核协同编辑工作区。
 */
@Data
@TableName("ci_draft_workspace")
public class DraftWorkspace {

    /**
     * 自增主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所产生关联的反编译任务 ID
     */
    private Long taskId;

    /**
     * 关联的业务系统 ID
     */
    private Long systemId;

    /**
     * 关联的 Git 代码库 ID
     */
    private Long repositoryId;

    /**
     * 评审状态：ACTIVE-活跃编辑中, COMPLETED-复核完成通过, ARCHIVED-历史归档
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}

