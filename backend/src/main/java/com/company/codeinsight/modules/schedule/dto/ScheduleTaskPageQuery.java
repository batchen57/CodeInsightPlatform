package com.company.codeinsight.modules.schedule.dto;

import lombok.Data;

/**
 * 定时任务分页查询参数。
 */
@Data
public class ScheduleTaskPageQuery {

    private int current = 1;
    private int size = 10;

    /** 按系统过滤 */
    private Long systemId;

    /** 按代码库过滤 */
    private Long repositoryId;

    /** 按启用状态过滤：true / false / null=全部 */
    private Boolean enabled;

    /** 关键字模糊匹配（name / description） */
    private String keyword;
}