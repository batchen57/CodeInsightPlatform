package com.company.codeinsight.modules.schedule.cron;

import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Cron 表达式解析与下次触发时间计算工具。
 * <p>底层复用 Spring 的 {@link CronExpression}（支持 6 位：秒 分 时 日 月 周）。</p>
 */
public final class CronExpressionParser {

    private CronExpressionParser() {}

    /**
     * 校验 cron 表达式是否合法。
     */
    public static boolean isValid(String cron) {
        if (cron == null || cron.isBlank()) return false;
        try {
            CronExpression.parse(cron);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 计算指定时间之后的下一次触发时刻（按给定时区）。
     *
     * @param cron     Spring 6 位 cron 表达式
     * @param timezone 时区字符串（如 Asia/Shanghai），为 null 时使用系统默认
     * @param from     基准时间
     * @return 下一次触发时刻；若表达式非法返回 null
     */
    public static LocalDateTime nextAfter(String cron, String timezone, LocalDateTime from) {
        if (!isValid(cron)) return null;
        ZoneId zone = (timezone == null || timezone.isBlank())
                ? ZoneId.systemDefault()
                : ZoneId.of(timezone);
        ZonedDateTime z = from.atZone(zone);
        CronExpression exp = CronExpression.parse(cron);
        ZonedDateTime next = exp.next(z);
        return next == null ? null : next.toLocalDateTime();
    }
}