import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Form,
  Input,
  Row,
  Segmented,
  Select,
  Space,
  Statistic,
  Table,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  CodeOutlined,
  InboxOutlined,
  PlusOutlined,
  ReloadOutlined,
  StarFilled,
} from '@ant-design/icons';
import {
  archivePrompt,
  clonePrompt,
  createPrompt,
  deletePrompt,
  listPrompts,
  publishPrompt,
  setDefaultPrompt,
  testRunPrompt,
  updatePrompt,
} from '../../../api/prompt';
import { listModels } from '../../../api/model';
import type { AiModel, Prompt } from '../../../types';
import type { PromptType } from './constants';
import { PROMPT_TYPE_TABS } from './constants';
import { createPromptColumns, renderPromptExpandedRow } from './columns';
import { getPreferredModel } from './utils';
import PromptFormModal from './PromptFormModal';
import PromptTrialModal from './PromptTrialModal';
import './prompts.css';

const { Text, Paragraph } = Typography;

type LifecycleTab = 'DRAFT' | 'RELEASED' | 'ARCHIVED';

const LIFECYCLE_LABEL: Record<LifecycleTab, string> = {
  DRAFT: '草稿',
  RELEASED: '已发布',
  ARCHIVED: '已归档',
};

const LIFECYCLE_TONE: Record<LifecycleTab, string> = {
  DRAFT: '#faad14',
  RELEASED: '#52c41a',
  ARCHIVED: '#8c8c8c',
};

/**
 * 「提示词」单页(基础配置 — 基础配置 — 提示词)
 *
 * 集中管理所有提示词:
 *  - 顶部:lifecycle 切换(草稿/已发布/已归档)
 *  - 顶部:promptType 切换(模块提取/文档生成)+ 名称搜索
 *  - 顶部:3 张统计卡(当前默认 / 当前 lifecycle 数 / 当前页数)
 *  - 表格:按 lifecycle 动态显示操作
 *    - DRAFT:编辑 / 试跑 / 发布 / 复制 / 删除
 *    - RELEASED:试跑 / 设为默认 / 归档 / 复制
 *    - ARCHIVED:只读(可复制产生新草稿)
 *  - 顶部 "+ 新建" → 创建 DRAFT 草稿
 */
const PromptsPage: React.FC = () => {
  const [form] = Form.useForm();

  // 筛选
  const [lifecycle, setLifecycle] = useState<LifecycleTab>('RELEASED');
  const [promptType, setPromptType] = useState<PromptType>('MODULARIZE');
  const [searchName, setSearchName] = useState('');

  // 列表
  const [prompts, setPrompts] = useState<Prompt[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);

  // 模态框
  const [formModalOpen, setFormModalOpen] = useState(false);
  const [editingPrompt, setEditingPrompt] = useState<Prompt | null>(null);
  const [formModalType, setFormModalType] = useState<PromptType>('MODULARIZE');
  const [trialOpen, setTrialOpen] = useState(false);
  const [trialPrompt, setTrialPrompt] = useState<Prompt | null>(null);
  const [models, setModels] = useState<AiModel[]>([]);
  const [selectedModelId, setSelectedModelId] = useState<number | undefined>();
  const [sampleCode, setSampleCode] = useState<string>(
    `public class OrderService {
  public void createOrder(Order order) {
    checkInventory(order.getItemId());
    orderMapper.insert(order);
  }
}`,
  );
  const [trialRunning, setTrialRunning] = useState(false);
  const [trialResult, setTrialResult] = useState<{
    inputTokens: number;
    outputTokens: number;
    durationMs: number;
    result: string;
    errorReason?: string;
  } | null>(null);

  /** 拉取指定 promptType + lifecycle 的提示词 */
  const fetchPrompts = useCallback(async () => {
    setLoading(true);
    try {
      const data = await listPrompts({
        current,
        size,
        name: searchName || undefined,
        promptType,
        lifecycle,
      });
      setPrompts(data.records);
      setTotal(data.total);
    } finally {
      setLoading(false);
    }
  }, [current, size, searchName, promptType, lifecycle]);

  /** 拉取可用模型(试跑用) */
  const fetchModels = useCallback(async () => {
    try {
      const data = await listModels();
      setModels(data);
      const preferred = getPreferredModel(data);
      if (preferred) setSelectedModelId(preferred.id);
    } catch {
      // ignore
    }
  }, []);

  useEffect(() => {
    fetchPrompts();
  }, [fetchPrompts]);

  useEffect(() => {
    fetchModels();
  }, [fetchModels]);

  /** 切换 lifecycle / promptType 时重置到第一页 */
  useEffect(() => {
    setCurrent(1);
  }, [lifecycle, promptType]);

  // ========== 表单模态框 ==========
  const openCreateModal = () => {
    setEditingPrompt(null);
    setFormModalType(promptType);
    setFormModalOpen(true);
  };
  const openEditModal = (p: Prompt) => {
    setEditingPrompt(p);
    setFormModalType((p.promptType as PromptType) ?? 'MODULARIZE');
    setFormModalOpen(true);
  };
  const handleFormSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (editingPrompt) {
        await updatePrompt(editingPrompt.id, values);
        message.success('已更新(版本号 +1)');
      } else {
        // 新建默认是 DRAFT 草稿
        await createPrompt({
          ...values,
          status: values.status ?? 1,
          isDefault: values.isDefault ?? 0,
          lifecycle: 'DRAFT',
        });
        message.success('已创建草稿');
      }
      setFormModalOpen(false);
      fetchPrompts();
    } catch {
      // 拦截器已提示
    }
  };

  // ========== 试跑模态框 ==========
  const openTrial = (p: Prompt) => {
    setTrialPrompt(p);
    setTrialResult(null);
    setTrialOpen(true);
  };
  const handleTrialRun = async (params: {
    sampleCode: string;
    variables: Record<string, string>;
    resolvedContent: string;
  }) => {
    if (!trialPrompt) return;
    setTrialRunning(true);
    setTrialResult(null);
    try {
      const res = await testRunPrompt(
        trialPrompt.id,
        params.sampleCode,
        selectedModelId,
        params.resolvedContent,
      );
      setTrialResult(res);
      if (res.errorReason) message.error('试跑失败');
      else message.success('试跑完成');
    } catch {
      message.error('试跑请求失败');
    } finally {
      setTrialRunning(false);
    }
  };

  // ========== 行内操作 ==========
  // onClone 接 Prompt,其余接 id
  const handleClone = async (p: Prompt) => {
    try {
      await clonePrompt(p.id);
      message.success('已复制(产生新草稿)');
      fetchPrompts();
    } catch {
      // 拦截器已提示
    }
  };
  const handleDelete = async (id: number) => {
    try {
      await deletePrompt(id);
      message.success('已删除');
      fetchPrompts();
    } catch {
      // 拦截器已提示
    }
  };
  const handlePublish = async (id: number) => {
    try {
      await publishPrompt(id);
      message.success('已发布');
      fetchPrompts();
    } catch {
      // 拦截器已提示
    }
  };
  const handleArchive = async (id: number) => {
    try {
      await archivePrompt(id);
      message.success('已归档');
      fetchPrompts();
    } catch {
      // 拦截器已提示
    }
  };
  const handleSetDefault = async (id: number) => {
    try {
      await setDefaultPrompt(id);
      message.success('已设为当前默认');
      fetchPrompts();
    } catch {
      // 拦截器已提示
    }
  };

  const columns = useMemo(
    () =>
      createPromptColumns({
        onOpenTrial: openTrial,
        onEdit: openEditModal,
        onClone: handleClone,
        onDelete: handleDelete,
        onPublish: handlePublish,
        onArchive: handleArchive,
        onSetDefault: handleSetDefault,
      }),
    [],
  );

  /** 当前页里:默认那条提示词(高亮展示) */
  const currentDefaultForType = useMemo(() => {
    return prompts.find(
      (p) => p.promptType === promptType && p.lifecycle === 'RELEASED' && p.isDefault === 1,
    );
  }, [prompts, promptType]);

  return (
    <div className="ci-page ci-prompts-page">
      {/* 顶部 KPI 卡片 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic
              title={`${LIFECYCLE_LABEL[lifecycle]} · 总数`}
              value={total}
              prefix={<InboxOutlined />}
              valueStyle={{ color: LIFECYCLE_TONE[lifecycle] }}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Statistic
              title="当前 lifecycle 视图(本页)"
              value={prompts.length}
              prefix={<CodeOutlined />}
            />
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Tooltip title="当前 type + RELEASED 状态下的默认提示词">
              <Statistic
                title={`${PROMPT_TYPE_TABS.find((t) => t.key === promptType)?.label} · 当前默认`}
                value={currentDefaultForType ? currentDefaultForType.name : '未设置'}
                prefix={currentDefaultForType ? <StarFilled style={{ color: '#faad14' }} /> : null}
                valueStyle={
                  currentDefaultForType
                    ? { fontSize: 16 }
                    : { color: '#bfbfbf', fontSize: 16 }
                }
              />
            </Tooltip>
          </Card>
        </Col>
        <Col xs={12} md={6}>
          <Card size="small">
            <Tooltip title="在 DRAFT lifecycle 下的草稿数(需发布)">
              <Statistic
                title="待发布草稿"
                value={prompts.filter((p) => p.lifecycle === 'DRAFT').length}
                valueStyle={{ color: '#faad14' }}
              />
            </Tooltip>
          </Card>
        </Col>
      </Row>

      {/* 顶部筛选条 */}
      <Card
        size="small"
        className="ci-filter-card"
        style={{ marginBottom: 12 }}
        title={
          <Space>
            <Segmented
              value={lifecycle}
              onChange={(v) => setLifecycle(v as LifecycleTab)}
              options={[
                { value: 'DRAFT', label: '草稿' },
                { value: 'RELEASED', label: '已发布' },
                { value: 'ARCHIVED', label: '已归档' },
              ]}
            />
            <Select
              value={promptType}
              onChange={setPromptType}
              style={{ width: 180 }}
              options={PROMPT_TYPE_TABS.map((t) => ({ value: t.key, label: t.label }))}
            />
            <Input.Search
              placeholder="按名称模糊搜索"
              allowClear
              value={searchName}
              onChange={(e) => setSearchName(e.target.value)}
              onSearch={() => {
                setCurrent(1);
                fetchPrompts();
              }}
              style={{ width: 240 }}
            />
          </Space>
        }
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={fetchPrompts}>
              刷新
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
              新建草稿
            </Button>
          </Space>
        }
      >
        <Paragraph type="secondary" style={{ marginBottom: 0 }}>
          提示词的生命周期:<b>草稿</b>(可编辑/试跑/发布) → <b>已发布</b>(锁定,只读,试跑/可设为默认/可复制/可归档) → <b>已归档</b>(历史保留)。
          每种类型支持多个发布版本,只有 1 条"当前默认"被运行时采用。
        </Paragraph>
      </Card>

      {/* 表格 */}
      <Card>
        <Table<Prompt>
          dataSource={prompts}
          columns={columns}
          rowKey="id"
          loading={loading}
          expandable={{ expandedRowRender: renderPromptExpandedRow }}
          scroll={{ x: 1300 }}
          pagination={{
            current,
            pageSize: size,
            total,
            showSizeChanger: true,
            onChange: (p, ps) => {
              setCurrent(p);
              setSize(ps);
            },
          }}
          locale={{
            emptyText:
              lifecycle === 'DRAFT' ? (
                <Space direction="vertical" style={{ padding: 24 }}>
                  <Text type="secondary">暂无{`${LIFECYCLE_LABEL[lifecycle]}`}提示词</Text>
                  <Button type="primary" icon={<PlusOutlined />} onClick={openCreateModal}>
                    新建草稿
                  </Button>
                </Space>
              ) : lifecycle === 'RELEASED' ? (
                <Space direction="vertical" style={{ padding: 24 }}>
                  <Text type="secondary">该类型暂无已发布提示词,先去「草稿」页发布一个</Text>
                </Space>
              ) : (
                <Text type="secondary" style={{ padding: 24, display: 'block' }}>暂无归档</Text>
              ),
          }}
        />
      </Card>

      {/* 新建/编辑表单 */}
      <PromptFormModal
        open={formModalOpen}
        editingPrompt={editingPrompt}
        form={form}
        promptTypeLabel={
          PROMPT_TYPE_TABS.find((t) => t.key === formModalType)?.label ?? ''
        }
        onCancel={() => setFormModalOpen(false)}
        onSubmit={handleFormSubmit}
      />

      {/* 试跑 */}
      <PromptTrialModal
        open={trialOpen}
        promptTypeLabel={PROMPT_TYPE_TABS.find((t) => t.key === trialPrompt?.promptType)?.label ?? ''}
        selectedPrompt={trialPrompt}
        models={models}
        selectedModelId={selectedModelId}
        sampleCode={sampleCode}
        running={trialRunning}
        result={trialResult}
        onCancel={() => setTrialOpen(false)}
        onRun={handleTrialRun}
        onModelChange={setSelectedModelId}
        onSampleCodeChange={setSampleCode}
      />
    </div>
  );
};

export default PromptsPage;
