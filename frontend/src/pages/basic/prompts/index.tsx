import type React from 'react';
import { useCallback, useMemo, useState } from 'react';
import { Col, Form, Row, message } from 'antd';
import {
  changePromptStatus,
  clonePrompt,
  createPrompt,
  deletePrompt,
  testRunPrompt,
  testRunPromptStream,
  updatePrompt,
  type PromptTestResult,
  type PromptTestStreamEvent,
} from '../../../api/prompt';
import type { Prompt } from '../../../types';
import { DEFAULT_PROMPTS, DEFAULT_SAMPLE_CODE, PROMPT_TYPE_TABS, type PromptType } from './constants';
import { createPromptColumns } from './columns';
import { usePromptList, usePromptModels } from './hooks';
import PromptFormModal from './PromptFormModal';
import PromptLibraryCard from './PromptLibraryCard';
import PromptTrialModal from './PromptTrialModal';
import './prompts.css';

const createEmptyTrialResult = (): PromptTestResult => ({
  inputTokens: 0,
  outputTokens: 0,
  durationMs: 0,
  result: '',
});

const Prompts: React.FC = () => {
  const [activePromptType, setActivePromptType] = useState<PromptType>('MODULARIZE');
  const {
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
  } = usePromptList(activePromptType);
  const { models, selectedModelId, setSelectedModelId } = usePromptModels();

  const [promptModalOpen, setPromptModalOpen] = useState(false);
  const [editingPrompt, setEditingPrompt] = useState<Prompt | null>(null);
  const [promptForm] = Form.useForm();

  const [selectedPrompt, setSelectedPrompt] = useState<Prompt | null>(null);
  const [sampleCode, setSampleCode] = useState(DEFAULT_SAMPLE_CODE);
  const [trialRunning, setTrialRunning] = useState(false);
  const [trialResult, setTrialResult] = useState<PromptTestResult | null>(null);
  const [trialModalOpen, setTrialModalOpen] = useState(false);

  const activePromptTypeMeta = PROMPT_TYPE_TABS.find((item) => item.key === activePromptType) ?? PROMPT_TYPE_TABS[0];

  const openTrialModal = useCallback((prompt: Prompt) => {
    setSelectedPrompt(prompt);
    setTrialResult(null);
    setTrialModalOpen(true);
  }, []);

  const handleSearch = useCallback(() => {
    resetPage();
    fetchPrompts(1);
  }, [fetchPrompts, resetPage]);

  const handleReset = useCallback(() => {
    setSearchName('');
    resetPage();
    setTimeout(() => fetchPrompts(1), 0);
  }, [fetchPrompts, resetPage, setSearchName]);

  const handlePromptTypeChange = useCallback(
    (key: string) => {
      setActivePromptType(key as PromptType);
      resetPage();
      setSelectedPrompt(null);
      setTrialResult(null);
      setTrialModalOpen(false);
    },
    [resetPage],
  );

  const openPromptModal = useCallback(
    (prompt: Prompt | null = null) => {
      setEditingPrompt(prompt);
      if (prompt) {
        promptForm.setFieldsValue({
          name: prompt.name,
          content: prompt.content,
          isDefault: prompt.isDefault === 1,
        });
      } else {
        promptForm.resetFields();
        promptForm.setFieldsValue({ content: DEFAULT_PROMPTS[activePromptType], isDefault: false });
      }
      setPromptModalOpen(true);
    },
    [activePromptType, promptForm],
  );

  const handlePromptSubmit = useCallback(async () => {
    const values = await promptForm.validateFields();
    const payload = {
      ...values,
      isDefault: values.isDefault ? 1 : 0,
      promptType: editingPrompt?.promptType ?? activePromptType,
    };
    if (editingPrompt) {
      await updatePrompt(editingPrompt.id, payload);
      message.success('提示词已更新并生成新版本');
    } else {
      await createPrompt(payload);
      message.success('提示词已创建');
    }
    setPromptModalOpen(false);
    fetchPrompts();
  }, [activePromptType, editingPrompt, fetchPrompts, promptForm]);

  const handleStatusChange = useCallback(
    async (checked: boolean, prompt: Prompt) => {
      await changePromptStatus(prompt.id, checked ? 1 : 0);
      message.success(`${prompt.name} 已${checked ? '启用' : '停用'}`);
      fetchPrompts();
    },
    [fetchPrompts],
  );

  const handleClone = useCallback(
    async (prompt: Prompt) => {
      await clonePrompt(prompt.id);
      message.success(`已复制 ${prompt.name}`);
      fetchPrompts();
    },
    [fetchPrompts],
  );

  const handleDelete = useCallback(
    async (id: number) => {
      try {
        await deletePrompt(id);
        message.success('提示词模板已删除');
        if (selectedPrompt?.id === id) {
          setSelectedPrompt(null);
        }
        fetchPrompts();
      } catch (e) {
        console.error('Failed to delete prompt', e);
      }
    },
    [fetchPrompts, selectedPrompt],
  );

  const handleTrialRun = useCallback(
    async (params: {
      sampleCode: string;
      variables: Record<string, string>;
      resolvedContent: string;
    }) => {
      if (!selectedPrompt) {
        message.warning('请先选择一个提示词再试跑');
        return;
      }

      const { sampleCode: sc, resolvedContent } = params;
      // 若已替换占位符,则把 resolvedContent 透传给后端,后端不再做二次替换
      // 否则把 sampleCode 留在 body 供后端自动解析 class/method
      const body = resolvedContent
        ? { sampleCode: sc, resolvedContent }
        : { sampleCode: sc };

      setTrialRunning(true);
      setTrialResult(createEmptyTrialResult());

      let streamedText = '';
      let receivedStreamEvent = false;
      let streamError: string | undefined;

      const updateStreamingResult = (event: PromptTestStreamEvent) => {
        receivedStreamEvent = true;

        if (event.type === 'content' && event.content) {
          streamedText += event.content;
          setTrialResult((previous) => ({
            inputTokens: previous?.inputTokens ?? 0,
            outputTokens: previous?.outputTokens ?? 0,
            durationMs: previous?.durationMs ?? 0,
            result: streamedText,
          }));
          return;
        }

        if (event.type === 'done') {
          setTrialResult({
            inputTokens: event.inputTokens ?? 0,
            outputTokens: event.outputTokens ?? Math.max(1, Math.ceil(streamedText.length / 4)),
            durationMs: event.durationMs ?? 0,
            result: streamedText,
          });
          return;
        }

        if (event.type === 'error') {
          streamError = event.errorReason || '流式试跑失败';
          setTrialResult({
            inputTokens: event.inputTokens ?? 0,
            outputTokens: event.outputTokens ?? 0,
            durationMs: event.durationMs ?? 0,
            result: streamedText,
            errorReason: streamError,
          });
        }
      };

      try {
        await testRunPromptStream(
          selectedPrompt.id,
          body.sampleCode,
          selectedModelId,
          updateStreamingResult,
          undefined,
          body.resolvedContent,
        );
        if (streamError) {
          message.error('试跑失败');
        } else {
          message.success('试跑完成');
        }
      } catch (error) {
        if (receivedStreamEvent) {
          setTrialResult((previous) => ({
            inputTokens: previous?.inputTokens ?? 0,
            outputTokens: previous?.outputTokens ?? 0,
            durationMs: previous?.durationMs ?? 0,
            result: previous?.result ?? streamedText,
            errorReason: error instanceof Error ? error.message : '流式试跑中断',
          }));
          message.error('流式试跑中断');
        } else {
          try {
            const result = await testRunPrompt(
              selectedPrompt.id,
              body.sampleCode,
              selectedModelId,
              body.resolvedContent,
            );
            setTrialResult(result);
            if (result.errorReason) {
              message.error('试跑失败');
            } else {
              message.success('试跑完成');
            }
          } catch (fallbackError) {
            setTrialResult({
              inputTokens: 0,
              outputTokens: 0,
              durationMs: 0,
              result: '',
              errorReason: fallbackError instanceof Error ? fallbackError.message : '试跑失败',
            });
            message.error('试跑失败');
          }
        }
      } finally {
        setTrialRunning(false);
      }
    },
    [selectedModelId, selectedPrompt],
  );

  const columns = useMemo(
    () =>
      createPromptColumns({
        onOpenTrial: openTrialModal,
        onEdit: openPromptModal,
        onClone: handleClone,
        onDelete: handleDelete,
        onStatusChange: handleStatusChange,
      }),
    [handleClone, handleDelete, handleStatusChange, openPromptModal, openTrialModal],
  );

  return (
    <div className="ci-page ci-prompts-page">
      <Row gutter={[18, 18]} className="ci-split-workspace">
        <Col span={24}>
          <PromptLibraryCard
            activePromptType={activePromptType}
            searchName={searchName}
            prompts={prompts}
            columns={columns}
            loading={loading}
            current={current}
            size={size}
            total={total}
            selectedPromptId={selectedPrompt?.id}
            onPromptTypeChange={handlePromptTypeChange}
            onSearchNameChange={setSearchName}
            onSearch={handleSearch}
            onReset={handleReset}
            onCreate={() => openPromptModal()}
            onPageChange={(page, pageSize) => {
              setCurrent(page);
              setSize(pageSize);
            }}
          />
        </Col>
      </Row>

      <PromptTrialModal
        open={trialModalOpen}
        promptTypeLabel={activePromptTypeMeta.label}
        selectedPrompt={selectedPrompt}
        models={models}
        selectedModelId={selectedModelId}
        sampleCode={sampleCode}
        running={trialRunning}
        result={trialResult}
        onCancel={() => setTrialModalOpen(false)}
        onRun={handleTrialRun}
        onModelChange={setSelectedModelId}
        onSampleCodeChange={setSampleCode}
      />

      <PromptFormModal
        open={promptModalOpen}
        promptTypeLabel={activePromptTypeMeta.label}
        editingPrompt={editingPrompt}
        form={promptForm}
        onSubmit={handlePromptSubmit}
        onCancel={() => setPromptModalOpen(false)}
      />
    </div>
  );
};

export default Prompts;
