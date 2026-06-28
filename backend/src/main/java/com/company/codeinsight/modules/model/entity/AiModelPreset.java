package com.company.codeinsight.modules.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.codeinsight.common.model.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 模型预设模板实体。
 * 预设模板用于快速填充模型配置表单，不保存 API Key，也不直接参与任务执行。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ci_model_preset")
public class AiModelPreset extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "请输入预设名称")
    private String name;

    @NotBlank(message = "请输入技术供应商")
    private String provider;

    @NotBlank(message = "请输入模型调用ID")
    private String identifier;

    private String baseUrl;

    private String capabilities;

    private String description;

    private Integer sortOrder;

    private Integer status;
}
