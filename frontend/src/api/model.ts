import request from './request';
import type { AiModel } from '../types';

export const listModels = (): Promise<AiModel[]> => {
  return request.get('/models');
};

export const getModelDetail = (id: number): Promise<AiModel> => {
  return request.get(`/models/${id}`);
};

export const createModel = (data: Partial<AiModel>): Promise<AiModel> => {
  return request.post('/models', data);
};

export const updateModel = (id: number, data: Partial<AiModel>): Promise<AiModel> => {
  return request.put(`/models/${id}`, data);
};

export const deleteModel = (id: number): Promise<void> => {
  return request.delete(`/models/${id}`);
};
