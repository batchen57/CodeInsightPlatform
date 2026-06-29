package com.company.codeinsight.modules.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.company.codeinsight.common.exception.BusinessException;
import com.company.codeinsight.modules.entrypoint.model.EntryPointConfigCodec;
import com.company.codeinsight.modules.log.service.OperationLogService;
import com.company.codeinsight.modules.schedule.cron.CronExpressionParser;
import com.company.codeinsight.modules.schedule.dto.ScheduleTaskCreateDto;
import com.company.codeinsight.modules.schedule.dto.ScheduleTaskPageQuery;
import com.company.codeinsight.modules.schedule.dto.ScheduleTaskUpdateDto;
import com.company.codeinsight.modules.schedule.entity.ScheduleFireRecord;
import com.company.codeinsight.modules.schedule.entity.ScheduleTask;
import com.company.codeinsight.modules.schedule.enums.FireStatus;
import com.company.codeinsight.modules.schedule.enums.FireStrategy;
import com.company.codeinsight.modules.schedule.enums.OverlapStrategy;
import com.company.codeinsight.modules.schedule.mapper.ScheduleFireRecordMapper;
import com.company.codeinsight.modules.schedule.mapper.ScheduleTaskMapper;
import com.company.codeinsight.modules.schedule.service.ScheduleTaskService;
import com.company.codeinsight.modules.task.entity.DecompileTask;
import com.company.codeinsight.modules.task.service.DecompileTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 定时任务调度配置服务实现。
 *
 * <p>核心职责：</p>
 * <ol>
 *   <li>配置 CRUD：校验 cron 合法性，计算并持久化 next_fire_at</li>
 *   <li>由 {@code ScheduleExecutor} 周期调用 {@link #tryFire(Long)}；
 *       内部用 Redis SETNX 做分布式锁防重入，再按 overlap_strategy 路由：
 *       SKIP / QUEUE / PARALLEL</li>
 *   <li>每次触发均复用 {@code DecompileTaskService} 创建一条 ci_task 记录并启动流水线</li>
 *   <li>记录每次触发到 ci_schedule_fire_record</li>
 * </ol>
 */
@Slf4j
@Service
public class ScheduleTaskServiceImpl extends ServiceImpl<ScheduleTaskMapper, ScheduleTask>
        implements ScheduleTaskService {

    /** Redis 锁 key 前缀：schedule:lock:{id}，TTL 由配置决定 */
    private static final String LOCK_KEY_PREFIX = "code-insight:schedule:lock:";
    /** Redis QUEUE 列表 key：等待上一次任务结束后再触发 */
    private static final String QUEUE_KEY = "code-insight:schedule:queue";

    @Autowired
    private ScheduleFireRecordMapper fireRecordMapper;

    @Autowired
    @Lazy
    private DecompileTaskService decompileTaskService;

    @Autowired
    private OperationLogService operationLogService;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Value("${code-insight.schedule.lock-ttl-seconds:30}")
    private long lockTtlSeconds;

    // ============== CRUD ==============

    @Override
    public Page<ScheduleTask> pageQuery(ScheduleTaskPageQuery query) {
        Page<ScheduleTask> page = new Page<>(query.getCurrent(), query.getSize());
        // 把可空 Boolean 缓存到局部变量，避免三元表达式在 null 上自动拆箱抛 NPE
        Boolean enabled = query.getEnabled();
        LambdaQueryWrapper<ScheduleTask> qw = new LambdaQueryWrapper<>();
        qw.eq(query.getSystemId() != null, ScheduleTask::getSystemId, query.getSystemId())
                .eq(query.getRepositoryId() != null, ScheduleTask::getRepositoryId, query.getRepositoryId())
                .eq(enabled != null, ScheduleTask::getEnabled, Boolean.TRUE.equals(enabled) ? 1 : 0)
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(ScheduleTask::getName, query.getKeyword())
                        .or().like(ScheduleTask::getDescription, query.getKeyword()))
                .orderByDesc(ScheduleTask::getUpdatedAt);
        return this.page(page, qw);
    }

    @Override
    @Transactional
    public ScheduleTask createConfig(ScheduleTaskCreateDto dto) {
        validateConfigBasic(dto.getSystemId(), dto.getRepositoryId(), dto.getCronExpression());
        ScheduleTask s = new ScheduleTask();
        applyDto(s, dto);
        s.setTotalFired(0);
        s.setTotalSuccess(0);
        s.setTotalFailed(0);
        s.setTotalSkipped(0);
        s.setNextFireAt(CronExpressionParser.nextAfter(
                s.getCronExpression(), s.getTimezone(), LocalDateTime.now()));
        this.save(s);
        operationLogService.logOperation(s.getSystemId(), null, "SCHEDULE_CREATE",
                "创建定时任务：" + s.getName() + "，cron=" + s.getCronExpression(), null, true);
        return s;
    }

    @Override
    @Transactional
    public ScheduleTask updateConfig(Long id, ScheduleTaskUpdateDto dto) {
        ScheduleTask exist = this.getById(id);
        if (exist == null) throw new BusinessException("定时任务不存在");
        if (StringUtils.hasText(dto.getCronExpression())
                && !CronExpressionParser.isValid(dto.getCronExpression())) {
            throw new BusinessException("cron 表达式不合法");
        }
        applyUpdateDto(exist, dto);
        // cron 或时区变化时重算 next_fire_at
        if (StringUtils.hasText(dto.getCronExpression()) || StringUtils.hasText(dto.getTimezone())) {
            exist.setNextFireAt(CronExpressionParser.nextAfter(
                    exist.getCronExpression(), exist.getTimezone(), LocalDateTime.now()));
        }
        this.updateById(exist);
        operationLogService.logOperation(exist.getSystemId(), null, "SCHEDULE_UPDATE",
                "更新定时任务：" + exist.getName(), null, true);
        return exist;
    }

    @Override
    public void enable(Long id) {
        ScheduleTask s = getOrThrow(id);
        s.setEnabled(1);
        s.setNextFireAt(CronExpressionParser.nextAfter(
                s.getCronExpression(), s.getTimezone(), LocalDateTime.now()));
        this.updateById(s);
        operationLogService.logOperation(s.getSystemId(), null, "SCHEDULE_ENABLE",
                "启用定时任务：" + s.getName(), null, true);
    }

    @Override
    public void disable(Long id) {
        ScheduleTask s = getOrThrow(id);
        s.setEnabled(0);
        // 禁用时清空 next_fire_at，调度器不会再拉取
        s.setNextFireAt(null);
        this.updateById(s);
        operationLogService.logOperation(s.getSystemId(), null, "SCHEDULE_DISABLE",
                "禁用定时任务：" + s.getName(), null, true);
    }

    @Override
    @Transactional
    public void softDelete(Long id) {
        ScheduleTask s = getOrThrow(id);
        this.removeById(id);
        operationLogService.logOperation(s.getSystemId(), null, "SCHEDULE_DELETE",
                "删除定时任务：" + s.getName(), null, true);
    }

    @Override
    public List<ScheduleTask> findDueSchedules() {
        LambdaQueryWrapper<ScheduleTask> qw = new LambdaQueryWrapper<>();
        qw.eq(ScheduleTask::getEnabled, 1)
                .isNotNull(ScheduleTask::getNextFireAt)
                .le(ScheduleTask::getNextFireAt, LocalDateTime.now());
        return this.list(qw);
    }

    // ============== 触发调度 ==============

    /**
     * 调度器入口：加分布式锁 → 检查冲突 → 路由 SKIP / QUEUE / PARALLEL → fireNow。
     */
    @Override
    public ScheduleFireRecord tryFire(Long scheduleId) {
        String lockKey = LOCK_KEY_PREFIX + scheduleId;
        boolean acquired = tryLock(lockKey);
        if (!acquired) {
            // 已被其他实例抢到，本轮放弃
            log.debug("调度锁被其他节点持有，跳过 scheduleId={}", scheduleId);
            return null;
        }
        try {
            ScheduleTask s = this.getById(scheduleId);
            if (s == null || Integer.valueOf(0).equals(s.getEnabled())) {
                return null;
            }
            boolean hasRunning = hasRunningTaskForSchedule(s);
            LocalDateTime planned = s.getNextFireAt() != null ? s.getNextFireAt() : LocalDateTime.now();
            LocalDateTime now = LocalDateTime.now();

            if (!hasRunning) {
                // 无冲突：直接 fireNow
                return fireNow(s, planned, now);
            }
            // 有冲突：按 overlap_strategy 路由
            OverlapStrategy strategy = parseOverlap(s.getOverlapStrategy());
            switch (strategy) {
                case SKIP:
                    return recordSkipped(s, planned, now, "上一次任务尚未结束（SKIP 策略）");
                case QUEUE:
                    enqueueQueue(scheduleId);
                    return recordQueued(s, planned, now);
                case PARALLEL:
                default:
                    return fireNow(s, planned, now);
            }
        } catch (Exception ex) {
            log.error("调度触发异常 scheduleId={}", scheduleId, ex);
            recordFailedFire(scheduleId, ex);
            throw ex;
        } finally {
            releaseLock(lockKey);
        }
    }

    /**
     * 真正的触发：复用 DecompileTaskService 创建任务并启动流水线。
     */
    private ScheduleFireRecord fireNow(ScheduleTask s, LocalDateTime planned, LocalDateTime now) {
        try {
            String triggerSource = "SCHEDULED";
            String fireStrategyStr = parseFireStrategy(s.getFireStrategy()).name();
            DecompileTask task;
            if (FireStrategy.INITIAL == parseFireStrategy(s.getFireStrategy())) {
                task = decompileTaskService.createInitialTask(s.getSystemId(), s.getRepositoryId(),
                        s.getModularizePromptId(), s.getDocumentPromptId(),
                        s.getModelName(),
                        EntryPointConfigCodec.decode(s.getEntryScanConfig()),
                        s.getRequireHierarchyReview() != null && s.getRequireHierarchyReview() == 1,
                        s.getRequireEntrypointReview() == null || s.getRequireEntrypointReview() == 1,
                        triggerSource, s.getId());
            } else {
                task = decompileTaskService.createIncrementalTask(s.getSystemId(), s.getRepositoryId(),
                        s.getModularizePromptId(), s.getDocumentPromptId(),
                        s.getModelName(),
                        EntryPointConfigCodec.decode(s.getEntryScanConfig()),
                        s.getRequireHierarchyReview() != null && s.getRequireHierarchyReview() == 1,
                        s.getRequireEntrypointReview() == null || s.getRequireEntrypointReview() == 1,
                        triggerSource, s.getId());
            }
            // 立刻启动流水线
            decompileTaskService.startTask(task.getId());

            // 写 fire_record
            ScheduleFireRecord rec = new ScheduleFireRecord();
            rec.setScheduleId(s.getId());
            rec.setTaskId(task.getId());
            rec.setFireTime(now);
            rec.setPlannedTime(planned);
            rec.setStatus(FireStatus.CREATED.name());
            fireRecordMapper.insert(rec);

            // 更新调度配置统计与 next_fire_at
            s.setLastFiredAt(now);
            s.setLastTaskId(task.getId());
            s.setLastStatus(FireStatus.CREATED.name());
            s.setTotalFired(s.getTotalFired() == null ? 1 : s.getTotalFired() + 1);
            s.setNextFireAt(CronExpressionParser.nextAfter(
                    s.getCronExpression(), s.getTimezone(), now));
            this.updateById(s);

            operationLogService.logOperation(s.getSystemId(), task.getId(), "SCHEDULE_FIRE",
                    "定时任务触发：" + s.getName() + " → ci_task.id=" + task.getId() + "（" + fireStrategyStr + "）",
                    null, true);
            return rec;
        } catch (Exception ex) {
            log.error("定时触发失败 scheduleId={}", s.getId(), ex);
            // 写失败 fire_record 并更新调度统计
            ScheduleFireRecord rec = new ScheduleFireRecord();
            rec.setScheduleId(s.getId());
            rec.setFireTime(now);
            rec.setPlannedTime(planned);
            rec.setStatus(FireStatus.FAILED.name());
            rec.setErrorMessage(ex.getMessage());
            fireRecordMapper.insert(rec);

            s.setLastFiredAt(now);
            s.setLastStatus(FireStatus.FAILED.name());
            s.setTotalFired(s.getTotalFired() == null ? 1 : s.getTotalFired() + 1);
            s.setTotalFailed(s.getTotalFailed() == null ? 1 : s.getTotalFailed() + 1);
            s.setNextFireAt(CronExpressionParser.nextAfter(
                    s.getCronExpression(), s.getTimezone(), now));
            this.updateById(s);

            operationLogService.logOperation(s.getSystemId(), null, "SCHEDULE_FIRE_FAIL",
                    "定时任务触发失败：" + s.getName() + "，" + ex.getMessage(),
                    ex.getMessage(), false);
            return rec;
        }
    }

    private ScheduleFireRecord recordSkipped(ScheduleTask s, LocalDateTime planned, LocalDateTime now, String reason) {
        ScheduleFireRecord rec = new ScheduleFireRecord();
        rec.setScheduleId(s.getId());
        rec.setFireTime(now);
        rec.setPlannedTime(planned);
        rec.setStatus(FireStatus.SKIPPED.name());
        rec.setSkipReason(reason);
        fireRecordMapper.insert(rec);

        s.setLastFiredAt(now);
        s.setLastStatus(FireStatus.SKIPPED.name());
        s.setTotalSkipped(s.getTotalSkipped() == null ? 1 : s.getTotalSkipped() + 1);
        s.setNextFireAt(CronExpressionParser.nextAfter(
                s.getCronExpression(), s.getTimezone(), now));
        this.updateById(s);
        log.info("定时任务 SKIPPED scheduleId={} reason={}", s.getId(), reason);
        return rec;
    }

    private ScheduleFireRecord recordQueued(ScheduleTask s, LocalDateTime planned, LocalDateTime now) {
        ScheduleFireRecord rec = new ScheduleFireRecord();
        rec.setScheduleId(s.getId());
        rec.setFireTime(now);
        rec.setPlannedTime(planned);
        rec.setStatus(FireStatus.QUEUED.name());
        rec.setSkipReason("上一次任务尚未结束（QUEUE 策略，已加入排队）");
        fireRecordMapper.insert(rec);

        s.setLastFiredAt(now);
        s.setLastStatus(FireStatus.QUEUED.name());
        s.setNextFireAt(CronExpressionParser.nextAfter(
                s.getCronExpression(), s.getTimezone(), now));
        this.updateById(s);
        return rec;
    }

    private void recordFailedFire(Long scheduleId, Exception ex) {
        try {
            ScheduleFireRecord rec = new ScheduleFireRecord();
            rec.setScheduleId(scheduleId);
            rec.setFireTime(LocalDateTime.now());
            rec.setPlannedTime(LocalDateTime.now());
            rec.setStatus(FireStatus.FAILED.name());
            rec.setErrorMessage(ex.getMessage());
            fireRecordMapper.insert(rec);
        } catch (Exception ignore) {
            // 记录失败也不要影响主流程
        }
    }

    /**
     * 检查调度配置上一次触发的任务是否仍在运行中。
     * 仅当 last_task_id 对应的 ci_task 仍处于 RUNNING 状态分组时返回 true。
     */
    private boolean hasRunningTaskForSchedule(ScheduleTask s) {
        if (s.getLastTaskId() == null) return false;
        DecompileTask last = decompileTaskService.getById(s.getLastTaskId());
        if (last == null) return false;
        String st = last.getStatus();
        if (st == null) return false;
        // RUNNING 状态分组：PENDING / PULLING_CODE / PARSING_CODE / SPLITTING_TASK / ENTRYPOINT_REVIEW /
        // AI_ANALYZING / MODULE_HIERARCHY / MODULE_HIERARCHY_REVIEW / GENERATING_DOC / PUSHING
        return switch (st) {
            case "PENDING", "PULLING_CODE", "PARSING_CODE", "SPLITTING_TASK", "ENTRYPOINT_REVIEW",
                 "AI_ANALYZING", "MODULE_HIERARCHY", "MODULE_HIERARCHY_REVIEW",
                 "GENERATING_DOC", "PUSHING" -> true;
            default -> false;
        };
    }

    // ============== 手动立即触发 ==============

    @Override
    public ScheduleFireRecord triggerNow(Long scheduleId) {
        ScheduleTask s = getOrThrow(scheduleId);
        if (Integer.valueOf(0).equals(s.getEnabled())) {
            throw new BusinessException("定时任务已禁用，无法手动触发");
        }
        String lockKey = LOCK_KEY_PREFIX + scheduleId;
        boolean acquired = tryLock(lockKey);
        if (!acquired) {
            throw new BusinessException("已有其他触发正在进行，请稍后重试");
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            return fireNow(s, now, now);
        } finally {
            releaseLock(lockKey);
        }
    }

    // ============== 队列（QUEUE 策略） ==============

    @Override
    public Long popQueuedScheduleId() {
        if (redisTemplate == null) return null;
        String v = redisTemplate.opsForList().rightPop(QUEUE_KEY);
        return v == null ? null : Long.valueOf(v);
    }

    private void enqueueQueue(Long scheduleId) {
        if (redisTemplate == null) {
            log.warn("RedisTemplate 不可用，QUEUE 策略降级为 SKIP scheduleId={}", scheduleId);
            return;
        }
        redisTemplate.opsForList().leftPush(QUEUE_KEY, String.valueOf(scheduleId));
    }

    // ============== 触发历史 ==============

    @Override
    public Page<ScheduleFireRecord> listFireRecords(Long scheduleId, int current, int size) {
        Page<ScheduleFireRecord> page = new Page<>(current, size);
        LambdaQueryWrapper<ScheduleFireRecord> qw = new LambdaQueryWrapper<>();
        qw.eq(ScheduleFireRecord::getScheduleId, scheduleId)
                .orderByDesc(ScheduleFireRecord::getFireTime);
        return fireRecordMapper.selectPage(page, qw);
    }

    // ============== helpers ==============

    private void validateConfigBasic(Long systemId, Long repositoryId, String cron) {
        if (systemId == null || repositoryId == null) {
            throw new BusinessException("系统和代码库必填");
        }
        if (!CronExpressionParser.isValid(cron)) {
            throw new BusinessException("cron 表达式不合法: " + cron);
        }
    }

    private void applyDto(ScheduleTask s, ScheduleTaskCreateDto dto) {
        s.setSystemId(dto.getSystemId());
        s.setRepositoryId(dto.getRepositoryId());
        s.setName(dto.getName());
        s.setDescription(dto.getDescription());
        s.setCronExpression(dto.getCronExpression());
        s.setTimezone(StringUtils.hasText(dto.getTimezone()) ? dto.getTimezone() : "Asia/Shanghai");
        s.setEnabled(dto.getEnabled() == null ? 1 : (dto.getEnabled() ? 1 : 0));
        s.setFireStrategy(parseFireStrategy(dto.getFireStrategy()).name());
        s.setOverlapStrategy(parseOverlap(dto.getOverlapStrategy()).name());
        s.setModularizePromptId(dto.getModularizePromptId());
        s.setDocumentPromptId(dto.getDocumentPromptId());
        s.setModelName(dto.getModelName());
        s.setEntryScanConfig(EntryPointConfigCodec.encode(dto.getEntryScanConfig()));
        s.setRequireHierarchyReview(dto.getRequireHierarchyReview() == null
                || dto.getRequireHierarchyReview() ? 1 : 0);
        s.setRequireEntrypointReview(dto.getRequireEntrypointReview() == null
                || dto.getRequireEntrypointReview() ? 1 : 0);
    }

    private void applyUpdateDto(ScheduleTask s, ScheduleTaskUpdateDto dto) {
        if (StringUtils.hasText(dto.getName())) s.setName(dto.getName());
        if (dto.getDescription() != null) s.setDescription(dto.getDescription());
        if (StringUtils.hasText(dto.getCronExpression())) s.setCronExpression(dto.getCronExpression());
        if (StringUtils.hasText(dto.getTimezone())) s.setTimezone(dto.getTimezone());
        if (dto.getEnabled() != null) s.setEnabled(dto.getEnabled() ? 1 : 0);
        if (StringUtils.hasText(dto.getFireStrategy())) s.setFireStrategy(parseFireStrategy(dto.getFireStrategy()).name());
        if (StringUtils.hasText(dto.getOverlapStrategy())) s.setOverlapStrategy(parseOverlap(dto.getOverlapStrategy()).name());
        if (dto.getModularizePromptId() != null) s.setModularizePromptId(dto.getModularizePromptId());
        if (dto.getDocumentPromptId() != null) s.setDocumentPromptId(dto.getDocumentPromptId());
        if (dto.getModelName() != null) s.setModelName(dto.getModelName());
        if (dto.getEntryScanConfig() != null) s.setEntryScanConfig(EntryPointConfigCodec.encode(dto.getEntryScanConfig()));
        if (dto.getRequireHierarchyReview() != null) s.setRequireHierarchyReview(dto.getRequireHierarchyReview() ? 1 : 0);
        if (dto.getRequireEntrypointReview() != null) s.setRequireEntrypointReview(dto.getRequireEntrypointReview() ? 1 : 0);
    }

    private static FireStrategy parseFireStrategy(String s) {
        if (s == null) return FireStrategy.INCREMENTAL;
        try {
            return FireStrategy.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FireStrategy.INCREMENTAL;
        }
    }

    private static OverlapStrategy parseOverlap(String s) {
        if (s == null) return OverlapStrategy.SKIP;
        try {
            return OverlapStrategy.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OverlapStrategy.SKIP;
        }
    }

    private ScheduleTask getOrThrow(Long id) {
        ScheduleTask s = this.getById(id);
        if (s == null) throw new BusinessException("定时任务不存在");
        return s;
    }

    private boolean tryLock(String key) {
        if (redisTemplate == null) {
            // 没有 Redis 时降级为本地内存锁（仅单机有效）
            return LocalLocks.tryLock(key);
        }
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", lockTtlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    private void releaseLock(String key) {
        if (redisTemplate == null) {
            LocalLocks.release(key);
            return;
        }
        redisTemplate.delete(key);
    }

    /**
     * 无 Redis 时的本地内存锁降级方案。
     * 通过 TTL 自然过期防止死锁；多实例部署需配置 Redis 才能保证严格一致性。
     */
    private static final class LocalLocks {
        private static final java.util.Map<String, Long> HOLDER = new java.util.concurrent.ConcurrentHashMap<>();
        private static final long TTL_MS = Duration.ofSeconds(30).toMillis();

        static boolean tryLock(String key) {
            long now = System.currentTimeMillis();
            Long exp = HOLDER.get(key);
            if (exp == null || exp < now) {
                HOLDER.put(key, now + TTL_MS);
                return true;
            }
            return false;
        }

        static void release(String key) {
            HOLDER.remove(key);
        }
    }
}