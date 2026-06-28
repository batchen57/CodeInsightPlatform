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
  /** 多状态过滤（与 status 互斥，AXIOS 会自动序列化为 ?statuses=A&statuses=B） */
  statuses?: string[];
  type?: string;
}): Promise<PageResult<Task>> => {
  return request.get('/tasks', { params });
};

/**
 * 任务中心顶部状态分组 chips 数据：
 * - ALL：所有任务
 * - RUNNING：进行中（PENDING / PULLING_CODE / ... / PUSHING）
 * - PENDING_REVIEW：待复核 + 复核中
 * - CONFIRMED：已确认 + 已推送
 * - CLOSED：已终止（FAILED / CANCELLED / ARCHIVED / DRAFT）
 */
export interface TaskStatusSummary {
  ALL: number;
  RUNNING: number;
  PENDING_REVIEW: number;
  CONFIRMED: number;
  CLOSED: number;
}

export const getTaskSummary = (params: { systemId?: number } = {}): Promise<TaskStatusSummary> => {
  return request.get('/tasks/summary', { params });
};

/**
 * 全局新建任务前置条件查询的响应体：
 * - ready=true：可以新建任务
 * - ready=false：blockingDrafts 列出所有非终态草稿，前端弹窗引导复核人去处理
 */
export interface BlockingDraft {
  draftId: number;
  moduleName: string;
  status: string;
  workspaceId: number;
  taskId?: number;
  systemId?: number;
  repositoryId?: number;
  updatedAt: string;
}

export interface RepositoryReadiness {
  ready: boolean;
  unconfirmedCount: number;
  blockingDrafts: BlockingDraft[];
}

/**
 * 全局新建任务前置条件查询：
 * 任何 ci_knowledge_draft 仍处于 DRAFT / EDITING / REJECTED 时即返回 ready=false。
 * 后端对应接口：GET /api/drafts/readiness。
 */
export const getRepositoryReadiness = (): Promise<RepositoryReadiness> => {
  return request.get('/drafts/readiness');
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

/**
 * 任务级「确认通过」：整组草稿置 CONFIRMED，工作区升 COMPLETED，任务升 CONFIRMED。
 * 这是复核工作区工具栏「确认通过」按钮的真实语义入口 —
 * 操作粒度是任务，不是单文件。
 *
 * 后端对应接口：POST /api/tasks/{id}/confirm
 */
export const confirmTask = (id: number, author?: string, comment?: string): Promise<void> => {
  return request.post(`/tasks/${id}/confirm`, { author, comment });
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
