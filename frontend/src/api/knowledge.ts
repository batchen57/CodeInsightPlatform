import request from './request';

export interface KnowledgeVersion {
  id: number;
  systemId: number;
  repositoryId: number;
  taskId: number;
  versionNum: string;
  sourceBranch: string;
  sourceCommit: string;
  targetBranch: string;
  targetCommit: string | null;
  promptVersion: number;
  modelName: string;
  status: string;
  pushMethod: string;
  confirmedBy: string;
  confirmedAt: string;
  pushedAt: string | null;
  createdAt: string;
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

export function createVersion(taskId: number, versionNum: string, confirmedBy?: string): Promise<KnowledgeVersion> {
  return request.post('/knowledge/version', null, {
    params: { taskId, versionNum, confirmedBy },
  });
}

export function pushVersion(versionId: number, method: string = 'GIT'): Promise<void> {
  return request.post(`/knowledge/${versionId}/push`, null, {
    params: { method },
  });
}

export function listPushTasks(versionId: number): Promise<PushTask[]> {
  return request.get(`/push/version/${versionId}/tasks`);
}

export function listVersions(params: {
  current: number;
  size: number;
  systemId?: number;
}): Promise<{ total: number; records: KnowledgeVersion[] }> {
  return request.get('/knowledge/page', { params });
}
