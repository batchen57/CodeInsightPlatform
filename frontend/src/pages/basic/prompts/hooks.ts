import { useCallback, useEffect, useState } from 'react';
import { listModels } from '../../../api/model';
import { listPrompts } from '../../../api/prompt';
import type { AiModel, Prompt } from '../../../types';
import type { PromptType } from './constants';
import { getPreferredModel } from './utils';

export const usePromptModels = () => {
  const [models, setModels] = useState<AiModel[]>([]);
  const [selectedModelId, setSelectedModelId] = useState<number | undefined>(undefined);

  const fetchModels = useCallback(async () => {
    try {
      const data = await listModels();
      setModels(data);
      const preferredModel = getPreferredModel(data);
      if (preferredModel) {
        setSelectedModelId(preferredModel.id);
      }
    } catch (e) {
      console.error('Failed to load models', e);
    }
  }, []);

  useEffect(() => {
    fetchModels();
  }, [fetchModels]);

  return {
    models,
    selectedModelId,
    setSelectedModelId,
  };
};

export const usePromptList = (
  activePromptType: PromptType,
  lifecycle?: 'DRAFT' | 'RELEASED' | 'ARCHIVED' | 'all',
) => {
  const [prompts, setPrompts] = useState<Prompt[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [searchName, setSearchName] = useState('');

  const fetchPrompts = useCallback(
    async (page = current, pageSize = size) => {
      setLoading(true);
      try {
        const data = await listPrompts({
          current: page,
          size: pageSize,
          name: searchName || undefined,
          promptType: activePromptType,
          lifecycle: lifecycle && lifecycle !== 'all' ? lifecycle : undefined,
        });
        setPrompts(data.records);
        setTotal(data.total);
      } finally {
        setLoading(false);
      }
    },
    [activePromptType, lifecycle, current, searchName, size],
  );

  useEffect(() => {
    fetchPrompts();
  }, [fetchPrompts]);

  const resetPage = useCallback(() => {
    setCurrent(1);
  }, []);

  return {
    prompts,
    total,
    loading,
    current,
    size,
    searchName,
    setCurrent,
    setSize,
    setSearchName,
    resetPage,
    fetchPrompts,
  };
};
