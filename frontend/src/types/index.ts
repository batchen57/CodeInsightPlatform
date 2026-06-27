export interface System {
  id: number;
  name: string;
  description: string;
  owner: string;
  status: number; // 0-停用, 1-启用
  createdAt: string;
  updatedAt: string;
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
  createdAt: string;
  updatedAt: string;
}

export interface Task {
  id: number;
  systemId: number;
  repositoryId: number;
  promptVersion?: number;
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
  createdAt: string;
  updatedAt: string;
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

export interface AiModel {
  id: number;
  name: string;
  identifier: string;
  provider: string;
  apiKey?: string;
  baseUrl?: string;
  isDefault: 'true' | 'false';
  capabilities?: string;
  description?: string;
  sortOrder: number;
  createdAt?: string;
  updatedAt?: string;
}

