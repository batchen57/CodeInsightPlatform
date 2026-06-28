package com.company.codeinsight.modules.task.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 任务执行日志写入器
 * 将 pipeline 各阶段的关键事件写入 {storageBase}/task_{taskId}/pipeline.log，
 * 供前端实时查看执行过程。
 */
@Slf4j
@Component
public class TaskExecutionLogger {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    @Value("${code-insight.storage.local-path:./storage}")
    private String storageBase;

    /**
     * 追加一条带时间戳的日志行
     */
    public void log(Long taskId, String message) {
        if (taskId == null) return;
        String timestamp = LocalDateTime.now().format(TS_FMT);
        String line = String.format("[%s] %s%n", timestamp, message);
        try {
            File dir = new File(storageBase, "task_" + taskId);
            if (!dir.exists()) dir.mkdirs();
            try (PrintWriter pw = new PrintWriter(new FileWriter(new File(dir, "pipeline.log"), true))) {
                pw.append(line);
                pw.flush();
            }
        } catch (IOException e) {
            log.warn("写入执行日志失败 taskId={}: {}", taskId, e.getMessage());
        }
    }

    /** 阶段开始 */
    public void logStage(Long taskId, String stage) {
        log(taskId, ">>> 进入阶段: " + stage);
    }

    /** 阶段产出 */
    public void logResult(Long taskId, String key, Object value) {
        log(taskId, "    " + key + ": " + value);
    }

    /** 异常 */
    public void logError(Long taskId, String message, Throwable e) {
        log(taskId, "!!! " + message + (e != null ? " — " + e.getMessage() : ""));
    }
}