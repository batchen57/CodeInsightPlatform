import type { AiModel } from '../../types';

export const getPreferredModel = (models: AiModel[]): AiModel | undefined => {
  const availableModels = models.filter((model) => model.status !== 0 && model.hasApiKey);
  return availableModels.find((model) => model.isDefault === 'true') ?? availableModels[0] ?? models.find((model) => model.status !== 0) ?? models[0];
};

export const getModelOptionDisabled = (model: AiModel): boolean => {
  return model.status === 0 || !model.hasApiKey;
};

export const formatDateTime = (value?: string): string => {
  return value ? new Date(value).toLocaleString() : '-';
};
