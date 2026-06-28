/**
 * 当前操作人身份工具。
 *
 * <p>本文件是 MVP 阶段的 placeholder：返回硬编码的 'Admin'，
 * 业务调用点统一通过 {@link getCurrentOperator} 读取，便于后续接入
 * JWT / Spring Security 用户体系时只替换函数实现，不动业务代码。</p>
 *
 * <p>接入真鉴权时只需替换为：</p>
 * <pre>{@code
 *   export function getCurrentOperator(): string {
 *     return sessionStorage.getItem('current-operator') ?? 'Admin';
 *   }
 * }</pre>
 *
 * <p>典型用法（自动保存 / 手动保存 / 确认通过 / 创建版本）：</p>
 * <pre>{@code
 *   await saveDraft(draftId, content, getCurrentOperator(), remark);
 * }</pre>
 */

/**
 * 兜底操作人：当未来未登录态命中此函数时使用，避免业务链路报空指针。
 */
export const DEFAULT_OPERATOR = 'Admin';

/**
 * 获取当前请求线程的操作人。
 *
 * <p>TODO：接入 JWT 时改为读取 sessionStorage / cookies / Authorization header。</p>
 *
 * @returns 操作人 username；当前固定返回 'Admin'
 */
export function getCurrentOperator(): string {
  return DEFAULT_OPERATOR;
}