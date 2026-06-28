import request from './request';
import type {
  AiModel,
  AiModelMetricSummary,
  AiModelMetricTrendPoint,
  AiModelPreset,
  AiModelTestResult,
} from '../types';

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

export const changeModelStatus = (id: number, status: number): Promise<void> => {
  return request.put(`/models/${id}/status`, null, { params: { status } });
};

export const testModelConnection = (id: number): Promise<AiModelTestResult> => {
  return request.post(`/models/${id}/test`);
};

export const listModelPresets = (): Promise<AiModelPreset[]> => {
  return request.get('/models/presets');
};

export const listAllModelPresets = (): Promise<AiModelPreset[]> => {
  return request.get('/models/presets/all');
};

export const createModelPreset = (data: Partial<AiModelPreset>): Promise<AiModelPreset> => {
  return request.post('/models/presets', data);
};

export const updateModelPreset = (
  id: number,
  data: Partial<AiModelPreset>,
): Promise<AiModelPreset> => {
  return request.put(`/models/presets/${id}`, data);
};

export const deleteModelPreset = (id: number): Promise<void> => {
  return request.delete(`/models/presets/${id}`);
};

export const changeModelPresetStatus = (id: number, status: number): Promise<void> => {
  return request.put(`/models/presets/${id}/status`, null, { params: { status } });
};

export const listModelMetricSummaries = (): Promise<AiModelMetricSummary[]> => {
  return request.get('/models/metrics/summary');
};

export const getModelMetricTrend = (
  id: number,
  days = 7,
): Promise<AiModelMetricTrendPoint[]> => {
  return request.get(`/models/${id}/metrics/trend`, { params: { days } });
};
