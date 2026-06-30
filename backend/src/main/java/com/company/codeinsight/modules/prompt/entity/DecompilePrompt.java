package com.company.codeinsight.modules.prompt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.codeinsight.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 代码分析提示词模板实体类
 * 对应数据库中的 ci_prompt 表，存储系统内置或人工调试优化的 Prompt 核心模板正文、版本号及生命周期状态。
 * <p>
 * 通过 {@link #promptType} 区分用途：
 * <ul>
 *     <li>{@code MODULARIZE}：模块提取提示词，用于 AI_ANALYZING / MODULE_HIERARCHY 阶段</li>
 *     <li>{@code DOCUMENT_GENERATION}：文档生成提示词，用于 GENERATING_DOC 阶段</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ci_prompt")
public class DecompilePrompt extends BaseEntity {

    /**
     * 提示词用途：模块提取（MODULARIZE）
     */
    public static final String TYPE_MODULARIZE = "MODULARIZE";

    /**
     * 提示词用途：文档生成（DOCUMENT_GENERATION）
     */
    public static final String TYPE_DOCUMENT_GENERATION = "DOCUMENT_GENERATION";

    /**
     * 生命周期：草稿(DRAFT) — 可编辑/试跑/发布
     */
    public static final String LIFECYCLE_DRAFT = "DRAFT";

    /**
     * 生命周期：已发布(RELEASED) — 不可直改，需复制(→ 新 DRAFT) → 发布
     */
    public static final String LIFECYCLE_RELEASED = "RELEASED";

    /**
     * 生命周期：已归档(ARCHIVED) — 历史保留，不再被流水线使用
     */
    public static final String LIFECYCLE_ARCHIVED = "ARCHIVED";

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
     * 是否是任务分析时默认使用的全局首选模板：0-否, 1-是
     */
    private Integer isDefault;

    /**
     * 提示词用途分类
     * <p>
     * 取值：
     * <ul>
     *     <li>{@link #TYPE_MODULARIZE}：模块提取提示词</li>
     *     <li>{@link #TYPE_DOCUMENT_GENERATION}：文档生成提示词</li>
     * </ul>
     */
    @TableField("prompt_type")
    private String promptType;

    /**
     * 生命周期：DRAFT(草稿,可编辑/试跑/发布) / RELEASED(已发布,锁定) / ARCHIVED(已归档)
     * <p>默认 RELEASED 以保持向后兼容；新建提示词可显式指定 DRAFT。</p>
     */
    private String lifecycle;

    /**
     * 提示词分类
     * <p>DEFAULT = 全局默认提示词(基础配置 → 提示词页管理,通过 is_default=1 标识真正启用项)</p>
     * <p>USER = 用户自定义提示词(按 scope_id 隔离,不同 scope 互不可见)</p>
     */
    private String category;

    /**
     * USER 提示词的 scope ID
     * <p>标识该 USER 提示词归属哪个配置上下文(系统 ID 或 仓库 ID);DEFAULT 提示词此字段为 NULL(全局可见)</p>
     */
    private Long scopeId;

    /** 是否已发布、可用于流水线与设为默认 */
    public boolean isReleased() {
        return LIFECYCLE_RELEASED.equals(lifecycle);
    }
}

