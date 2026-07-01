import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Modal,
  Select,
  Space,
  Tag,
  Typography,
  message,
} from 'antd';
import { PlayCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { listPrompts, getPrompt, deletePrompt } from '../../api/prompt';
import { updateRepository } from '../../api/repository';
import type { Prompt, Repository, System } from '../../types';
import SystemPromptEditorModal from './SystemPromptEditorModal';
import SystemPromptTrialModal from './SystemPromptTrialModal';

const { Text } = Typography;

const TYPE_NAME: Record<'MODULARIZE' | 'DOCUMENT_GENERATION', string> = {
  MODULARIZE: '模块提取',
  DOCUMENT_GENERATION: '文档生成',
};

const TYPE_LABEL: Record<'MODULARIZE' | 'DOCUMENT_GENERATION', string> = TYPE_NAME;

interface Props {
  open: boolean;
  repository: Repository | null;
  onClose: () => void;
  /** 父组件的 list 重新拉取回调(创建/绑定后) */
  onSaved?: () => void;
}

/**
 * 「系统 → 提示词」聚焦编辑弹窗
 *
 * 与 wizard Step 4 等价的逻辑,但只有 Step 4 这一段(用于已创建系统的修改)。
 * - 每种类型一个卡片,显示当前绑定的提示词(标签: 默认 / 自定义)
 * - 「自定义」按钮打开编辑器;编辑器内置「从默认提示词加载」+ 试跑
 * - 创建成功后,新 prompt 自动绑定到系统,并刷新本地提示词列表
 */
const SystemPromptBindModal: React.FC<Props> = ({ open, repository, onClose, onSaved }) => {
  const [prompts, setPrompts] = useState<Prompt[]>([]);
  const [promptsLoading, setPromptsLoading] = useState(false);

  const [editorState, setEditorState] = useState<{
    open: boolean;
    promptType: 'MODULARIZE' | 'DOCUMENT_GENERATION';
  } | null>(null);
  const [trialPrompt, setTrialPrompt] = useState<Prompt | null>(null);
  const [trialOpen, setTrialOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // 拉取可用提示词(DEFAULT is_default=1 + 该系统下 USER 提示词)
  const fetchAll = async () => {
    setPromptsLoading(true);
    try {
      const all: Prompt[] = [];
      const sysId = repository?.id;
      for (const t of Object.keys(TYPE_NAME) as ('MODULARIZE' | 'DOCUMENT_GENERATION')[]) {
        // 拉 DEFAULT 类别的
        const defRes = await listPrompts({
          current: 1,
          size: 200,
          lifecycle: 'RELEASED',
          promptType: t,
          category: 'DEFAULT',
          isDefault: 1,
        });
        all.push(...defRes.records);
        // 拉该系统下的 USER 提示词
        if (sysId) {
          const userRes = await listPrompts({
            current: 1,
            size: 200,
            lifecycle: 'RELEASED',
            promptType: t,
            category: 'USER',
            scopeId: sysId,
          });
          all.push(...userRes.records);
        }
      }
      // DRAFT 的也补进来
      if (repository) {
        const boundIds = [repository.modularizePromptId, repository.documentPromptId].filter(
          Boolean,
        ) as number[];
        for (const id of boundIds) {
          if (!all.some((p) => p.id === id)) {
            try {
              const p = await getPrompt(id);
              if (p) all.push(p);
            } catch {
              // 忽略
            }
          }
        }
      }
      setPrompts(all);
    } finally {
      setPromptsLoading(false);
    }
  };

  useEffect(() => {
    if (open) fetchAll();
  }, [open, repository?.id]);

  // 找到当前已绑定的 prompt(可能为 null)
  const selectedModularize = useMemo(
    () => prompts.find((p) => p.id === repository?.modularizePromptId) ?? null,
    [prompts, repository?.modularizePromptId],
  );
  const selectedDocument = useMemo(
    () => prompts.find((p) => p.id === repository?.documentPromptId) ?? null,
    [prompts, repository?.documentPromptId],
  );

  // 全局默认(同类型 is_default=1)
  const defaultModularize = useMemo(
    () => prompts.find((p) => p.promptType === 'MODULARIZE' && p.isDefault === 1) ?? null,
    [prompts],
  );
  const defaultDocument = useMemo(
    () => prompts.find((p) => p.promptType === 'DOCUMENT_GENERATION' && p.isDefault === 1) ?? null,
    [prompts],
  );

  // 下拉框选项(每个类型的所有 prompt,按 is_default 优先 / version 倒序)
  const modularizeOptions = useMemo(
    () =>
      prompts
        .filter((p) => p.promptType === 'MODULARIZE' && p.lifecycle === 'RELEASED')
        .map((p) => ({
          value: p.id,
          label: `${p.name} (v${p.version})${p.isDefault === 1 ? ' · 默认' : ''}`,
        })),
    [prompts],
  );
  const documentOptions = useMemo(
    () =>
      prompts
        .filter((p) => p.promptType === 'DOCUMENT_GENERATION' && p.lifecycle === 'RELEASED')
        .map((p) => ({
          value: p.id,
          label: `${p.name} (v${p.version})${p.isDefault === 1 ? ' · 默认' : ''}`,
        })),
    [prompts],
  );

  /** 清理孤立的 USER 提示词(旧绑定是 USER + 同 scopeId + 不再被任何系统引用) */
  const cleanupOrphanedUserPrompt = async (_promptType: 'MODULARIZE' | 'DOCUMENT_GENERATION', oldPrompt: Prompt | null) => {
    if (!oldPrompt || oldPrompt.category !== 'USER') return;
    if (!repository) return;
    if (oldPrompt.scopeId !== repository.id) return;
    try {
      await deletePrompt(oldPrompt.id);
    } catch {
      // 后端拦截(如仍被引用)自动阻止
    }
  };

  /** 从下拉框切换提示词 → 立即调后端 updateSystem */
  const handleSelectExisting = async (
    promptType: 'MODULARIZE' | 'DOCUMENT_GENERATION',
    id: number,
  ) => {
    if (!repository) return;
    const oldPrompt = promptType === 'MODULARIZE' ? selectedModularize : selectedDocument;
    const payload: Partial<System> =
      promptType === 'MODULARIZE'
        ? { modularizePromptId: id }
        : { documentPromptId: id };
    try {
      await updateRepository(repository.id, payload);
      message.success('提示词绑定已更新');
      onSaved?.();
    } catch {
      // 拦截器已提示
    }
    // 异步清理(不阻塞 UI)
    cleanupOrphanedUserPrompt(promptType, oldPrompt);
  };

  /** 打开自定义编辑器 */
  const openCustom = (promptType: 'MODULARIZE' | 'DOCUMENT_GENERATION') => {
    setEditorState({ open: true, promptType });
  };
  const openTrial = (p: Prompt) => {
    setTrialPrompt(p);
    setTrialOpen(true);
  };

  /** 自定义创建成功 → 调用后端 updateSystem 绑定到对应字段 */
  const handlePromptCreated = async (p: Prompt) => {
    if (!repository) return;
    const oldPrompt = p.promptType === 'MODULARIZE' ? selectedModularize : selectedDocument;
    const fieldName = p.promptType === 'MODULARIZE' ? 'modularizePromptId' : 'documentPromptId';
    setSubmitting(true);
    try {
      await updateRepository(repository.id, {
        ...(repository.modularizePromptId ? {} : {}),
        [fieldName]: p.id,
      } as Partial<System>);
      // 把新 prompt 合并到本地列表
      setPrompts((prev) => (prev.some((x) => x.id === p.id) ? prev : [...prev, p]));
      message.success(`已绑定提示词:${p.name}`);
      onSaved?.();
    } catch {
      // 拦截器已提示
    } finally {
      setSubmitting(false);
    }
    // 异步清理旧的 USER 孤立提示词
    cleanupOrphanedUserPrompt(p.promptType as 'MODULARIZE' | 'DOCUMENT_GENERATION', oldPrompt);
  };

  const renderCard = (promptType: 'MODULARIZE' | 'DOCUMENT_GENERATION') => {
    const selected = promptType === 'MODULARIZE' ? selectedModularize : selectedDocument;
    const defaultP = promptType === 'MODULARIZE' ? defaultModularize : defaultDocument;
    return (
      <Card
        size="small"
        style={{ marginBottom: 12 }}
        title={
          <Space>
            <span>{TYPE_LABEL[promptType]}提示词</span>
            {selected && (
              <Tag color="green">
                {selected.name} (v{selected.version})
                {selected.isDefault === 1 ? ' · 默认' : ' · 自定义'}
              </Tag>
            )}
          </Space>
        }
        extra={
          <Space size={4} wrap>
            <Select
              showSearch
              optionFilterProp="label"
              placeholder="选择已有提示词"
              style={{ width: 320 }}
              value={selected?.id}
              onChange={(id) => handleSelectExisting(promptType, id)}
              options={promptType === 'MODULARIZE' ? modularizeOptions : documentOptions}
              loading={promptsLoading}
              allowClear
            />
            <Button
              size="small"
              icon={<PlusOutlined />}
              onClick={() => openCustom(promptType)}
            >
              自定义
            </Button>
            {selected && (
              <Button
                size="small"
                type="link"
                icon={<PlayCircleOutlined />}
                onClick={() => openTrial(selected)}
              >
                试跑
              </Button>
            )}
          </Space>
        }
      >
        {selected ? (
          <Text type="secondary">
            已绑定 ID={selected.id}。点「自定义」可基于默认提示词({defaultP?.name ?? '无'})复制修改并保存为该系统专属的提示词。
          </Text>
        ) : (
          <Text type="secondary">
            当前未绑定。点「自定义」创建一个(将基于默认提示词 {defaultP?.name ?? '无'} 复制修改,自动以「{repository?.gitUrl ?? '系统'} - {TYPE_LABEL[promptType]} - 时间戳」命名)。
          </Text>
        )}
      </Card>
    );
  };

  return (
    <>
      <Modal
        title={
          <Space>
            提示词 · {repository?.gitUrl ?? ''}
            {repository?.gitUrl && <Text type="secondary">({repository?.gitUrl})</Text>}
          </Space>
        }
        open={open}
        onCancel={onClose}
        width={760}
        destroyOnClose
        footer={[
          <Button key="close" onClick={onClose}>
            关闭
          </Button>,
        ]}
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message={
            <Space direction="vertical" size={4}>
              <Text>系统绑定 1 个模块提取提示词 + 1 个文档生成提示词,初始默认绑定到「全局默认提示词」。</Text>
              <Text type="secondary">
                点「自定义」可基于全局默认提示词复制修改,自动以「{repository?.gitUrl ?? '系统'} - 类型 - 时间戳」命名。
              </Text>
            </Space>
          }
        />
        {renderCard('MODULARIZE')}
        {renderCard('DOCUMENT_GENERATION')}

        {(promptsLoading || submitting) && (
          <Text type="secondary" style={{ display: 'block', textAlign: 'right' }}>
            {submitting ? '保存中...' : '加载提示词中...'}
          </Text>
        )}
      </Modal>

      {/* 自定义编辑器弹窗 */}
      {editorState && (
        <SystemPromptEditorModal
          open={editorState.open}
          mode="custom"
          defaultPrompt={
            editorState.promptType === 'MODULARIZE' ? defaultModularize : defaultDocument
          }
          systemName={repository?.gitUrl}
          scopeId={repository?.id}
          promptType={editorState.promptType}
          promptTypeLabel={TYPE_LABEL[editorState.promptType]}
          onClose={() => setEditorState(null)}
          onCreated={handlePromptCreated}
        />
      )}

      {/* 试跑弹窗 */}
      {trialPrompt && (
        <SystemPromptTrialModal
          open={trialOpen}
          prompt={trialPrompt}
          promptTypeLabel={
            trialPrompt.promptType === 'MODULARIZE'
              ? TYPE_LABEL.MODULARIZE
              : TYPE_LABEL.DOCUMENT_GENERATION
          }
          onClose={() => setTrialOpen(false)}
        />
      )}
    </>
  );
};

export default SystemPromptBindModal;
