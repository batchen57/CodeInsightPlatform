export type SystemState =
  | 'DRAFT'
  | 'REPO_CONFIGURED'
  | 'SCAN_CONFIGURED'
  | 'PROMPT_CONFIGURED'
  | 'ACTIVE'
  | 'DISABLED';

export interface System {
  id: number;
  name: string;
  nameCn?: string;
  description: string;
  owner: string;
  status: number; // 0-停用, 1-启用（已废弃，请使用 state）
  /** 状态机：DRAFT / REPO_CONFIGURED / SCAN_CONFIGURED / PROMPT_CONFIGURED / ACTIVE / DISABLED */
  state?: SystemState;
  /** 系统级模块提取提示词 ID（FK → ci_prompt.id） */
  modularizePromptId?: number | null;
  /** 系统级文档生成提示词 ID（FK → ci_prompt.id） */
  documentPromptId?: number | null;
  createdAt: string;
  updatedAt: string;
  // 以下字段由 /systems 聚合接口返回，list 才有
  repositoryCount?: number;
  knowledgeVersionCount?: number;
  lastDecompileAt?: string;
}

export interface Repository {
  id: number;
  systemId: number;
  gitUrl: string;
  branch: string;
  username?: string;
  password?: string;
  scanRoot: string;
  excludeDirs?: string;
  excludeFileTypes?: string;
  lastCommitId?: string;
  lastDecompileAt?: string;
  /** 仓库级入口扫描配置，新建任务时默认带出，任务可单独覆盖 */
  entryScanConfig?: EntryScanConfig;
  createdAt: string;
  updatedAt: string;
}

export interface Prompt {
  id: number;
  name: string;
  content: string;
  version: number;
  status: number; // 0-禁用, 1-启用
  isDefault: number; // 0-否, 1-是
  /** 提示词用途：MODULARIZE-模块提取 / DOCUMENT_GENERATION-文档生成 */
  promptType?: 'MODULARIZE' | 'DOCUMENT_GENERATION' | string;
  /** 生命周期：DRAFT-草稿(可编辑) / RELEASED-已发布(锁定) / ARCHIVED-已归档 */
  lifecycle?: 'DRAFT' | 'RELEASED' | 'ARCHIVED' | string;
  createdAt: string;
  updatedAt: string;
}

export interface Task {
  id: number;
  systemId: number;
  repositoryId: number;
  /** 已废弃，请使用 modularizePromptVersion / documentPromptVersion */
  promptVersion?: number;
  /** 模块提取提示词版本（对应 ci_prompt.prompt_type=MODULARIZE） */
  modularizePromptVersion?: number;
  /** 文档生成提示词版本（对应 ci_prompt.prompt_type=DOCUMENT_GENERATION） */
  documentPromptVersion?: number;
  modelName?: string;
  status: string;
  type: 'INITIAL' | 'INCREMENTAL';
  progress: number;
  logUri?: string;
  errorReason?: string;
  durationMs: number;
  startedAt?: string;
  endedAt?: string;
  entryScanConfig?: EntryScanConfig;
  /** 是否启用模块层级调试（人工复核断点）；undefined 时按 TRUE 处理 */
  requireHierarchyReview?: boolean;
  /** 是否启用知识入口复核（人工复核断点，介于 SPLITTING_TASK 与 AI_ANALYZING 之间）；undefined 时按 TRUE 处理 */
  requireEntrypointReview?: boolean;
  /** 触发来源：MANUAL 手动触发 / SCHEDULED 定时调度触发 */
  triggerSource?: 'MANUAL' | 'SCHEDULED' | string;
  /** 触发该任务的调度配置 ID（triggerSource=SCHEDULED 时非空） */
  scheduleId?: number;
  /** 队列优先级 0-100，越大越优先；TaskQueueDispatcher 按此字段排序调度 */
  priority?: number;
  createdAt: string;
  updatedAt: string;
}

/**
 * 任务流水线中单个阶段的统计摘要（来自 GET /tasks/{id}/log/summary）。
 * 前端"执行日志"卡片渲染 Timeline 时使用。
 */
export interface PipelineStageStat {
  key: string;
  label: string;
  status: 'pending' | 'running' | 'done' | 'skipped' | 'error';
  durationMs: number;
  startedAt?: string;
  endedAt?: string;
}

/**
 * 任务执行日志的结构化摘要（来自 GET /tasks/{id}/log/summary）。
 * 同时驱动知识构建任务页的"执行日志"卡片与"查看完整日志"模态框顶栏。
 */
export interface TaskLogSummary {
  taskId: number;
  status: string;
  progress: number;
  durationMs: number;
  startedAt?: string;
  endedAt?: string;
  modelName?: string;
  /** 是否启用 AI 本地 Mock（来自后端 code-insight.ai.mock） */
  aiMock: boolean;
  pipeline: PipelineStageStat[];
  counters: {
    totalFiles: number;
    totalChunks: number;
    chunksByType: { FILE: number; CLASS: number; METHOD: number; DIFF: number };
    chunksAnalyzed: number;
    chunksFailed: number;
    chunksPending: number;
  };
  aiCalls: { total: number; success: number; failed: number };
  /** AI_ANALYZING / MODULE_HIERARCHY 阶段（第一段 AI）的调用统计 */
  hierarchyAiCalls?: { total: number; success: number; failed: number };
  /** GENERATING_DOC 阶段（第二段 AI）的调用统计 */
  docAiCalls?: { total: number; success: number; failed: number };
  /** 当前正在处理的进度索引；-1 表示未知 */
  current: {
    fileIndex: number;
    totalFiles: number;
    chunkIndex: number;
    totalChunks: number;
    moduleIndex: number;
    moduleTotal: number;
  };
  /** 失败原因的单行摘要（无堆栈），失败时用于"执行日志"卡片友好提示 */
  lastError?: string;
}

/**
 * 任务级入口扫描配置（仅在该任务创建时生效，不影响仓库）
 * include 规则"或"逻辑：任一列表非空即视为启用配置驱动，全部为空走默认 Controller/JOB/MQ 兜底
 * exclude 规则"或"逻辑：任一命中即从候选中排除
 */
export interface EntryScanConfig {
  /** 入口识别 - 注解（类的 annotations 含任一即匹配） */
  includeAnnotations?: string[];
  /** 入口识别 - 类路径 Ant 模式（FQ 与任一模式匹配即识别） */
  includeClasspaths?: string[];
  /** 入口识别 - 继承/实现（extendsClass 或 implementsList 含任一即识别） */
  includeExtends?: string[];
  /** 排除 - 类路径 Ant 模式 */
  excludeClasspaths?: string[];
  /** 排除 - 包路径（FQ 点分隔前缀匹配） */
  excludePackages?: string[];
  /** 排除 - 注解 */
  excludeAnnotations?: string[];
}

/** 模块层级（人工复核断点编辑对象），与后端 ModuleHierarchy DTO 对应 */
export interface ModuleHierarchy {
  taskId?: number;
  systemId?: number;
  modules?: Record<string, ModuleNode>;
}

/** 知识入口复核视图中的单个方法（只读展示用） */
export interface EntrypointMethodView {
  methodName: string;
  methodSignature?: string;
  annotation?: string;
  httpPath?: string;
  httpMethod?: string;
}

/** 知识入口复核视图中的单个入口类（只读展示用） */
export interface EntrypointReviewItem {
  id: number;
  taskId: number;
  systemId: number;
  className: string;
  filePath?: string;
  entryType?: string;
  annotation?: string;
  remark?: string;
  enabled: boolean;
  sortOrder: number;
  methods: EntrypointMethodView[];
}

export interface ModuleNode {
  id: string;
  moduleName: string;
  keywords?: string[];
  /** 人工逐项复核确认标记：true = 已确认，false/undefined = 未确认；JSON 中以 "Y"/"N" 字符串呈现 */
  confirmed?: boolean;
  subModules?: Record<string, SubModuleNode>;
}

export interface SubModuleNode {
  id: string;
  subModuleName: string;
  keywords?: string[];
  /** 人工逐项复核确认标记 */
  confirmed?: boolean;
  functions?: Record<string, FunctionNode>;
}

export interface FunctionNode {
  id: string;
  functionName: string;
  /** 入口类全限定名集合（仅在内存维护，不会写入提示词） */
  classPaths?: string[];
  /** 人工逐项复核确认标记 */
  confirmed?: boolean;
}

export interface PushTask {
  id: number;
  versionId: number;
  pushMethod: string;
  status: string;
  retryCount: number;
  maxRetries: number;
  targetInfo: string;
  errorMessage?: string;
  enqueuedAt: string;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
}

/** 知识查看 - 文件类型枚举（与后端 KnowledgeBrowseServiceImpl 常量对齐） */
export type KnowledgeBrowseFileType = 'DRAFT' | 'INDEX' | 'MANIFEST' | 'ALL';

/** 知识查看 - 单条文件视图（与后端 KnowledgeBrowseItem 对齐） */
export interface KnowledgeBrowseItem {
  /** 复合主键：draft:<id> / index:<taskId>:<path> / manifest:<taskId>:<path> */
  id: string;
  /** 文件名（不包含父路径） */
  name: string;
  /** DRAFT / INDEX / MANIFEST */
  type: KnowledgeBrowseFileType;
  taskId?: number;
  versionId?: number;
  versionNum?: string;
  /** 相对路径：draft = ci_knowledge_draft.filePath；index/manifest = docs/code-insight 下的相对路径 */
  filePath: string;
  /** 文件字节数 */
  size: number;
  /** DRAFT: DRAFT/EDITING/CONFIRMED/PUSHED/ARCHIVED；INDEX/MANIFEST: GENERATED */
  status: string;
  /** ISO timestamp */
  updatedAt: string;
  /** 数据源标识：DB（draft 行）/ TEMP_REPOS（index/manifest 文件） */
  source: 'DB' | 'TEMP_REPOS';
}

/** 知识查看 - 列表查询入参 */
export interface KnowledgeBrowseQuery {
  systemId: number;
  type?: KnowledgeBrowseFileType;
  keyword?: string;
  taskId?: number;
  versionId?: number;
  status?: string;
  createdAtStart?: string;
  createdAtEnd?: string;
}

export interface KnowledgeDraft {
  id: number;
  workspaceId: number;
  filePath: string;
  moduleName: string;
  contentUri: string;
  status: string;
  hash: string;
  createdAt: string;
  updatedAt: string;
}

export interface TokenUsageAudit {
  id: number;
  systemId: number;
  taskId: number;
  userId?: number;
  promptVersion?: number;
  modelName: string;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  cost: number;
  type: string;
  status: number;
  createdAt: string;
}

export interface OperationLog {
  id: number;
  systemId?: number;
  taskId?: number;
  userId?: number;
  username: string;
  actionType: string;
  detail: string;
  ipAddress?: string;
  exceptionMsg?: string;
  isSuccess: number;
  createdAt: string;
}

export interface PageResult<T> {
  total: number;
  size: number;
  current: number;
  records: T[];
}

export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

export interface LoginRequest {
  username: string;
  password: string;
  token: string;
}

export interface LoginResponse {
  token: string;
  username: string;
  displayName: string;
  role: string;
  expiresInSeconds: number;
}

export interface AiModel {
  id: number;
  name: string;
  identifier: string;
  provider: string;
  apiKey?: string;
  hasApiKey?: boolean;
  baseUrl?: string;
  isDefault: 'true' | 'false';
  capabilities?: string;
  description?: string;
  sortOrder: number;
  status?: number; // 0-停用 1-启用（未返回时按启用处理）
  createdAt?: string;
  updatedAt?: string;
}

export interface AiModelPreset {
  id: number;
  name: string;
  identifier: string;
  provider: string;
  baseUrl?: string;
  capabilities?: string;
  description?: string;
  sortOrder: number;
  status?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface AiModelMetricSummary {
  modelName: string;
  totalCalls: number;
  totalTokens: number;
  totalCost: number;
}

export interface AiModelMetricTrendPoint {
  date: string;
  calls: number;
  tokens: number;
  cost: number;
}

export interface AiModelTestResult {
  success: boolean;
  durationMs: number;
  message: string;
  responseSummary?: string;
}

/* ===========================================================
 * 定时任务调度（schedule）
 * =========================================================== */

/** 触发策略：INCREMENTAL 增量扫描 / INITIAL 全量扫描 */
export type FireStrategy = 'INCREMENTAL' | 'INITIAL';

/** 冲突策略：SKIP 上一次未结束则跳过 / QUEUE 排队等待 / PARALLEL 允许并发 */
export type OverlapStrategy = 'SKIP' | 'QUEUE' | 'PARALLEL';

/** 触发状态：CREATED / RUNNING / SUCCESS / FAILED / SKIPPED / QUEUED */
export type FireStatus =
  | 'CREATED'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'SKIPPED'
  | 'QUEUED'
  | string;

/**
 * 定时任务调度配置（对应 ci_schedule_task 表）
 */
export interface ScheduleTask {
  id: number;
  systemId: number;
  repositoryId: number;
  name: string;
  description?: string;
  /** Spring 6 位 cron 表达式：秒 分 时 日 月 周 */
  cronExpression: string;
  /** 时区，默认 Asia/Shanghai */
  timezone: string;
  /** 是否启用：0-禁用 1-启用 */
  enabled: number;
  fireStrategy: FireStrategy;
  overlapStrategy: OverlapStrategy;
  modularizePromptId?: number;
  documentPromptId?: number;
  modelName?: string;
  entryScanConfig?: EntryScanConfig;
  /** 是否启用模块层级调试断点：0-否 1-是 */
  requireHierarchyReview?: number;
  /** 是否启用知识入口复核断点：0-否 1-是；触发任务时复制到 ci_task */
  requireEntrypointReview?: number;
  lastFiredAt?: string;
  /** 最近一次触发产生的知识构建任务 ID */
  lastTaskId?: number;
  /** 最近一次触发状态 */
  lastStatus?: FireStatus;
  /** 下一次触发时间（cron 计算结果） */
  nextFireAt?: string;
  totalFired: number;
  totalSuccess: number;
  totalFailed: number;
  totalSkipped: number;
  createdBy?: number;
  createdAt: string;
  updatedAt: string;
}

/** 定时任务触发记录（对应 ci_schedule_fire_record 表） */
export interface ScheduleFireRecord {
  id: number;
  scheduleId: number;
  /** 本次触发创建的知识构建任务 ID（SKIPPED 时为空） */
  taskId?: number;
  fireTime: string;
  plannedTime: string;
  status: FireStatus;
  skipReason?: string;
  errorMessage?: string;
  durationMs?: number;
  createdAt: string;
}

/** 立即触发接口返回 */
export interface TriggerNowResult {
  scheduleId: number;
  taskId?: number;
  fireRecordId?: number;
  status?: FireStatus;
}

