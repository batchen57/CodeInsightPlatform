import request from './request';
import type { PageResult, Repository } from '../types';

export const listRepositories = (params: {
  current: number;
  size: number;
  systemId?: number;
  gitUrl?: string;
}): Promise<PageResult<Repository>> => {
  return request.get('/repositories', { params });
};

export const getRepository = (id: number): Promise<Repository> => {
  return request.get(`/repositories/${id}`);
};

export const createRepository = (data: Partial<Repository>): Promise<Repository> => {
  return request.post('/repositories', data);
};

export const updateRepository = (id: number, data: Partial<Repository>): Promise<Repository> => {
  return request.put(`/repositories/${id}`, data);
};

export const testRepositoryConnection = (data: Partial<Repository>): Promise<boolean> => {
  return request.post('/repositories/test-connection', data);
};

export const testSavedRepositoryConnection = (id: number): Promise<boolean> => {
  return request.post(`/repositories/${id}/test-connection`);
};
