package com.company.codeinsight.modules.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.codeinsight.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI模型配置实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ci_model")
public class AiModel extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String identifier;

    private String provider;

    private String apiKey;

    private String baseUrl;

    private String isDefault; // "true" 或 "false"

    private String capabilities; // 逗号分隔，如 "text,image,video"

    private String description;

    private Integer sortOrder;
}
