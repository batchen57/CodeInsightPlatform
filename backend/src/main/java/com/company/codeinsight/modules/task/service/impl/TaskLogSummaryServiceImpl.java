package com.company.codeinsight.modules.task.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.codeinsight.modules.ai.entity.AiCallRecord;
import com.company.codeinsight.modules.ai.mapper.AiCallRecordMapper;
import com.company.codeinsight.modules.chunk.entity.CodeChunk;
import com.company.codeinsight.modules.chunk.mapper.CodeChunkMapper;
import com.company.codeinsight.modules.hierarchy.entity.ModuleHierarchyNode;
import com.company.codeinsight.modules.hierarchy.mapper.ModuleHierarchyNodeMapper;
import com.company.codeinsight.modules.scanner.entity.CodeFileSnapshot;
import com.company.codeinsight.modules.scanner.mapper.CodeFileSnapshotMapper;
import com.company.codeinsight.modules.task.dto.PipelineStageStatDto;
import com.company.codeinsight.modules.task.dto.TaskLogSummaryDto;
import com.company.codeinsight.modules.task.entity.DecompileTask;
import com.company.codeinsight.modules.task.enums.TaskStatus;
import com.company.codeinsight.modules.task.mapper.DecompileTaskMapper;
import com.company.codeinsight.modules.task.service.TaskExecutionLogger;
import com.company.codeinsight.modules.task.service.TaskLogSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 任务执行日志摘要服务实现。
 * 单次读快照聚合 ci_task / ci_chunk / ci_ai_call_record / ci_file_snapshot / ci_module_hierarchy 与 pipeline.log 文本。
 * 不开事务（聚合只读）。
 */
@Slf4j
@Service
public class TaskLogSummaryServiceImpl implements TaskLogSummaryService {

    /** pipeline.log 行前缀的时间戳格式 [yyyy-MM-dd HH:mm:ss.SSS] */
    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /** 阶段开始行：[ts] >>> KEY — 描述 */
    private static final Pattern STAGE_BEGIN = Pattern.compile(
            "^\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\] >>> (PULLING_CODE|PARSING_CODE|SPLITTING_TASK|AI_ANALYZING|MODULE_HIERARCHY|MODULE_HIERARCHY_REVIEW|GENERATING_DOC)\\b.*$"
    );

    /** 阶段耗时行（首尾缩进 4 空格）：[ts]     耗时 Nms */
    private static final Pattern STAGE_DURATION = Pattern.compile(
            "^\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\]\\s+耗时\\s+(\\d+)ms.*$"
    );

    /** 流水线完成 / 暂停 / 异常 终止行 */
    private static final Pattern STAGE_END_OK = Pattern.compile(
            "^\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\] <<< (流水线完成 → PENDING_REVIEW|暂停 — 等待人工复核模块层级).*$"
    );
    private static final Pattern STAGE_END_ERROR = Pattern.compile(
            "^\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\] !!! 流水线异常:\\s*(.+)$"
    );

    /** 逐切片/逐模块进度行（由后端补写）：[ts]   [chunk i/N] path#method type=METHOD */
    private static final Pattern CHUNK_PROGRESS = Pattern.compile(
            "^\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\]\\s+\\[chunk\\s+(\\d+)/(\\d+)\\].*$"
    );
    private static final Pattern MODULE_PROGRESS = Pattern.compile(
            "^\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\]\\s+\\[module\\s+(\\d+)/(\\d+)\\].*$"
    );

    /** 阶段中文展示名（与前端 statusMeta 保持一致） */
    private static final Map<String, String> STAGE_LABEL = new LinkedHashMap<>();
    static {
        STAGE_LABEL.put("PULLING_CODE", "拉取代码");
        STAGE_LABEL.put("PARSING_CODE", "静态解析");
        STAGE_LABEL.put("SPLITTING_TASK", "代码切片");
        STAGE_LABEL.put("ENTRYPOINT_REVIEW", "入口复核");
        STAGE_LABEL.put("AI_ANALYZING", "AI 分析");
        STAGE_LABEL.put("MODULE_HIERARCHY", "模块层级提炼");
        STAGE_LABEL.put("MODULE_HIERARCHY_REVIEW", "模块层级复核");
        STAGE_LABEL.put("GENERATING_DOC", "生成文档");
    }

    @Autowired
    private DecompileTaskMapper decompileTaskMapper;

    @Autowired
    private CodeChunkMapper codeChunkMapper;

    @Autowired
    private AiCallRecordMapper aiCallRecordMapper;

    @Autowired
    private CodeFileSnapshotMapper codeFileSnapshotMapper;

    @Autowired
    private ModuleHierarchyNodeMapper moduleHierarchyNodeMapper;

    @Autowired
    private TaskExecutionLogger taskExecutionLogger;

    @Value("${code-insight.storage.local-path:./storage}")
    private String storageBase;

    @Value("${code-insight.ai.mock:false}")
    private boolean aiMock;

    @Override
    public TaskLogSummaryDto summarize(Long taskId) {
        TaskLogSummaryDto dto = new TaskLogSummaryDto();
        dto.setTaskId(taskId);

        // 1. 顶层字段
        DecompileTask task = decompileTaskMapper.selectById(taskId);
        if (task == null) {
            dto.setStatus("UNKNOWN");
            dto.setProgress(0);
            dto.setDurationMs(0L);
            dto.setAiMock(aiMock);
            dto.setPipeline(emptyPipeline());
            dto.setCounters(emptyCounters());
            dto.setAiCalls(emptyAiCalls());
            dto.setCurrent(emptyCurrent());
            return dto;
        }
        dto.setStatus(task.getStatus());
        dto.setProgress(task.getProgress() == null ? 0 : task.getProgress());
        dto.setDurationMs(task.getDurationMs() == null ? 0L : task.getDurationMs());
        // 运行中任务实时耗时：当 endedAt 为空且 startedAt 不为空时，按当前时间计算实时耗时
        if (task.getEndedAt() == null && task.getStartedAt() != null) {
            long realDuration = java.time.Duration.between(task.getStartedAt(), LocalDateTime.now()).toMillis();
            if (realDuration > dto.getDurationMs()) {
                dto.setDurationMs(realDuration);
            }
        }
        dto.setStartedAt(task.getStartedAt());
        dto.setEndedAt(task.getEndedAt());
        dto.setModelName(task.getModelName());
        dto.setAiMock(aiMock);

        // 2. counters: 切片计数
        TaskLogSummaryDto.Counters counters = new TaskLogSummaryDto.Counters();
        Map<String, Integer> byType = new HashMap<>();
        for (String t : new String[]{"FILE", "CLASS", "METHOD", "DIFF"}) {
            byType.put(t, 0);
        }
        List<Map<String, Object>> rows = codeChunkMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<CodeChunk>()
                        .select("chunk_type, status, count(*) cnt")
                        .eq("task_id", taskId)
                        .groupBy("chunk_type", "status")
        );
        int totalChunks = 0;
        int analyzed = 0;
        int failed = 0;
        int pending = 0;
        for (Map<String, Object> r : rows) {
            String chunkType = String.valueOf(r.get("chunk_type"));
            String status = String.valueOf(r.get("status"));
            long cnt = ((Number) r.get("cnt")).longValue();
            byType.merge(chunkType, (int) cnt, Integer::sum);
            totalChunks += cnt;
            if ("ANALYZED".equals(status)) analyzed += cnt;
            else if ("FAILED".equals(status)) failed += cnt;
            else pending += cnt;
        }
        counters.setChunksByType(byType);
        counters.setTotalChunks(totalChunks);
        counters.setChunksAnalyzed(analyzed);
        counters.setChunksFailed(failed);
        counters.setChunksPending(pending);

        // 3. counters: 文件快照计数
        Long totalFiles = codeFileSnapshotMapper.selectCount(
                new LambdaQueryWrapper<CodeFileSnapshot>().eq(CodeFileSnapshot::getTaskId, taskId)
        );
        counters.setTotalFiles(totalFiles == null ? 0 : totalFiles.intValue());
        dto.setCounters(counters);

        // 4. aiCalls — 按 call_stage 分两组：MODULE_HIERARCHY（含 AI_ANALYZING）和 GENERATING_DOC
        java.util.function.Function<String, TaskLogSummaryDto.AiCalls> countByStage = (stage) -> {
            TaskLogSummaryDto.AiCalls c = new TaskLogSummaryDto.AiCalls();
            LambdaQueryWrapper<AiCallRecord> totalW = new LambdaQueryWrapper<AiCallRecord>()
                    .eq(AiCallRecord::getTaskId, taskId)
                    .eq(AiCallRecord::getCallStage, stage);
            Long total = aiCallRecordMapper.selectCount(totalW);
            Long ok = aiCallRecordMapper.selectCount(
                    new LambdaQueryWrapper<AiCallRecord>()
                            .eq(AiCallRecord::getTaskId, taskId)
                            .eq(AiCallRecord::getCallStage, stage)
                            .eq(AiCallRecord::getIsSuccess, 1)
            );
            c.setTotal(total == null ? 0 : total.intValue());
            c.setSuccess(ok == null ? 0 : ok.intValue());
            c.setFailed((total == null ? 0 : total.intValue()) - (ok == null ? 0 : ok.intValue()));
            return c;
        };
        // 全量聚合（保持兼容）
        TaskLogSummaryDto.AiCalls aiCalls = new TaskLogSummaryDto.AiCalls();
        Long aiTotal = aiCallRecordMapper.selectCount(
                new LambdaQueryWrapper<AiCallRecord>().eq(AiCallRecord::getTaskId, taskId)
        );
        Long aiOk = aiCallRecordMapper.selectCount(
                new LambdaQueryWrapper<AiCallRecord>()
                        .eq(AiCallRecord::getTaskId, taskId)
                        .eq(AiCallRecord::getIsSuccess, 1)
        );
        aiCalls.setTotal(aiTotal == null ? 0 : aiTotal.intValue());
        aiCalls.setSuccess(aiOk == null ? 0 : aiOk.intValue());
        aiCalls.setFailed(aiTotal == null ? 0 : (aiTotal.intValue() - (aiOk == null ? 0 : aiOk.intValue())));
        dto.setAiCalls(aiCalls);
        // 分组统计
        dto.setHierarchyAiCalls(countByStage.apply("MODULE_HIERARCHY"));
        dto.setDocAiCalls(countByStage.apply("MODULE_DOC"));

        // 5. current: 模块数
        TaskLogSummaryDto.Current current = new TaskLogSummaryDto.Current();
        current.setTotalFiles(counters.getTotalFiles());
        current.setTotalChunks(totalChunks);
        Long moduleTotal = moduleHierarchyNodeMapper.selectCount(
                new LambdaQueryWrapper<ModuleHierarchyNode>()
                        .eq(ModuleHierarchyNode::getTaskId, taskId)
                        .eq(ModuleHierarchyNode::getLevel, "MODULE")
        );
        current.setModuleTotal(moduleTotal == null ? 0 : moduleTotal.intValue());
        current.setFileIndex(-1);
        current.setChunkIndex(-1);
        current.setModuleIndex(-1);
        dto.setCurrent(current);

        // 6. 解析 pipeline.log（仅最近一次流水线运行）
        String logContent = taskExecutionLogger.readLastRunContent(taskId);
        if (logContent != null && !logContent.isBlank()) {
            try {
                PipelineParseResult parsed = parsePipelineLog(logContent, STAGE_LABEL);
                dto.setPipeline(parsed.stages);
                if (parsed.lastError != null && TaskStatus.FAILED.name().equals(task.getStatus())) {
                    dto.setLastError(truncate(parsed.lastError, 200));
                }
                if (parsed.chunkIndex >= 0) {
                    current.setChunkIndex(parsed.chunkIndex);
                }
                if (parsed.moduleIndex >= 0) {
                    current.setModuleIndex(parsed.moduleIndex);
                }
            } catch (Exception e) {
                log.warn("解析 pipeline.log 失败 taskId={}: {}", taskId, e.getMessage());
                dto.setPipeline(emptyPipeline());
            }
        } else {
            dto.setPipeline(emptyPipeline());
        }

        // 7. fileIndex 兜底：若 chunkIndex 已知则取其与文件数-1 的较小值
        if (current.getChunkIndex() >= 0 && counters.getTotalFiles() > 0) {
            current.setFileIndex(Math.min(current.getChunkIndex(), counters.getTotalFiles() - 1));
        }

        return dto;
    }

    /**
     * 把缺失字段填齐，便于前端在 pipeline.log 尚未生成时也能渲染占位。
     */
    private List<PipelineStageStatDto> emptyPipeline() {
        List<PipelineStageStatDto> list = new ArrayList<>();
        for (Map.Entry<String, String> e : STAGE_LABEL.entrySet()) {
            PipelineStageStatDto s = new PipelineStageStatDto();
            s.setKey(e.getKey());
            s.setLabel(e.getValue());
            s.setStatus("pending");
            s.setDurationMs(0L);
            list.add(s);
        }
        return list;
    }

    private TaskLogSummaryDto.Counters emptyCounters() {
        TaskLogSummaryDto.Counters c = new TaskLogSummaryDto.Counters();
        c.setTotalFiles(0);
        c.setTotalChunks(0);
        Map<String, Integer> byType = new HashMap<>();
        for (String t : new String[]{"FILE", "CLASS", "METHOD", "DIFF"}) {
            byType.put(t, 0);
        }
        c.setChunksByType(byType);
        c.setChunksAnalyzed(0);
        c.setChunksFailed(0);
        c.setChunksPending(0);
        return c;
    }

    private TaskLogSummaryDto.AiCalls emptyAiCalls() {
        TaskLogSummaryDto.AiCalls a = new TaskLogSummaryDto.AiCalls();
        a.setTotal(0);
        a.setSuccess(0);
        a.setFailed(0);
        return a;
    }

    private TaskLogSummaryDto.Current emptyCurrent() {
        TaskLogSummaryDto.Current c = new TaskLogSummaryDto.Current();
        c.setFileIndex(-1);
        c.setTotalFiles(0);
        c.setChunkIndex(-1);
        c.setTotalChunks(0);
        c.setModuleIndex(-1);
        c.setModuleTotal(0);
        return c;
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** pipeline.log 解析结果 */
    private static class PipelineParseResult {
        List<PipelineStageStatDto> stages = new ArrayList<>();
        String lastError;
        int chunkIndex = -1;
        int moduleIndex = -1;
    }

    /**
     * 单趟扫描 pipeline.log，按阶段开始/结束行构建阶段列表，并提取 lastError / 当前进度。
     */
    private PipelineParseResult parsePipelineLog(String content, Map<String, String> labelMap) {
        PipelineParseResult result = new PipelineParseResult();
        Map<String, PipelineStageStatDto> started = new LinkedHashMap<>();
        // 保留声明顺序的阶段列表起始
        for (Map.Entry<String, String> e : labelMap.entrySet()) {
            PipelineStageStatDto s = new PipelineStageStatDto();
            s.setKey(e.getKey());
            s.setLabel(e.getValue());
            s.setStatus("pending");
            s.setDurationMs(0L);
            started.put(e.getKey(), s);
            result.stages.add(s);
        }

        String[] lines = content.split("\\r?\\n");
        PipelineStageStatDto active = null;
        long activeStartedAtMs = 0L;
        boolean terminated = false;

        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();

            Matcher begin = STAGE_BEGIN.matcher(raw);
            if (begin.find()) {
                String key = begin.group(2);
                long tsMs = parseTs(begin.group(1));
                // 阶段切换：先将上一阶段标记为 done（避免所有历史阶段都卡在 running 状态）
                if (active != null && "running".equals(active.getStatus())) {
                    active.setStatus("done");
                }
                PipelineStageStatDto s = started.get(key);
                if (s != null) {
                    s.setStartedAt(toLocalDateTime(begin.group(1)));
                    if (!"done".equals(s.getStatus()) && !"skipped".equals(s.getStatus())) {
                        s.setStatus("running");
                    }
                    active = s;
                    activeStartedAtMs = tsMs;
                }
                continue;
            }

            if (active != null) {
                Matcher dur = STAGE_DURATION.matcher(raw);
                if (dur.find()) {
                    long ms = Long.parseLong(dur.group(1));
                    active.setDurationMs(ms);
                    if (active.getStartedAt() != null) {
                        active.setEndedAt(toLocalDateTime(dur.group(1)));
                    }
                }
            }

            Matcher endOk = STAGE_END_OK.matcher(raw);
            if (endOk.find()) {
                if (active != null && "running".equals(active.getStatus())) {
                    active.setStatus("done");
                    Matcher dur = STAGE_DURATION.matcher(raw);
                    if (dur.find()) {
                        active.setDurationMs(Long.parseLong(dur.group(1)));
                    }
                }
                terminated = true;
                break;
            }

            Matcher endErr = STAGE_END_ERROR.matcher(raw);
            if (endErr.find()) {
                if (active != null && "running".equals(active.getStatus())) {
                    active.setStatus("error");
                }
                result.lastError = endErr.group(1).trim();
                terminated = true;
                break;
            }

            Matcher chunk = CHUNK_PROGRESS.matcher(raw);
            if (chunk.find()) {
                int idx = Integer.parseInt(chunk.group(1));
                if (idx > result.chunkIndex) {
                    result.chunkIndex = idx;
                }
                continue;
            }
            Matcher mod = MODULE_PROGRESS.matcher(raw);
            if (mod.find()) {
                int idx = Integer.parseInt(mod.group(1));
                if (idx > result.moduleIndex) {
                    result.moduleIndex = idx;
                }
            }
        }

        // 若整篇日志已遍历仍未遇到终止行，但任务已不在 runningStatuses，
        // 我们无法在此判断，状态保持为 running 即可。
        if (!terminated && active != null && "running".equals(active.getStatus())) {
            // 不强行收尾，由前端根据 task.status 自行判断
        }
        return result;
    }

    private long parseTs(String ts) {
        try {
            return LocalDateTime.parse(ts, TS_FMT).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return 0L;
        }
    }

    private LocalDateTime toLocalDateTime(String ts) {
        try {
            return LocalDateTime.parse(ts, TS_FMT);
        } catch (Exception e) {
            return null;
        }
    }
}