package com.company.codeinsight.modules.scanwindow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.company.codeinsight.common.model.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 仓库执行时间窗口实体，对应 ci_scan_window。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ci_scan_window")
public class ScanWindowEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long repositoryId;

    /** 周几位掩码：bit0..bit6 对应周一到周日，127=每天 */
    private Integer weekDays;

    private Integer hour;

    private Integer minute;

    private Boolean enabled;

    /** 最近一次实际触发时间，用于幂等 */
    private LocalDateTime lastFiredAt;
}
