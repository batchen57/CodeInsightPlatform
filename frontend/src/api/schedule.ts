import request from './request';
import type {
  EntryScanConfig,
  FireStrategy,
  OverlapStrategy,
  PageResult,
  ScheduleFireRecord,
  ScheduleTask,
  TriggerNowResult,
} from '../types';

/** 调度配置分页查询参数 */
export interface ScheduleQueryParams {
  current: number;
  size: number;
  systemId?: number;
  repositoryId?: number;
  enabled?: boolean;
  keyword?: string;
}

/** 新增调度入参 */
export interface ScheduleCreatePayload {
  systemId: number;
  repositoryId: number;
  name: string;
  description?: string;
  cronExpression: string;
  timezone?: string;
  enabled?: boolean;
  fireStrategy?: FireStrategy;
  overlapStrategy?: OverlapStrategy;
  modularizePromptId?: number;
  documentPromptId?: number;
  modelName?: string;
  entryScanConfig?: EntryScanConfig;
  requireHierarchyReview?: boolean;
}

/** 更新调度入参（字段均可选） */
export interface ScheduleUpdatePayload {
  name?: string;
  description?: string;
  cronExpression?: string;
  timezone?: string;
  enabled?: boolean;
  fireStrategy?: FireStrategy;
  overlapStrategy?: OverlapStrategy;
  modularizePromptId?: number;
  documentPromptId?: number;
  modelName?: string;
  entryScanConfig?: EntryScanConfig;
  requireHierarchyReview?: boolean;
}

/** 分页查询调度配置 */
export const listSchedules = (params: ScheduleQueryParams): Promise<PageResult<ScheduleTask>> => {
  return request.get('/schedules', { params });
};

/** 调度详情 */
export const getSchedule = (id: number): Promise<ScheduleTask> => {
  return request.get(`/schedules/${id}`);
};

/** 新增调度配置 */
export const createSchedule = (payload: ScheduleCreatePayload): Promise<ScheduleTask> => {
  return request.post('/schedules', payload);
};

/** 更新调度配置 */
export const updateSchedule = (id: number, payload: ScheduleUpdatePayload): Promise<ScheduleTask> => {
  return request.put(`/schedules/${id}`, payload);
};

/** 删除调度配置 */
export const deleteSchedule = (id: number): Promise<void> => {
  return request.delete(`/schedules/${id}`);
};

/** 启用 */
export const enableSchedule = (id: number): Promise<void> => {
  return request.post(`/schedules/${id}/enable`);
};

/** 禁用 */
export const disableSchedule = (id: number): Promise<void> => {
  return request.post(`/schedules/${id}/disable`);
};

/** 立即触发一次（不依赖 cron） */
export const triggerScheduleNow = (id: number): Promise<TriggerNowResult> => {
  return request.post(`/schedules/${id}/trigger`);
};

/** 触发历史分页 */
export const listFireRecords = (
  scheduleId: number,
  params: { current: number; size: number },
): Promise<PageResult<ScheduleFireRecord>> => {
  return request.get(`/schedules/${scheduleId}/fire-records`, { params });
};

/** 该定时任务已触发的知识构建任务分页（force scheduleId） */
export const listScheduleTasks = (
  scheduleId: number,
  params: {
    current: number;
    size: number;
    status?: string;
    type?: string;
    statuses?: string[];
  },
): Promise<PageResult<import('../types').Task>> => {
  return request.get(`/schedules/${scheduleId}/tasks`, { params });
};