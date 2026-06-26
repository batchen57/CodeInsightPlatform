import request from './request';

export interface DraftWorkspace {
  id: number;
  taskId: number;
  systemId: number;
  repositoryId: number;
  status: string;
  createdAt: string;
  updatedAt: string;
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

export interface DraftRevision {
  id: number;
  draftId: number;
  contentUri: string;
  author: string;
  remark: string;
  createdAt: string;
}

export interface DraftReviewComment {
  id: number;
  draftId: number;
  author: string;
  comment: string;
  createdAt: string;
}

export interface DraftSourceReference {
  id: number;
  draftId: number;
  filePath: string;
  startLine: number;
  endLine: number;
  createdAt: string;
}

export function getWorkspaceByTask(taskId: number): Promise<{ workspace: DraftWorkspace; drafts: KnowledgeDraft[] }> {
  return request.get(`/drafts/workspace/task/${taskId}`);
}

export function getDraftContent(draftId: number): Promise<string> {
  return request.get(`/drafts/${draftId}/content`);
}

export function saveDraft(draftId: number, content: string, author?: string, remark?: string): Promise<void> {
  return request.post(`/drafts/${draftId}/save`, null, {
    params: { content, author, remark },
  });
}

export function autoSaveDraft(draftId: number, content: string, author?: string): Promise<void> {
  return request.post(`/drafts/${draftId}/autosave`, null, {
    params: { content, author },
  });
}

export function confirmDraft(draftId: number, author?: string): Promise<void> {
  return request.post(`/drafts/${draftId}/confirm`, null, {
    params: { author },
  });
}

export function rejectDraft(draftId: number, comment: string, author?: string): Promise<void> {
  return request.post(`/drafts/${draftId}/reject`, null, {
    params: { comment, author },
  });
}

export function getRevisions(draftId: number): Promise<DraftRevision[]> {
  return request.get(`/drafts/${draftId}/revisions`);
}

export function getComments(draftId: number): Promise<DraftReviewComment[]> {
  return request.get(`/drafts/${draftId}/comments`);
}

export function getReferences(draftId: number): Promise<DraftSourceReference[]> {
  return request.get(`/drafts/${draftId}/references`);
}
