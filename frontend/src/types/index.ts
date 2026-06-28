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
  createdAt: string;
  updatedAt: string;
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

