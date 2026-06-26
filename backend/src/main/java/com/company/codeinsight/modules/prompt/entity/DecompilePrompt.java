package com.company.codeinsight.modules.prompt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.codeinsight.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ci_prompt")
public class DecompilePrompt extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String content;

    private Integer version;

    private Integer status; // 0-禁用, 1-启用

    private Integer isDefault; // 0-否, 1-是
}
