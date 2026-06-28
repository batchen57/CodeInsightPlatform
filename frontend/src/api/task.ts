import request from './request';
import type { EntryScanConfig, ModuleHierarchy, PageResult, Task, TaskLogSummary } from '../types';

export interface TaskProgress {
  status: string;
  progress: number;
  errorReason?: string;
}

export interface CreateTaskPayload {
  systemId: number;
  repositoryId: number;
  /** 已废弃：请使用 modularizePromptVersion / documentPromptVersion */
  promptVersion?: number;
  /** 模块提取提示词版本（对应 ci_prompt.prompt_type=MODULARIZE），不传则后端兜底为默认版本 */
  modularizePromptVersion?: number;
  /** 文档生成提示词版本（对应 ci_prompt.prompt_type=DOCUMENT_GENERATION），不传则后端兜底为默认版本 */
  documentPromptVersion?: number;
  modelName?: string;
  /** 入口扫描配置（可选；不传则走默认 Controller/JOB/MQ 兜底） */
  entryScanConfig?: EntryScanConfig;
  /** 是否启用模块层级调试（人工复核断点）；不传则按默认 TRUE 处理 */
  requireHierarchyReview?: boolean;
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

/** 读取任务真实执行日志（pipeline 写入的 pipeline.log 文件内容） */
export const getTaskExecutionLog = (id: number): Promise<string> => {
  return request.get(`/tasks/${id}/log`);
};

/**
 * 读取任务执行日志的结构化摘要（阶段耗时、文件/切片计数、AI 成功失败数、Mock 标记、当前进度）。
 * 与 getTaskExecutionLog 互补：前者返回全文，后者返回聚合结构，供"执行日志"卡片快速展示。
 */
export const getTaskLogSummary = (id: number): Promise<TaskLogSummary> => {
  return request.get(`/tasks/${id}/log/summary`);
};

/** 拉取任务当前模块层级（人工复核断点用） */
export const getModuleHierarchy = (id: number): Promise<ModuleHierarchy> => {
  return request.get(`/tasks/${id}/module-hierarchy`);
};

/** 整体替换任务模块层级（人工复核断点提交用） */
export const replaceModuleHierarchy = (id: number, payload: ModuleHierarchy): Promise<ModuleHierarchy> => {
  return request.put(`/tasks/${id}/module-hierarchy`, payload);
};

/** 模块层级复核完成后恢复流水线 */
export const resumeModuleHierarchyReview = (id: number): Promise<void> => {
  return request.post(`/tasks/${id}/module-hierarchy/resume`);
};
