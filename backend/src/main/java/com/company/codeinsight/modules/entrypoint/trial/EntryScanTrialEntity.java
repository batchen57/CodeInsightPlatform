package com.company.codeinsight.modules.entrypoint.trial;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.codeinsight.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 入口扫描试跑记录：用户在仓库配置中点击"试跑"产生的结果。
 * <p>不入库真实任务，每次独立执行；唯一持久化用途是历史结果回看。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ci_entry_scan_trial")
public class EntryScanTrialEntity extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("repository_id")
    private Long repositoryId;

    @TableField("user_id")
    private String userId;

    @TableField("status")
    private String status;

    @TableField("config_snapshot")
    private String configSnapshot;

    @TableField("result_json")
    private String resultJson;

    @TableField("error_message")
    private String errorMessage;

    @TableField("started_at")
    private LocalDateTime startedAt;

    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
