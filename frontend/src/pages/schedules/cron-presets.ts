/**
 * 常用 cron 预设值（Spring 6 位：秒 分 时 日 月 周）。
 */
export const cronPresets: { label: string; value: string }[] = [
  { label: '每分钟', value: '0 * * * * *' },
  { label: '每 5 分钟', value: '0 */5 * * * *' },
  { label: '每 15 分钟', value: '0 */15 * * * *' },
  { label: '每 30 分钟', value: '0 */30 * * * *' },
  { label: '每小时整点', value: '0 0 * * * *' },
  { label: '每天凌晨 2 点', value: '0 0 2 * * *' },
  { label: '每天早上 9 点', value: '0 0 9 * * *' },
  { label: '每周一凌晨 3 点', value: '0 0 3 ? * MON' },
  { label: '每月 1 号凌晨 4 点', value: '0 0 4 1 * *' },
];

/**
 * 简易 cron 表达式校验（6 位：秒 分 时 日 月 周）。
 * 仅做结构与字段数检查，复杂的具体语义合法性交由后端 CronExpression 解析。
 */
export function isValidCron(cron: string): boolean {
  if (!cron || typeof cron !== 'string') return false;
  const parts = cron.trim().split(/\s+/);
  if (parts.length !== 6 && parts.length !== 7) return false;
  // 6 位每位都允许：数字 / * / 范围 / 步进 / 枚举（逗号分隔）/ ? / 字母（W/L/#）
  const fieldRegex = /^[\d*/,\-?#LW]+$/i;
  return parts.every((p) => fieldRegex.test(p));
}