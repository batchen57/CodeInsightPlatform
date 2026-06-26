package com.company.codeinsight.modules.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.codeinsight.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ci_repository")
public class CodeRepository extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long systemId;

    private String gitUrl;

    private String branch;

    private String username;

    private String password; // 存储密码或 Access Token

    private String scanRoot;

    private String excludeDirs; // 逗号分隔的排除目录

    private String excludeFileTypes; // 逗号分隔的排除扩展名

    private String lastCommitId;

    private LocalDateTime lastDecompileAt;
}
