import request from './request';
import type { EntryScanConfig, PageResult, Task } from '../types';

export interface TaskProgress {
  status: string;
  progress: number;
  errorReason?: string;
}

export interface CreateTaskPayload {
  systemId: number;
  repositoryId: number;
  promptVersion?: number;
  modelName?: string;
  /** 入口扫描配置（可选；不传则走默认 Controller/JOB/MQ 兜底） */
  entryScanConfig?: EntryScanConfig;
}

export const listTasks = (params: {
  current: number;
  size: number;
  systemId?: number;
  status?: string;
  type?: string;
}): Promise<PageResult<Task>> => {
  return request.get('/tasks', { params });
};

export const getTask = (id: number): Promise<Task> => {
  return request.get(`/tasks/${id}`);
};

export const createInitialTask = (data: CreateTaskPayload): Promise<Task> => {
  return request.post('/tasks/initial', data);
};

export const createIncrementalTask = (data: CreateTaskPayload): Promise<Task> => {
  return request.post('/tasks/incremental', data);
};

export const startTask = (id: number): Promise<void> => {
  return request.post(`/tasks/${id}/start`);
};

export const terminateTask = (id: number): Promise<void> => {
  return request.post(`/tasks/${id}/terminate`);
};

export const retryTask = (id: number): Promise<void> => {
  return request.post(`/tasks/${id}/retry`);
};

export const getTaskProgress = (id: number): Promise<TaskProgress> => {
  return request.get(`/tasks/${id}/progress`);
};
