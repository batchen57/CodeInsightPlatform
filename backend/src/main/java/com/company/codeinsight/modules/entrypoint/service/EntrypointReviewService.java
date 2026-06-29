package com.company.codeinsight.modules.entrypoint.service;

import com.company.codeinsight.modules.entrypoint.model.DiscoveredEntrypoint;
import com.company.codeinsight.modules.entrypoint.model.EntrypointReviewView;
import com.company.codeinsight.modules.entrypoint.model.EntryPoint;
import com.company.codeinsight.modules.entrypoint.model.EntryPointConfig;
import com.company.codeinsight.modules.task.entity.DecompileTask;

import java.io.File;
import java.util.List;

/**
 * 知识入口复核服务
 * <p>在 SPLITTING_TASK 完成后、调用 AI 之前，由流水线触发一次入口识别并落表 ci_entrypoint，
 * 等待用户在 ENTRYPOINT_REVIEW 状态下确认（继续）或驳回（终止任务）。</p>
 *
 * <p>该服务把"识别"与"落表"绑成一次原子动作，避免中间态被 AI 阶段误用；AI 阶段改为读取
 * {@link #loadEnabledEntries(Long)}，保证用户确认过的入口集合是 AI 调度的真实输入。</p>
 *
 * @see com.company.codeinsight.modules.entrypoint.service.EntryPointDiscoveryService
 */
public interface EntrypointReviewService {

    /**
     * 全量识别入口（含方法列表）并落表 ci_entrypoint（delete-then-insert by taskId）。
     * <p>无论 requireEntrypointReview 是否启用都执行，保证 AI 阶段总能读到一致的数据。</p>
     *
     * @return 识别并落表后的入口列表（供流水线 execLog 输出统计）
     */
    List<DiscoveredEntrypoint> discoverAndPersist(Long taskId, File projectDir, EntryPointConfig config);

    /**
     * 复核页用：返回 task 下所有入口（含反序列化后的方法列表），按 sort_order / id 升序。
     * <p>只读视图，仅由 controller 层在序列化前反序列化 methods_json 字段。</p>
     */
    List<EntrypointReviewView> listByTaskId(Long taskId);

    /**
     * AI 阶段用：返回已落表的 enabled=true 入口（仅 class 级），构造为现有 {@link EntryPoint} DTO。
     * <p>不携带方法信息——AI 调度只看入口类。</p>
     */
    List<EntryPoint> loadEnabledEntries(Long taskId);

    /**
     * 解析 EntryPointConfig：任务级空时回退到仓库级，再 null 走默认 Controller/JOB/MQ 兜底。
     * <p>与 {@code ModuleHierarchyServiceImpl.buildAndPersist} 原有解析逻辑保持一致。</p>
     */
    EntryPointConfig resolveConfig(DecompileTask task);
}