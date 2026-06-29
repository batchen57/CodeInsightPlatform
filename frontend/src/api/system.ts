import request from './request';
import type { PageResult, System, SystemState } from '../types';

export const listSystems = (params: {
  current: number;
  size: number;
  name?: string;
  owner?: string;
  status?: number;
  state?: SystemState;
}): Promise<PageResult<System>> => {
  return request.get('/systems', { params });
};

export const getSystem = (id: number): Promise<System> => {
  return request.get(`/systems/${id}`);
};

export const createSystem = (data: Partial<System>): Promise<System> => {
  return request.post('/systems', data);
};

export const updateSystem = (id: number, data: Partial<System>): Promise<System> => {
  return request.put(`/systems/${id}`, data);
};

/**
 * 旧启停切换：1=启用 / 0=停用
 * @deprecated 请改用 {@link changeSystemState}
 */
export const changeSystemStatus = (id: number, status: number): Promise<void> => {
  return request.put(`/systems/${id}/status`, null, { params: { status } });
};

/**
 * 通过状态机切换系统状态：仅支持 ACTIVE / DISABLED。
 * 后端其它目标态（DRAFT/REPO_CONFIGURED/SCAN_CONFIGURED/PROMPT_CONFIGURED）由业务自动推进。
 */
export const changeSystemState = (id: number, target: SystemState): Promise<void> => {
  return request.put(`/systems/${id}/state`, { target });
};

export const deleteSystem = (id: number): Promise<void> => {
  return request.delete(`/systems/${id}`);
};
