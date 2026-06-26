import request from './request';
import type { PageResult, Task } from '../types';

export interface TaskProgress {
  status: string;
  progress: number;
  errorReason?: string;
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

export const createInitialTask = (data: {
  systemId: number;
  repositoryId: number;
  promptVersion?: number;
  modelName?: string;
}): Promise<Task> => {
  return request.post('/tasks/initial', data);
};

export const createIncrementalTask = (data: {
  systemId: number;
  repositoryId: number;
  promptVersion?: number;
  modelName?: string;
}): Promise<Task> => {
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
