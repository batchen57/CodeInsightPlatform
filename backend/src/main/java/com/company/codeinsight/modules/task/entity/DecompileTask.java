package com.company.codeinsight.modules.task.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.codeinsight.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ci_task")
public class DecompileTask extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long systemId;

    private Long repositoryId;

    private Integer promptVersion;

    private String modelName;

    private String status; // DRAFT, PENDING, PULLING_CODE, etc.

    private String type; // INITIAL, INCREMENTAL

    private Integer progress; // 进度百分比 0-100

    private String logUri;

    private String errorReason;

    private Long durationMs;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;
}
