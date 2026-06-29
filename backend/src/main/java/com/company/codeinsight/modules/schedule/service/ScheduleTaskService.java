package com.company.codeinsight.modules.schedule.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.company.codeinsight.modules.schedule.dto.ScheduleTaskCreateDto;
import com.company.codeinsight.modules.schedule.dto.ScheduleTaskPageQuery;
import com.company.codeinsight.modules.schedule.dto.ScheduleTaskUpdateDto;
import com.company.codeinsight.modules.schedule.entity.ScheduleFireRecord;
import com.company.codeinsight.modules.schedule.entity.ScheduleTask;

import java.util.List;

/**
 * 定时任务调度配置服务接口。
 */
public interface ScheduleTaskService extends IService<ScheduleTask> {

    /** 分页查询（按系统/代码库/启用状态/关键字） */
    Page<ScheduleTask> pageQuery(ScheduleTaskPageQuery query);

    /** 新增配置（创建时校验 cron 合法性并计算 next_fire_at） */
    ScheduleTask createConfig(ScheduleTaskCreateDto dto);

    /** 更新配置（cron 改变时重新计算 next_fire_at） */
    ScheduleTask updateConfig(Long id, ScheduleTaskUpdateDto dto);

    /** 启用 */
    void enable(Long id);

    /** 禁用 */
    void disable(Long id);

    /** 软删除 */
    void softDelete(Long id);

    /**
     * 拉取当前到达触发时间的启用配置（由 ScheduleExecutor 周期调用）。
     */
    List<ScheduleTask> findDueSchedules();

    /**
     * 由调度器调用：分布式锁 + overlap_strategy 路由后真正创建任务并 startTask。
     *
     * @param scheduleId 调度配置 ID
     * @return 本次触发创建的 fire record（可能为 SKIPPED / QUEUED / CREATED）
     */
    ScheduleFireRecord tryFire(Long scheduleId);

    /**
     * 由调度器调用：从 Redis QUEUE 队列拉取一个待执行的 schedule_id（仅当 overlap_strategy=QUEUE 时使用）。
     */
    Long popQueuedScheduleId();

    /** 立即触发一次（手动按钮） */
    ScheduleFireRecord triggerNow(Long scheduleId);

    /** 列出某条调度配置的触发历史（按 fire_time DESC） */
    Page<ScheduleFireRecord> listFireRecords(Long scheduleId, int current, int size);
}