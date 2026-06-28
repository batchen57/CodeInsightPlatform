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

export const createInitialTask = (data: CreateTaskPayload): Promise<Task> => {
  return request.post('/tasks/initial', data);
};

export const createIncrementalTask = (data: CreateTaskPayload): Promise<Task> => {
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
