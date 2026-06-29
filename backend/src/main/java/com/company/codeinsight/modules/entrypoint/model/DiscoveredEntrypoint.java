package com.company.codeinsight.modules.entrypoint.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 入口识别结果（含方法列表）
 * <p>封装 {@link EntryPoint}（class 级元数据）与该类下提取出的关键方法列表。
 * 仅用于流水线内部在 ENTRYPOINT_REVIEW 阶段落表与复核展示，不参与 AI 调度逻辑。</p>
 *
 * @see com.company.codeinsight.modules.entrypoint.service.EntrypointReviewService
 */
@Data
public class DiscoveredEntrypoint {

    /**
     * 类级元数据（className / filePath / entryType / annotation / remark）
     * 复用现有 {@link EntryPoint} DTO，避免重复定义。
     */
    private EntryPoint base;

    /**
     * 该入口类下提取出的关键方法列表
     * - CONTROLLER：仅包含带 @RequestMapping/@GetMapping/@PostMapping/@PutMapping/@DeleteMapping/@PatchMapping 的方法
     * - SCHEDULED_JOB / MQ_LISTENER / COMPONENT：当前 parser 不存方法级注解时，回退为所有非 private 方法
     * - APPLICATION / MAIN：main() 方法
     */
    private List<DiscoveredMethod> methods = new ArrayList<>();
}