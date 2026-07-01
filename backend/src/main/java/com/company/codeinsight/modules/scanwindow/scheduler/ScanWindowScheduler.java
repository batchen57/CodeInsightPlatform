package com.company.codeinsight.modules.scanwindow.scheduler;

import com.company.codeinsight.modules.repository.entity.CodeRepository;
import com.company.codeinsight.modules.repository.mapper.CodeRepositoryMapper;
import com.company.codeinsight.modules.scanwindow.entity.ScanWindowEntity;
import com.company.codeinsight.modules.scanwindow.service.ScanWindowService;
import com.company.codeinsight.modules.task.service.DecompileTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * 定时扫描任务调度器：每分钟检查 ci_scan_window 中命中当前时间窗口的行，Redis 抢锁后下发任务。
 *
 * <p>集群幂等：每个窗口使用 Redis key <code>scan:fire:{repoId}:{yyyyMMddHHmm}</code>，
 * SETNX 成功的节点执行下发并更新 last_fired_at，其他节点跳过。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScanWindowScheduler {

    private final ScanWindowService scanWindowService;
    private final DecompileTaskService decompileTaskService;
    private final CodeRepositoryMapper repositoryMapper;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    private static final DateTimeFormatter SLOT_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    @Scheduled(cron = "0 0/1 * * * *")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        int todayBit = dayBit(now.getDayOfWeek());
        int hour = now.getHour();
        int minute = now.getMinute();
        String slot = now.format(SLOT_FMT);

        List<ScanWindowEntity> windows;
        try {
            windows = scanWindowService.listEnabled();
        } catch (Exception e) {
            log.warn("scan window query failed, skip tick", e);
            return;
        }
        if (windows.isEmpty()) return;

        int fired = 0;
        for (ScanWindowEntity w : windows) {
            // 1. 检查星期 + 小时 + 分钟是否命中
            if ((w.getWeekDays() & todayBit) == 0) continue;
            if (!w.getHour().equals(hour)) continue;
            if (!w.getMinute().equals(minute)) continue;

            // 2. 幂等检查（同一窗口只触发一次）
            String lockKey = "scan:fire:" + w.getRepositoryId() + ":" + slot;
            boolean acquired = lockKey != null && tryLock(lockKey);
            if (lockKey != null && !acquired) continue;

            // 3. 跳过已触发的窗口
            if (w.getLastFiredAt() != null) {
                LocalDateTime last = w.getLastFiredAt();
                if (last.getDayOfWeek() == now.getDayOfWeek()
                        && last.getHour() == hour
                        && last.getMinute() == minute) {
                    continue;
                }
            }

            // 4. 获取仓库信息
            CodeRepository repo = repositoryMapper.selectById(w.getRepositoryId());
            if (repo == null) {
                log.warn("scan window repo not found: repoId={}", w.getRepositoryId());
                continue;
            }

            // 5. 下发全量扫描任务（INITIAL）
            try {
                decompileTaskService.createInitialTask(
                        repo.getSystemId(), repo.getId(),
                        null, null, null, null,
                        true, true, "SCHEDULED");
                fired++;

                // 6. 更新 last_fired_at
                try {
                    ScanWindowEntity upd = new ScanWindowEntity();
                    upd.setRepositoryId(w.getRepositoryId());
                    upd.setWeekDays(w.getWeekDays());
                    upd.setHour(w.getHour());
                    upd.setMinute(w.getMinute());
                    upd.setEnabled(w.getEnabled());
                    upd.setLastFiredAt(now);
                    scanWindowService.upsert(upd);
                } catch (Exception e) {
                    log.warn("update last_fired_at failed repoId={}", w.getRepositoryId(), e);
                }
            } catch (Exception e) {
                log.error("scan fire failed repoId={} systemId={}", w.getRepositoryId(), repo.getSystemId(), e);
                // 失败时释放 lockKey 以便下一次 tick 重试
                if (redisTemplate != null) {
                    try { redisTemplate.delete(lockKey); } catch (Exception ignored) {}
                }
            }
        }
        if (fired > 0) {
            log.info("scan scheduler tick: {} windows fired", fired);
        }
    }

    private boolean tryLock(String key) {
        if (redisTemplate == null) return false;
        try {
            return Boolean.TRUE.equals(
                    redisTemplate.opsForValue().setIfAbsent(key, "1", java.time.Duration.ofMinutes(2)));
        } catch (Exception e) {
            log.warn("redis lock failed for key={}", key, e);
            return false;
        }
    }

    /** 星期转位掩码 */
    static int dayBit(DayOfWeek d) {
        return 1 << (d.getValue() - 1); // DayOfWeek.MONDAY=1 → bit0, SUNDAY=7 → bit6
    }
}
