import React, { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  HolderOutlined,
  PlusOutlined,
  SettingOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import {
  createModel,
  deleteModel,
  listModels,
  updateModel,
} from '../../api/model';
import type { AiModel } from '../../types';

const { Text } = Typography;

export const ModelConfig: React.FC = () => {
  const [models, setModels] = useState<AiModel[]>([]);
  const [loading, setLoading] = useState(false);
  const [modelModalOpen, setModelModalOpen] = useState(false);
  const [editingModel, setEditingModel] = useState<AiModel | null>(null);
  const [form] = Form.useForm();

  // 拖拽排序状态
  const [draggedIndex, setDraggedIndex] = useState<number | null>(null);
  const [dragOverIndex, setDragOverIndex] = useState<number | null>(null);
  const [rowDraggable, setRowDraggable] = useState(false);

  const handleRowDragStart = (e: React.DragEvent, index: number) => {
    setDraggedIndex(index);
    e.dataTransfer.effectAllowed = 'move';
  };

  const handleRowDragOver = (e: React.DragEvent, index: number) => {
    e.preventDefault();
    if (dragOverIndex !== index) {
      setDragOverIndex(index);
    }
  };

  const handleRowDragLeave = () => {
    setDragOverIndex(null);
  };

  const handleRowDrop = async (e: React.DragEvent, targetIndex: number) => {
    e.preventDefault();
    if (draggedIndex === null || draggedIndex === targetIndex) return;

    const reorderedModels = [...models];
    const [draggedItem] = reorderedModels.splice(draggedIndex, 1);
    reorderedModels.splice(targetIndex, 0, draggedItem);

    // 重新计算并分配排序权重值 (10, 20, 30...)
    const updatedModels = reorderedModels.map((item, idx) => ({
      ...item,
      sortOrder: (idx + 1) * 10,
    }));

    setModels(updatedModels);
    setDraggedIndex(null);
    setDragOverIndex(null);

    try {
      const promises = updatedModels
        .filter((item, idx) => models[idx]?.id !== item.id || models[idx]?.sortOrder !== item.sortOrder)
        .map((item) => updateModel(item.id, item));

      await Promise.all(promises);
      message.success('排序更新成功');
      fetchModels();
    } catch (err) {
      console.error(err);
      message.error('排序更新失败');
      fetchModels();
    }
  };

  const handleRowDragEnd = () => {
    setDraggedIndex(null);
    setDragOverIndex(null);
    setRowDraggable(false);
  };

  const presets = [
    { name: 'Gemini 2.0 Pro', provider: 'Google', identifier: 'gemini-2.0-pro-exp-02-05', baseUrl: 'https://generativelanguage.googleapis.com', description: 'Google 顶尖多模态模型，支持原生视频理解。', capabilities: ['text', 'image', 'video'] },
    { name: 'Qwen-VL-Max', provider: 'Alibaba', identifier: 'qwen-vl-max', baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1', description: '通义千问视觉大模型，视频理解能力强。', capabilities: ['text', 'image', 'video'] },
    { name: 'DeepSeek Chat', provider: 'DeepSeek', identifier: 'deepseek-chat', baseUrl: 'https://api.deepseek.com', description: '深度求索高性能模型，环境分析极具性价比。', capabilities: ['text'] },
    { name: 'GPT-4o', provider: 'OpenAI', identifier: 'gpt-4o', baseUrl: 'https://api.openai.com/v1', description: 'OpenAI 旗舰全能模型，推理能力卓越。', capabilities: ['text', 'image', 'video'] },
    { name: 'MiniMax-M2.7', provider: 'MiniMax', identifier: 'MiniMax-M2.7', baseUrl: 'https://api.minimaxi.chat/v1', description: '国产多模态模型，支持图片理解与环境分析。', capabilities: ['text', 'image'] },
    { name: 'MiniMax-M3', provider: 'MiniMax', identifier: 'MiniMax-M3', baseUrl: 'https://api.minimaxi.chat/v1', description: 'MiniMax 最新旗舰，原生多模态，100 万 Token 上下文。', capabilities: ['text', 'image', 'video'] },
  ];

  const fetchModels = useCallback(async () => {
    setLoading(true);
    try {
      const data = await listModels();
      setModels(data);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchModels();
  }, [fetchModels]);

  const openModelModal = (model: AiModel | null = null) => {
    setEditingModel(model);
    if (model) {
      form.setFieldsValue({
        name: model.name,
        provider: model.provider,
        identifier: model.identifier,
        baseUrl: model.baseUrl,
        apiKey: model.apiKey,
        capabilities: model.capabilities ? model.capabilities.split(',') : [],
        description: model.description,
        isDefault: model.isDefault === 'true',
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        isDefault: false,
        capabilities: ['text'],
      });
    }
    setModelModalOpen(true);
  };

  const handleApplyPreset = (presetName: string) => {
    const p = presets.find((x) => x.name === presetName);
    if (p) {
      form.setFieldsValue({
        name: p.name,
        provider: p.provider,
        identifier: p.identifier,
        baseUrl: p.baseUrl,
        description: p.description,
        capabilities: p.capabilities,
      });
    }
  };

  const handleModelSubmit = async () => {
    const values = await form.validateFields();
    const payload: Partial<AiModel> = {
      name: values.name,
      provider: values.provider,
      identifier: values.identifier,
      baseUrl: values.baseUrl,
      apiKey: values.apiKey,
      capabilities: values.capabilities ? values.capabilities.join(',') : '',
      description: values.description,
      isDefault: values.isDefault ? 'true' : 'false',
    };

    try {
      if (editingModel) {
        await updateModel(editingModel.id, {
          ...payload,
          sortOrder: editingModel.sortOrder,
        });
        message.success('AI模型更新成功');
      } else {
        await createModel({
          ...payload,
          sortOrder: (models.length + 1) * 10,
        });
        message.success('AI模型创建成功');
      }
      setModelModalOpen(false);
      fetchModels();
    } catch (e) {
      console.error(e);
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteModel(id);
      message.success('AI模型已删除');
      fetchModels();
    } catch (e) {
      console.error(e);
    }
  };

  const handleToggleDefault = async (checked: boolean, record: AiModel) => {
    try {
      await updateModel(record.id, {
        ...record,
        isDefault: checked ? 'true' : 'false',
      });
      message.success(`${record.name} 已设置为默认模型`);
      fetchModels();
    } catch (e) {
      console.error(e);
    }
  };

  const columns = [
    {
      title: '排序',
      key: 'order',
      width: 80,
      render: (_: any, _record: AiModel, index: number) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div
            onMouseEnter={() => setRowDraggable(true)}
            onMouseLeave={() => setRowDraggable(false)}
            style={{ cursor: 'grab', display: 'flex', alignItems: 'center', padding: '4px' }}
            title="拖拽排序"
          >
            <HolderOutlined style={{ fontSize: '15px', color: '#8c8c8c' }} />
          </div>
          <Tag color="cyan" style={{ margin: 0, minWidth: '22px', height: '22px', lineHeight: '20px', textAlign: 'center', borderRadius: '4px', fontWeight: 600, border: 'none' }}>
            {index + 1}
          </Tag>
        </div>
      ),
    },
    {
      title: '模型信息',
      key: 'modelInfo',
      render: (_: any, record: AiModel) => (
        <Space direction="vertical" size={2} style={{ width: '100%' }}>
          <Space size={8} style={{ flexWrap: 'wrap' }}>
            <Text strong style={{ fontSize: '14px' }}>{record.name}</Text>
            <Tag color="blue" style={{ border: 'none', borderRadius: '4px', margin: 0 }}>
              {record.provider}
            </Tag>
            {record.isDefault === 'true' && (
              <Tag color="gold" icon={<ThunderboltOutlined />} style={{ border: 'none', borderRadius: '4px', margin: 0 }}>
                默认
              </Tag>
            )}
          </Space>
          <div style={{ marginTop: 2 }}>
            <Text type="secondary" code style={{ fontSize: '12px' }}>
              {record.identifier}
            </Text>
          </div>
        </Space>
      ),
    },
    {
      title: 'Endpoint URL (接口地址)',
      dataIndex: 'baseUrl',
      key: 'baseUrl',
      ellipsis: { showTitle: true },
      render: (text: string) =>
        text ? (
          <Text type="secondary" copyable style={{ fontSize: '13px', fontFamily: 'monospace' }}>
            {text}
          </Text>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
    {
      title: '支持能力',
      dataIndex: 'capabilities',
      key: 'capabilities',
      width: 180,
      render: (text: string) => {
        if (!text) return <Text type="secondary" italic>无</Text>;
        return (
          <Space size={4}>
            {text.split(',').map((cap) => {
              let label = cap;
              let color = 'default';
              if (cap === 'text') {
                label = '文本';
                color = 'blue';
              } else if (cap === 'image') {
                label = '图片';
                color = 'green';
              } else if (cap === 'video') {
                label = '视频';
                color = 'purple';
              }
              return (
                <Tag key={cap} color={color}>
                  {label}
                </Tag>
              );
            })}
          </Space>
        );
      },
    },
    {
      title: '默认模型',
      key: 'isDefault',
      width: 100,
      render: (_: any, record: AiModel) => (
        <Switch
          checked={record.isDefault === 'true'}
          onChange={(checked) => handleToggleDefault(checked, record)}
        />
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      fixed: 'right' as const,
      render: (_: any, record: AiModel) => (
        <Space size={16}>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            onClick={() => openModelModal(record)}
          >
            编辑
          </Button>
          <Popconfirm
            title="确认要删除该模型配置吗？"
            onConfirm={() => handleDelete(record.id)}
            okText="确认"
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

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
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => openModelModal()}
          >
            新增模型
          </Button>
        }
      >
        <Table
          dataSource={models}
          columns={columns}
          rowKey="id"
          loading={loading}
          tableLayout="auto"
          pagination={false}
          onRow={(_, index) => ({
            draggable: rowDraggable,
            onDragStart: (e) => handleRowDragStart(e, index!),
            onDragOver: (e) => handleRowDragOver(e, index!),
            onDragLeave: handleRowDragLeave,
            onDrop: (e) => handleRowDrop(e, index!),
            onDragEnd: handleRowDragEnd,
            className: `ci-model-row-draggable ${draggedIndex === index ? 'ci-model-row-dragging' : ''} ${dragOverIndex === index ? 'ci-model-row-drag-over' : ''}`,
            style: {
              cursor: rowDraggable ? 'grab' : 'default',
            }
          })}
        />
      </Card>

      <Modal
        title={
          <Space>
            <SettingOutlined />
            <span>{editingModel ? '编辑模型配置' : '新增 AI 模型'}</span>
          </Space>
        }
        open={modelModalOpen}
        onOk={handleModelSubmit}
        onCancel={() => setModelModalOpen(false)}
        width={700}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="从官方预设模板快速加载">
                <Select
                  placeholder="选择一个官方预设模板填充..."
                  onChange={handleApplyPreset}
                  options={presets.map((p) => ({ label: p.name, value: p.name }))}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="name"
                label="模型显示名称"
                rules={[{ required: true, message: '请输入模型显示名称' }]}
              >
                <Input placeholder="如: Gemini 2.0 Pro" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="provider"
                label="技术供应商"
                rules={[{ required: true, message: '请输入技术供应商' }]}
              >
                <Input placeholder="如: Google" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="identifier"
                label="模型调用 ID"
                rules={[{ required: true, message: '请输入模型调用 ID' }]}
              >
                <Input placeholder="如: gemini-2.0-pro-exp-02-05" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item
            name="baseUrl"
            label="Endpoint URL (接口地址)"
          >
            <Input placeholder="https://generativelanguage.googleapis.com" />
          </Form.Item>

          <Form.Item
            name="apiKey"
            label="API Key (密钥)"
          >
            <Input.Password placeholder="在此输入您的密钥" />
          </Form.Item>

          <Form.Item
            name="capabilities"
            label="支持能力"
            rules={[{ required: true, message: '请选择支持能力' }]}
          >
            <Select
              mode="multiple"
              placeholder="选择支持的能力"
              options={[
                { label: '文本识别', value: 'text' },
                { label: '图片识别', value: 'image' },
                { label: '视频识别', value: 'video' },
              ]}
            />
          </Form.Item>

          <Form.Item name="description" label="功能描述">
            <Input.TextArea
              rows={3}
              placeholder="描述该模型的分析优势、建议场景等..."
            />
          </Form.Item>

          <Form.Item name="isDefault" label="是否默认模型" valuePropName="checked">
            <Switch checkedChildren="是" unCheckedChildren="否" />
          </Form.Item>
        </Form>
      </Modal>

      <style>{`
        .ci-model-row-draggable {
          transition: background-color 0.25s ease, opacity 0.25s ease;
        }
        .ci-model-row-dragging {
          opacity: 0.45;
          background-color: #f3f4f6 !important;
        }
        .ci-model-row-drag-over td {
          background-color: var(--premium-primary-soft) !important;
          border-top: 2px solid var(--premium-primary) !important;
          transition: none !important;
        }
      `}</style>
    </div>
  );
};

export default ModelConfig;
