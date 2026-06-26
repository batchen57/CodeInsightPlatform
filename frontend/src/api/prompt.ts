import request from './request';
import type { PageResult, Prompt } from '../types';

export interface PromptTestResult {
  inputTokens: number;
  outputTokens: number;
  durationMs: number;
  result: string;
  errorReason?: string;
}

export const listPrompts = (params: {
  current: number;
  size: number;
  name?: string;
  status?: number;
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

export const deletePrompt = (id: number): Promise<void> => {
  return request.delete(`/prompts/${id}`);
};
