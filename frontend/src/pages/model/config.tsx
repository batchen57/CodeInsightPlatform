import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Button, Card, Form, Modal, Space, Table, Typography, message } from 'antd';
import type { FormInstance } from 'antd';
import { PlusOutlined, SettingOutlined } from '@ant-design/icons';
import {
  changeModelPresetStatus,
  changeModelStatus,
  createModel,
  createModelPreset,
  deleteModel,
  deleteModelPreset,
  getModelMetricTrend,
  listAllModelPresets,
  listModelPresets,
  listModelMetricSummaries,
  testModelConnection,
  updateModel,
  updateModelPreset,
} from '../../api/model';
import type {
  AiModel,
  AiModelMetricSummary,
  AiModelMetricTrendPoint,
  AiModelPreset,
} from '../../types';
import { useDragSort, useModels } from './hooks';
import { getModelColumns } from './columns';
import ModelFormModal, { type ModelFormValues } from './ModelFormModal';
import ModelMetricsDrawer from './ModelMetricsDrawer';
import ModelPresetManagerDrawer from './ModelPresetManagerDrawer';
import ModelPresetModal, { type ModelPresetFormValues } from './ModelPresetModal';
import {
  fillFormForEdit,
  getDefaultFormValues,
  valuesToPayload,
} from './modelFormUtils';
import './model-drag.css';

const { Text } = Typography;

/**
 * AI 模型配置管理页
 *
 * 关注点拆分：
 *  - 数据获取 → hooks.ts: useModels
 *  - 拖拽排序 → hooks.ts: useDragSort
 *  - 表单弹窗 → ModelFormModal.tsx
 *  - 表格列   → columns.tsx
 *  - 预设模板 → 后端 /models/presets
 *  - 拖拽样式 → model-drag.css
 *  - 本文件   → 仅编排：state + handlers + 渲染
 */
export const ModelConfig: React.FC = () => {
  const { models, loading, fetch, setModels } = useModels();
  const dragSort = useDragSort();

  // 表单 Modal 状态
  const [modelModalOpen, setModelModalOpen] = useState(false);
  const [editingModel, setEditingModel] = useState<AiModel | null>(null);
  const [form] = Form.useForm<ModelFormValues>();
  const [modelPresets, setModelPresets] = useState<AiModelPreset[]>([]);
  const [allModelPresets, setAllModelPresets] = useState<AiModelPreset[]>([]);
  const [presetsLoading, setPresetsLoading] = useState(false);
  const [presetManagerLoading, setPresetManagerLoading] = useState(false);
  const [presetManagerOpen, setPresetManagerOpen] = useState(false);
  const [presetModalOpen, setPresetModalOpen] = useState(false);
  const [presetEditing, setPresetEditing] = useState<AiModelPreset | null>(null);
  const [presetForm] = Form.useForm<ModelPresetFormValues>();
  const [metricSummaries, setMetricSummaries] = useState<AiModelMetricSummary[]>([]);
  const [metricTrend, setMetricTrend] = useState<AiModelMetricTrendPoint[]>([]);
  const [metricTrendLoading, setMetricTrendLoading] = useState(false);
  const [metricDrawerOpen, setMetricDrawerOpen] = useState(false);
  const [metricModel, setMetricModel] = useState<AiModel | null>(null);
  const [testingModelId, setTestingModelId] = useState<number | null>(null);

  // ===== 业务操作 =====
  const fetchModelPresets = useCallback(async () => {
    setPresetsLoading(true);
    try {
      const data = await listModelPresets();
      setModelPresets(data);
    } catch (e) {
      console.error(e);
    } finally {
      setPresetsLoading(false);
    }
  }, []);

  const fetchAllModelPresets = useCallback(async () => {
    setPresetManagerLoading(true);
    try {
      const data = await listAllModelPresets();
      setAllModelPresets(data);
    } catch (e) {
      console.error(e);
    } finally {
      setPresetManagerLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchModelPresets();
  }, [fetchModelPresets]);

  const fetchMetricSummaries = useCallback(async () => {
    try {
      const data = await listModelMetricSummaries();
      setMetricSummaries(data);
    } catch (e) {
      console.error(e);
    }
  }, []);

  useEffect(() => {
    fetchMetricSummaries();
  }, [fetchMetricSummaries]);

  const metricsByModel = useMemo(
    () =>
      metricSummaries.reduce<Record<string, AiModelMetricSummary>>((map, item) => {
        map[item.modelName] = item;
        return map;
      }, {}),
    [metricSummaries],
  );

  const openModelModal = useCallback(
    (model: AiModel | null = null) => {
      setEditingModel(model);
      fetchModelPresets();
      if (model) {
        fillFormForEdit(form, model);
      } else {
        form.resetFields();
        form.setFieldsValue(getDefaultFormValues());
      }
      setModelModalOpen(true);
    },
    [fetchModelPresets, form],
  );

  const openPresetModal = useCallback((preset: AiModelPreset | null = null) => {
    setPresetEditing(preset);
    presetForm.resetFields();
    if (preset) {
      presetForm.setFieldsValue({
        name: preset.name,
        provider: preset.provider,
        identifier: preset.identifier,
        baseUrl: preset.baseUrl,
        capabilities: preset.capabilities ? preset.capabilities.split(',') : [],
        description: preset.description,
        sortOrder: preset.sortOrder,
        status: (preset.status ?? 1) === 1,
      });
    } else {
      const presetCount = allModelPresets.length || modelPresets.length;
      presetForm.setFieldsValue({
        capabilities: ['text'],
        sortOrder: (presetCount + 1) * 10,
        status: true,
      });
    }
    setPresetModalOpen(true);
  }, [allModelPresets.length, modelPresets.length, presetForm]);

  const openPresetManager = useCallback(() => {
    setPresetManagerOpen(true);
    fetchAllModelPresets();
  }, [fetchAllModelPresets]);

  const handleModelSubmit = useCallback(async () => {
    const values = await form.validateFields();
    try {
      if (editingModel) {
        await updateModel(
          editingModel.id,
          valuesToPayload(values, { sortOrder: editingModel.sortOrder, id: editingModel.id }),
        );
        message.success('AI模型更新成功');
      } else {
        await createModel(valuesToPayload(values, { sortOrder: (models.length + 1) * 10 }));
        message.success('AI模型创建成功');
      }
      setModelModalOpen(false);
      fetch();
      fetchMetricSummaries();
    } catch (e) {
      console.error(e);
    }
  }, [editingModel, fetch, fetchMetricSummaries, form, models.length]);

  const handlePresetSubmit = useCallback(async () => {
    const values = await presetForm.validateFields();
    const payload = {
      name: values.name,
      provider: values.provider,
      identifier: values.identifier,
      baseUrl: values.baseUrl,
      capabilities: values.capabilities.join(','),
      description: values.description,
      sortOrder: values.sortOrder ?? (modelPresets.length + 1) * 10,
      status: values.status ? 1 : 0,
    };
    try {
      if (presetEditing) {
        await updateModelPreset(presetEditing.id, payload);
        message.success('模型预设更新成功');
      } else {
        await createModelPreset(payload);
        message.success('模型预设创建成功');
      }
      setPresetModalOpen(false);
      setPresetEditing(null);
      fetchModelPresets();
      fetchAllModelPresets();
    } catch (e) {
      console.error(e);
    }
  }, [
    fetchAllModelPresets,
    fetchModelPresets,
    modelPresets.length,
    presetEditing,
    presetForm,
  ]);

  const handlePresetToggleStatus = useCallback(
    async (preset: AiModelPreset, checked: boolean) => {
      try {
        await changeModelPresetStatus(preset.id, checked ? 1 : 0);
        message.success(`${preset.name} 已${checked ? '启用' : '停用'}`);
        fetchModelPresets();
        fetchAllModelPresets();
      } catch (e) {
        console.error(e);
      }
    },
    [fetchAllModelPresets, fetchModelPresets],
  );

  const handlePresetDelete = useCallback(
    async (id: number) => {
      try {
        await deleteModelPreset(id);
        message.success('模型预设已删除');
        fetchModelPresets();
        fetchAllModelPresets();
      } catch (e) {
        console.error(e);
      }
    },
    [fetchAllModelPresets, fetchModelPresets],
  );

  const handleDelete = useCallback(
    async (id: number) => {
      try {
        await deleteModel(id);
        message.success('AI模型已删除');
        fetch();
        fetchMetricSummaries();
      } catch (e) {
        console.error(e);
      }
    },
    [fetch, fetchMetricSummaries],
  );

  const handleToggleDefault = useCallback(
    async (checked: boolean, record: AiModel) => {
      try {
        await updateModel(record.id, {
          ...record,
          isDefault: checked ? 'true' : 'false',
        });
        message.success(`${record.name} 已设置为默认模型`);
        fetch();
        fetchMetricSummaries();
      } catch (e) {
        console.error(e);
      }
    },
    [fetch, fetchMetricSummaries],
  );

  const handleToggleStatus = useCallback(
    async (checked: boolean, record: AiModel) => {
      try {
        await changeModelStatus(record.id, checked ? 1 : 0);
        message.success(`${record.name} 已${checked ? '启用' : '停用'}`);
        fetch();
        fetchMetricSummaries();
      } catch (e) {
        console.error(e);
      }
    },
    [fetch, fetchMetricSummaries],
  );

  const handleTestModel = useCallback(async (record: AiModel) => {
    setTestingModelId(record.id);
    try {
      const result = await testModelConnection(record.id);
      const content = (
        <Space direction="vertical" size={6}>
          <Text>{result.message}</Text>
          <Text type="secondary">耗时：{result.durationMs} ms</Text>
          {result.responseSummary && (
            <Text type="secondary">响应摘要：{result.responseSummary}</Text>
          )}
        </Space>
      );

      if (result.success) {
        Modal.success({
          title: `${record.name} 测试通过`,
          content,
        });
      } else {
        Modal.error({
          title: `${record.name} 测试失败`,
          content,
        });
      }
    } catch (e) {
      console.error(e);
      message.error('模型测试失败，请检查接口地址和密钥配置');
    } finally {
      setTestingModelId(null);
    }
  }, []);

  const openMetricDrawer = useCallback(async (model: AiModel) => {
    setMetricModel(model);
    setMetricDrawerOpen(true);
    setMetricTrendLoading(true);
    try {
      const data = await getModelMetricTrend(model.id, 7);
      setMetricTrend(data);
    } catch (e) {
      console.error(e);
      setMetricTrend([]);
    } finally {
      setMetricTrendLoading(false);
    }
  }, []);

  // 拖拽落点：算出新顺序 → 并发调 API 落 sortOrder → 失败回滚
  const handleRowDrop = useCallback(
    async (e: React.DragEvent, targetIndex: number) => {
      e.preventDefault();
      const from = dragSort.draggedIndex;
      if (from === null || from === targetIndex) return;
      const reordered = dragSort.reorder(models, from, targetIndex);
      setModels(reordered);
      dragSort.disableDrag();
      try {
        const dirty = reordered.filter(
          (item, idx) =>
            models[idx]?.id !== item.id || models[idx]?.sortOrder !== item.sortOrder,
        );
        await Promise.all(dirty.map((m) => updateModel(m.id, m)));
        message.success('排序更新成功');
        fetch();
        fetchMetricSummaries();
      } catch (err) {
        console.error(err);
        message.error('排序更新失败');
        fetch();
        fetchMetricSummaries();
      }
    },
    [dragSort, models, setModels, fetch, fetchMetricSummaries],
  );

  // ===== 列定义 =====
  const columns = getModelColumns(
    {
      onEdit: openModelModal,
      onDelete: handleDelete,
      onTest: handleTestModel,
      onViewMetrics: openMetricDrawer,
      onToggleDefault: handleToggleDefault,
      onToggleStatus: handleToggleStatus,
      onDragHandleEnter: dragSort.enableDrag,
      onDragHandleLeave: dragSort.disableDrag,
    },
    { metricsByModel, testingModelId },
  );

  return (
    <div className="ci-workspace">
      <Card
        className="ci-table-card"
        title={
          <Space>
            <SettingOutlined className="ci-card-title-icon" />
            <span>AI模型配置管理</span>
          </Space>
        }
        extra={
          <Space>
            <Button icon={<SettingOutlined />} onClick={openPresetManager}>
              预设管理
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openModelModal()}>
              新增模型
            </Button>
          </Space>
        }
      >
        <Table<AiModel>
          dataSource={models}
          columns={columns}
          rowKey="id"
          loading={loading}
          tableLayout="auto"
          scroll={{ x: 1420 }}
          pagination={false}
          onRow={(_, index) => {
            const rowProps = dragSort.onRow(index ?? 0);
            return {
              ...rowProps,
              onDrop: (e) => {
                const target = rowProps.onDrop(e);
                handleRowDrop(e, target);
              },
            };
          }}
        />
      </Card>

      <ModelFormModal
        open={modelModalOpen}
        editing={editingModel}
        form={form as FormInstance<ModelFormValues>}
        presets={modelPresets}
        presetsLoading={presetsLoading}
        onCancel={() => setModelModalOpen(false)}
        onSubmit={handleModelSubmit}
        onCreatePreset={openPresetModal}
        onManagePresets={openPresetManager}
      />

      <ModelPresetModal
        open={presetModalOpen}
        editing={presetEditing}
        form={presetForm as FormInstance<ModelPresetFormValues>}
        onCancel={() => {
          setPresetModalOpen(false);
          setPresetEditing(null);
        }}
        onSubmit={handlePresetSubmit}
      />

      <ModelPresetManagerDrawer
        open={presetManagerOpen}
        presets={allModelPresets}
        loading={presetManagerLoading}
        onClose={() => setPresetManagerOpen(false)}
        onCreate={() => openPresetModal()}
        onEdit={openPresetModal}
        onDelete={handlePresetDelete}
        onToggleStatus={handlePresetToggleStatus}
      />

      <ModelMetricsDrawer
        open={metricDrawerOpen}
        model={metricModel}
        summary={metricModel ? metricsByModel[metricModel.identifier] : undefined}
        trend={metricTrend}
        loading={metricTrendLoading}
        onClose={() => setMetricDrawerOpen(false)}
      />
    </div>
  );
};

export default ModelConfig;
