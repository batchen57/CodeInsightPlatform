package com.company.codeinsight.modules.prompt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.codeinsight.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 代码分析提示词模板实体类
 * 对应数据库中的 ci_prompt 表，存储系统内置或人工调试优化的 Prompt 核心模板正文、版本号及启用状态。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ci_prompt")
public class DecompilePrompt extends BaseEntity {

    /**
     * 自增主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 提示词模板的别名/名称
     */
    private String name;

    /**
     * 提示词的核心模板文本正文（包含占位符 ${code} 等以在执行时被动态替换）
     */
    private String content;

    /**
     * 模板的版本号（每次编辑保存自动递增，用于追踪演进过程）
     */
    private Integer version;

    /**
     * 模板状态：0-禁用, 1-启用
     */
    private Integer status;

    /**
     * 是否是任务分析时默认使用的全局首选模板：0-否, 1-是
     */
    private Integer isDefault;
}

