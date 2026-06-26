import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Collapse,
  Divider,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Row,
  Select,
  Space,
  Spin,
  Statistic,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import { CopyOutlined, InfoCircleOutlined, PlusOutlined, PlayCircleOutlined, SearchOutlined } from '@ant-design/icons';
import {
  changePromptStatus,
  clonePrompt,
  createPrompt,
  listPrompts,
  testRunPrompt,
  updatePrompt,
  deletePrompt,
  type PromptTestResult,
} from '../../api/prompt';
import { listModels } from '../../api/model';
import type { Prompt, AiModel } from '../../types';

const { Text } = Typography;

const defaultPrompt = `你是一名资深代码知识分析师。

请阅读下面的 Java 代码，生成可供开发负责人复核的 Markdown 知识草稿。

类名：\${class_name}
核心方法：\${method_name}
源代码：
\${source_code}

输出章节：
1. 职责概述
2. 核心流程
3. 重要依赖
4. 待复核事项`;

const defaultSample = `public class OrderService {
  public void createOrder(Order order) {
    checkInventory(order.getItemId());
    orderMapper.insert(order);
  }
}`;

const Prompts: React.FC = () => {
  const [prompts, setPrompts] = useState<Prompt[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [searchName, setSearchName] = useState('');

  const [promptModalOpen, setPromptModalOpen] = useState(false);
  const [editingPrompt, setEditingPrompt] = useState<Prompt | null>(null);
  const [promptForm] = Form.useForm();

  const [selectedPrompt, setSelectedPrompt] = useState<Prompt | null>(null);
  const [sampleCode, setSampleCode] = useState(defaultSample);
  const [trialRunning, setTrialRunning] = useState(false);
  const [trialResult, setTrialResult] = useState<PromptTestResult | null>(null);
  const [trialModalOpen, setTrialModalOpen] = useState(false);

  const [models, setModels] = useState<AiModel[]>([]);
  const [selectedModelId, setSelectedModelId] = useState<number | undefined>(undefined);

  const fetchModels = useCallback(async () => {
    try {
      const data = await listModels();
      setModels(data);
      const defaultModel = data.find((m) => m.isDefault === 'true');
      if (defaultModel) {
        setSelectedModelId(defaultModel.id);
      }
    } catch (e) {
      console.error('Failed to load models', e);
    }
  }, []);

  useEffect(() => {
    fetchModels();
  }, [fetchModels]);

  const fetchPrompts = useCallback(
    async (page = current, pageSize = size) => {
      setLoading(true);
      try {
        const data = await listPrompts({
          current: page,
          size: pageSize,
          name: searchName || undefined,
        });
        setPrompts(data.records);
        setTotal(data.total);
      } finally {
        setLoading(false);
      }
    },
    [current, searchName, size],
  );

  useEffect(() => {
    fetchPrompts();
  }, [fetchPrompts]);

  const handleSearch = () => {
    setCurrent(1);
    fetchPrompts(1);
  };

  const openPromptModal = (prompt: Prompt | null = null) => {
    setEditingPrompt(prompt);
    if (prompt) {
      promptForm.setFieldsValue({
        name: prompt.name,
        content: prompt.content,
        isDefault: prompt.isDefault === 1,
      });
    } else {
      promptForm.resetFields();
      promptForm.setFieldsValue({ content: defaultPrompt, isDefault: false });
    }
    setPromptModalOpen(true);
  };

  const handlePromptSubmit = async () => {
    const values = await promptForm.validateFields();
    const payload = { ...values, isDefault: values.isDefault ? 1 : 0 };
    if (editingPrompt) {
      await updatePrompt(editingPrompt.id, payload);
      message.success('提示词已更新并生成新版本');
    } else {
      await createPrompt(payload);
      message.success('提示词已创建');
    }
    setPromptModalOpen(false);
    fetchPrompts();
  };

  const handleStatusChange = async (checked: boolean, record: Prompt) => {
    await changePromptStatus(record.id, checked ? 1 : 0);
    message.success(`${record.name} 已${checked ? '启用' : '停用'}`);
    fetchPrompts();
  };

  const handleClone = async (record: Prompt) => {
    await clonePrompt(record.id);
    message.success(`已复制 ${record.name}`);
    fetchPrompts();
  };

  const handleDelete = async (id: number) => {
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
  };

  const handleTrialRun = async () => {
    if (!selectedPrompt) {
      message.warning('请先选择一个提示词再试跑');
      return;
    }
    setTrialRunning(true);
    setTrialResult(null);
    try {
      const result = await testRunPrompt(selectedPrompt.id, sampleCode, selectedModelId);
      setTrialResult(result);
      if (result.errorReason) {
        message.error('试跑失败');
      } else {
        message.success('试跑完成');
      }
    } finally {
      setTrialRunning(false);
    }
  };

  const columns = [
    {
      title: '提示词',
      dataIndex: 'name',
      key: 'name',
      width: 250,
      render: (text: string, record: Prompt) => (
        <div className="ci-prompt-name-cell">
          <Tooltip title={text} placement="topLeft">
            <Button
              type="link"
              className="ci-table-link ci-prompt-name-link"
              onClick={() => {
                setSelectedPrompt(record);
                setTrialResult(null);
                setTrialModalOpen(true);
              }}
            >
              <span>{text}</span>
            </Button>
          </Tooltip>
          {record.isDefault === 1 && <Tag color="gold">默认</Tag>}
        </div>
      ),
    },
    {
      title: '版本',
      dataIndex: 'version',
      key: 'version',
      width: 72,
      render: (version: number) => <Tag color="purple">v{version}</Tag>,
    },
    {
      title: '启用',
      dataIndex: 'status',
      key: 'status',
      width: 82,
      render: (status: number, record: Prompt) => (
        <Switch checkedChildren="开" unCheckedChildren="关" checked={status === 1} onChange={(checked) => handleStatusChange(checked, record)} />
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 166,
      render: (time: string) => (time ? new Date(time).toLocaleString() : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 270,
      fixed: 'right' as const,
      render: (_: unknown, record: Prompt) => (
        <Space size={6} className="ci-prompt-row-actions">
          <Button size="small" onClick={() => openPromptModal(record)}>
            编辑
          </Button>
          <Button size="small" icon={<CopyOutlined />} onClick={() => handleClone(record)}>
            复制
          </Button>
          <Button
            size="small"
            color="primary"
            variant="outlined"
            icon={<PlayCircleOutlined />}
            onClick={() => {
              setSelectedPrompt(record);
              setTrialResult(null);
              setTrialModalOpen(true);
            }}
          >
            试跑
          </Button>
          <Popconfirm
            title="确定要删除该提示词模板吗？"
            description="删除后无法恢复！"
            onConfirm={() => handleDelete(record.id)}
            okText="确定"
            cancelText="取消"
          >
            <Button size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="ci-page ci-prompts-page">
      <Row gutter={[18, 18]} className="ci-split-workspace">
        <Col span={24}>
          <Card
            className="ci-workspace-card ci-prompt-library"
            title="提示词模板"
            extra={
              <Button type="primary" icon={<PlusOutlined />} onClick={() => openPromptModal()}>
                新增提示词
              </Button>
            }
          >
            <div className="ci-card-toolbar">
              <Input
                placeholder="搜索提示词"
                value={searchName}
                onChange={(event) => setSearchName(event.target.value)}
                onPressEnter={handleSearch}
                prefix={<SearchOutlined />}
              />
              <Button type="primary" onClick={handleSearch}>
                查询
              </Button>
              <Button
                onClick={() => {
                  setSearchName('');
                  setCurrent(1);
                  setTimeout(() => fetchPrompts(1), 0);
                }}
              >
                重置
              </Button>
            </div>
            <Table
              dataSource={prompts}
              columns={columns}
              rowKey="id"
              loading={loading}
              tableLayout="fixed"
              scroll={{ x: 872 }}
              rowClassName={(record) => (selectedPrompt?.id === record.id ? 'ci-selected-row' : '')}
              expandable={{
                expandedRowRender: (record) => (
                  <div style={{ padding: '8px 16px', background: '#fcfcfd' }}>
                    <div style={{ marginBottom: 6 }}><Text type="secondary" strong>提示词正文模板：</Text></div>
                    <pre style={{
                      margin: 0,
                      padding: '12px',
                      background: '#f8f9fb',
                      border: '1px solid #e3e6ed',
                      borderRadius: '6px',
                      whiteSpace: 'pre-wrap',
                      fontFamily: '"JetBrains Mono", monospace',
                      fontSize: '12px',
                      color: '#475467',
                      maxHeight: '300px',
                      overflowY: 'auto'
                    }}>
                      {record.content}
                    </pre>
                  </div>
                ),
                rowExpandable: (record) => !!record.content,
              }}
              pagination={{
                current,
                pageSize: size,
                total,
                onChange: (page, pageSize) => {
                  setCurrent(page);
                  setSize(pageSize);
                },
              }}
            />
          </Card>
        </Col>
      </Row>

      <Modal
        title={`提示词试跑：${selectedPrompt?.name || ''} v${selectedPrompt?.version || ''}`}
        open={trialModalOpen}
        onCancel={() => setTrialModalOpen(false)}
        width={800}
        footer={null}
        destroyOnClose
      >
        {selectedPrompt ? (
          <Space direction="vertical" size={16} style={{ width: '100%', marginTop: 16 }}>
            <Collapse
              ghost
              defaultActiveKey={['template']}
              items={[
                {
                  key: 'template',
                  label: (
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', paddingRight: 8 }}>
                      <Text strong>提示词模板正文</Text>
                      <Button
                        size="small"
                        type="text"
                        icon={<CopyOutlined />}
                        onClick={(e) => {
                          e.stopPropagation();
                          navigator.clipboard.writeText(selectedPrompt.content);
                          message.success('模板正文已复制');
                        }}
                      >
                        复制
                      </Button>
                    </div>
                  ),
                  children: (
                    <Input.TextArea
                      autoSize={{ minRows: 4, maxRows: 12 }}
                      className="ci-code-input"
                      value={selectedPrompt.content}
                      readOnly
                    />
                  ),
                },
              ]}
            />
            <Alert
              type="info"
              showIcon
              icon={<InfoCircleOutlined />}
              message="支持的变量"
              description={
                <Space direction="vertical" size={2}>
                  <Text code>{'${class_name}'}</Text>
                  <Text code>{'${method_name}'}</Text>
                  <Text code>{'${source_code}'}</Text>
                </Space>
              }
            />
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Text strong style={{ minWidth: '85px' }}>选择 AI 模型</Text>
              <Select
                style={{ flex: 1 }}
                placeholder="请选择用于试跑的 AI 模型配置"
                options={models.map((m) => ({
                  label: `${m.name} (${m.identifier})` + (m.isDefault === 'true' ? ' [系统默认]' : ''),
                  value: m.id,
                }))}
                value={selectedModelId}
                onChange={(value) => setSelectedModelId(value)}
                allowClear
              />
            </div>
            <div>
              <Text strong style={{ display: 'block', marginBottom: '4px' }}>Java 示例代码</Text>
              <Input.TextArea
                autoSize={{ minRows: 9, maxRows: 18 }}
                className="ci-code-input"
                value={sampleCode}
                onChange={(event) => setSampleCode(event.target.value)}
              />
            </div>
            <Button type="primary" block icon={<PlayCircleOutlined />} loading={trialRunning} onClick={handleTrialRun}>
              开始试跑
            </Button>

            {trialRunning && (
              <div className="ci-empty-panel">
                <Spin tip="AI 正在分析..." />
              </div>
            )}

            {trialResult && (
              <Space direction="vertical" size={12} style={{ width: '100%' }}>
                <Divider style={{ margin: 0 }} />
                <Row gutter={12}>
                  <Col span={8}>
                    <Card size="small">
                      <Statistic title="输入 Token" value={trialResult.inputTokens} />
                    </Card>
                  </Col>
                  <Col span={8}>
                    <Card size="small">
                      <Statistic title="输出 Token" value={trialResult.outputTokens} />
                    </Card>
                  </Col>
                  <Col span={8}>
                    <Card size="small">
                      <Statistic title="耗时" value={trialResult.durationMs} suffix="ms" />
                    </Card>
                  </Col>
                </Row>
                {trialResult.errorReason ? (
                  <Alert type="error" showIcon message="分析失败" description={trialResult.errorReason} />
                ) : (
                  <div className="ci-editor-shell" style={{ border: '1px solid #e3e6ed', borderRadius: '8px', overflow: 'hidden', width: '100%' }}>
                    <div style={{ padding: '8px 12px', background: '#f8f9fb', borderBottom: '1px solid #e3e6ed', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <Text strong style={{ fontSize: '13px' }}>AI 归纳结果输出</Text>
                      <Button size="small" icon={<CopyOutlined />} onClick={() => {
                        navigator.clipboard.writeText(trialResult.result);
                        message.success('试跑结果已复制到剪贴板');
                      }}>
                        复制结果
                      </Button>
                    </div>
                    <pre className="ci-result-preview" style={{ maxHeight: '520px', border: 'none', borderRadius: 0, margin: 0, background: '#ffffff' }}>
                      {trialResult.result}
                    </pre>
                  </div>
                )}
              </Space>
            )}
          </Space>
        ) : (
          <Empty description="请选择一个提示词模板，并使用示例 Java 代码进行试跑。" />
        )}
      </Modal>

      <Modal
        title={editingPrompt ? `编辑提示词：将生成 v${editingPrompt.version + 1}` : '创建提示词'}
        open={promptModalOpen}
        onOk={handlePromptSubmit}
        onCancel={() => setPromptModalOpen(false)}
        width={760}
        destroyOnClose
      >
        <Form form={promptForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label="提示词名称" rules={[{ required: true, message: '请输入提示词名称' }]}>
            <Input placeholder="Java 方法知识归纳" />
          </Form.Item>
          <Form.Item name="content" label="提示词内容" rules={[{ required: true, message: '请输入提示词内容' }]}>
            <Input.TextArea autoSize={{ minRows: 14, maxRows: 24 }} className="ci-code-input" />
          </Form.Item>
          <Form.Item name="isDefault" valuePropName="checked">
            <Switch checkedChildren="默认" unCheckedChildren="普通" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default Prompts;
