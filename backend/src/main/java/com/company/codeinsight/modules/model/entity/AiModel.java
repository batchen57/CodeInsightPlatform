package com.company.codeinsight.modules.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.codeinsight.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 大语言模型配置参数实体类
 * 对应数据库中的 ci_model 表，储存模型名称、调用端点 baseUrl、ApiKey 及是否默认选项。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ci_model")
public class AiModel extends BaseEntity {

    /**
     * 自增主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 模型展示别名（如 GPT-4o-mini）
     */
    private String name;

    /**
     * 大模型 API 请求里的真正模型标识名（如 gpt-4o-mini）
     */
    private String identifier;

    /**
     * 模型供应商提供商名称（如 OpenAI, DeepSeek, Anthropic 等）
     */
    private String provider;

    /**
     * 大模型接口身份认证的 API 密钥凭证（展示时自动脱敏）
     */
    private String apiKey;

    /**
     * 兼容 OpenAI/标准 HTTP 协议的调用接口 Base API URL
     */
    private String baseUrl;

    /**
     * 是否是任务分析的默认首选大模型："true" 或 "false"
     */
    private String isDefault;

    /**
     * 大模型支持的能力集（如 text,image,video，以逗号分隔）
     */
    private String capabilities;

    /**
     * 关于该大语言模型功能的简单描述
     */
    private String description;

    /**
     * 列表展示中的排序权值，值越小越靠前
     */
    private Integer sortOrder;

    /**
     * 启用状态：0-停用，1-启用。
     * 停用时拒绝被任务选用，但保留历史引用（ai_call_record / token_usage_audit 中的 model_name 是字符串外键）。
     */
    private Integer status;

    /**
     * API 响应辅助字段：标识该模型是否已配置密钥，不映射数据库。
     */
    @TableField(exist = false)
    private Boolean hasApiKey;
}

