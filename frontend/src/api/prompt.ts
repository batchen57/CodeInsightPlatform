import request from './request';
import { useAuthStore } from '../stores/auth';
import type { PageResult, Prompt } from '../types';

export interface PromptTestResult {
  inputTokens: number;
  outputTokens: number;
  durationMs: number;
  result: string;
  errorReason?: string;
}

export interface PromptTestStreamEvent {
  type: 'content' | 'done' | 'error';
  content?: string;
  inputTokens?: number;
  outputTokens?: number;
  durationMs?: number;
  errorReason?: string;
}

export const listPrompts = (params: {
  current: number;
  size: number;
  name?: string;
  status?: number;
  /** MODULARIZE-模块提取 / DOCUMENT_GENERATION-文档生成 */
  promptType?: 'MODULARIZE' | 'DOCUMENT_GENERATION' | string;
}): Promise<PageResult<Prompt>> => {
  return request.get('/prompts', { params });
};

export const getPrompt = (id: number): Promise<Prompt> => {
  return request.get(`/prompts/${id}`);
};

export const createPrompt = (data: Partial<Prompt>): Promise<Prompt> => {
  return request.post('/prompts', data);
};

export const updatePrompt = (id: number, data: Partial<Prompt>): Promise<Prompt> => {
  return request.put(`/prompts/${id}`, data);
};

export const clonePrompt = (id: number): Promise<Prompt> => {
  return request.post(`/prompts/${id}/clone`);
};

export const changePromptStatus = (id: number, status: number): Promise<void> => {
  return request.put(`/prompts/${id}/status`, null, { params: { status } });
};

export const testRunPrompt = (id: number, sampleCode: string, modelId?: number): Promise<PromptTestResult> => {
  return request.post(`/prompts/${id}/test-run`, { sampleCode, modelId });
};

export const testRunPromptStream = async (
  id: number,
  sampleCode: string,
  modelId: number | undefined,
  onEvent: (event: PromptTestStreamEvent) => void,
  signal?: AbortSignal,
): Promise<void> => {
  const baseUrl = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '');
  const session = useAuthStore.getState().session;
  const response = await fetch(`${baseUrl}/prompts/${id}/test-run/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(session?.token ? { Authorization: `Bearer ${session.token}` } : {}),
    },
    body: JSON.stringify({ sampleCode, modelId }),
    signal,
  });

  if (!response.ok || !response.body) {
    throw new Error(`Stream request failed: ${response.status}`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split('\n');
    buffer = lines.pop() ?? '';

    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed) {
        continue;
      }
      onEvent(JSON.parse(trimmed) as PromptTestStreamEvent);
    }
  }

  const remaining = buffer.trim();
  if (remaining) {
    onEvent(JSON.parse(remaining) as PromptTestStreamEvent);
  }
};

export const deletePrompt = (id: number): Promise<void> => {
  return request.delete(`/prompts/${id}`);
};
