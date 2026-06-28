import request from './request';
import type { LoginRequest, LoginResponse } from '../types';
import { useAuthStore } from '../stores/auth';

/**
 * 兜底操作人：当未登录或会话无 username 时使用，避免业务链路报空指针。
 */
export const DEFAULT_OPERATOR = 'Admin';

export const login = (data: LoginRequest): Promise<LoginResponse> => {
  return request.post('/auth/login', data);
};

/**
 * 获取当前请求线程的操作人。
 * 优先使用登录会话中的 username，未登录时回退到 DEFAULT_OPERATOR。
 */
export function getCurrentOperator(): string {
  return useAuthStore.getState().session?.username ?? DEFAULT_OPERATOR;
}
