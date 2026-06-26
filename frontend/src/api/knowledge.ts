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
  confirmedBy: string;
  confirmedAt: string;
  pushedAt: string | null;
  createdAt: string;
}

export function createVersion(taskId: number, versionNum: string, confirmedBy?: string): Promise<KnowledgeVersion> {
  return request.post('/knowledge/version', null, {
    params: { taskId, versionNum, confirmedBy },
  });
}

export function pushVersion(versionId: number): Promise<void> {
  return request.post(`/knowledge/${versionId}/push`);
}

export function listVersions(params: {
  current: number;
  size: number;
  systemId?: number;
}): Promise<{ total: number; records: KnowledgeVersion[] }> {
  return request.get('/knowledge/page', { params });
}
