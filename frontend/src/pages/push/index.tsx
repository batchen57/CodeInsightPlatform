import React, { useCallback, useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Alert, Button, Card, Descriptions, Form, Input, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import { CloudUploadOutlined, DownloadOutlined, PlusOutlined, PullRequestOutlined, ReloadOutlined } from '@ant-design/icons';
import { createVersion, listVersions, pushVersion, type KnowledgeVersion } from '../../api/knowledge';
import { listSystems } from '../../api/system';
import { listTasks } from '../../api/task';
import type { System, Task } from '../../types';

const { Text } = Typography;

const statusMeta: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '待推送' },
  PUSHING: { color: 'processing', label: '推送中' },
  PUSHED: { color: 'success', label: '已推送' },
  FAILED: { color: 'error', label: '失败' },
};

const taskStatusLabel: Record<string, string> = {
  PENDING_REVIEW: '待复核',
  REVIEWING: '复核中',
  CONFIRMED: '已确认',
};

const Push: React.FC = () => {
  const location = useLocation();
  const [versions, setVersions] = useState<KnowledgeVersion[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);

  const [systems, setSystems] = useState<System[]>([]);
  const [selectedSystemId, setSelectedSystemId] = useState<number | undefined>(
    location.state?.systemId ? Number(location.state.systemId) : undefined
  );
  const [tasks, setTasks] = useState<Task[]>([]);

  const [versionModalOpen, setVersionModalOpen] = useState(false);
  const [versionForm] = Form.useForm();
  const [mrModalOpen, setMrModalOpen] = useState(false);
  const [activeVersion, setActiveVersion] = useState<KnowledgeVersion | null>(null);
  const [mrForm] = Form.useForm();

  useEffect(() => {
    listSystems({ current: 1, size: 100, status: 1 }).then((data) => setSystems(data.records));
  }, []);

  const fetchVersions = useCallback(
    async (page = current, pageSize = size) => {
      setLoading(true);
      try {
        const data = await listVersions({ current: page, size: pageSize, systemId: selectedSystemId });
        setVersions(data.records);
        setTotal(data.total);
      } finally {
        setLoading(false);
      }
    },
    [current, selectedSystemId, size],
  );

  useEffect(() => {
    fetchVersions();
  }, [fetchVersions]);

  const openVersionModal = async () => {
    if (!selectedSystemId) {
      message.warning('请先选择系统再创建版本');
      return;
    }
    const data = await listTasks({ current: 1, size: 100, systemId: selectedSystemId });
    setTasks(data.records.filter((task) => ['PENDING_REVIEW', 'CONFIRMED'].includes(task.status)));
    setVersionModalOpen(true);
  };

  const handleCreateVersion = async () => {
    const values = await versionForm.validateFields();
    await createVersion(values.taskId, values.versionNum, 'Admin');
    message.success('知识版本已创建');
    setVersionModalOpen(false);
    versionForm.resetFields();
    fetchVersions();
  };

  const handlePushGit = async (versionId: number) => {
    message.loading({ content: '正在执行推送前校验并推送 Git...', key: 'pushing' });
    try {
      await pushVersion(versionId);
      message.success({ content: '知识已推送到 Git', key: 'pushing' });
      fetchVersions();
    } catch {
      message.error({ content: '推送失败', key: 'pushing' });
    }
  };

  const handleDownloadZip = (versionId: number) => {
    const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api';
    window.open(`${baseUrl}/knowledge/${versionId}/export`);
    message.success('ZIP 导出已开始');
  };

  const handleCreateMr = async () => {
    await mrForm.validateFields();
    message.success(`合并请求已为 ${activeVersion?.versionNum} 准备完成`);
    setMrModalOpen(false);
    mrForm.resetFields();
  };

  const columns = [
    {
      title: '版本',
      dataIndex: 'versionNum',
      key: 'versionNum',
      render: (text: string) => <Text strong>{text}</Text>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        const meta = statusMeta[status] ?? { color: 'default', label: status };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    { title: '源分支', dataIndex: 'sourceBranch', key: 'sourceBranch' },
    {
      title: '源 Commit',
      dataIndex: 'sourceCommit',
      key: 'sourceCommit',
      render: (commit: string) => <Text code>{commit?.substring(0, 8) || '-'}</Text>,
    },
    {
      title: '目标 Commit',
      dataIndex: 'targetCommit',
      key: 'targetCommit',
      render: (commit: string | null) => (commit ? <Text code>{commit.substring(0, 8)}</Text> : <Text type="secondary">-</Text>),
    },
    { title: '确认人', dataIndex: 'confirmedBy', key: 'confirmedBy' },
    {
      title: '推送时间',
      dataIndex: 'pushedAt',
      key: 'pushedAt',
      render: (text: string | null) => (text ? new Date(text).toLocaleString() : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      fixed: 'right' as const,
      render: (_: unknown, record: KnowledgeVersion) => (
        <Space size={8}>
          {record.status === 'DRAFT' || record.status === 'FAILED' ? (
            <Button type="primary" size="small" icon={<CloudUploadOutlined />} onClick={() => handlePushGit(record.id)}>
              推送 Git
            </Button>
          ) : (
            <Button
              size="small"
              icon={<PullRequestOutlined />}
              onClick={() => {
                setActiveVersion(record);
                setMrModalOpen(true);
              }}
            >
              创建 MR
            </Button>
          )}
          <Button size="small" icon={<DownloadOutlined />} onClick={() => handleDownloadZip(record.id)}>
            ZIP
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div className="ci-page ci-push-page">
      <Alert
        className="ci-guardrail-alert"
        type="info"
        showIcon
        message="推送防线"
        description="只有经过人工复核确认的知识才能推送。推送前校验会检查任务状态、草稿状态、生成元数据和导出包完整性。"
      />

      <Card
        className="ci-workspace-card ci-push-console"
        title="版本推送控制台"
        extra={
          <Space wrap>
            <Select
              style={{ width: 220 }}
              placeholder="筛选系统"
              value={selectedSystemId}
              onChange={setSelectedSystemId}
              allowClear
              options={systems.map((system) => ({ value: system.id, label: system.name }))}
            />
            <Button type="primary" icon={<PlusOutlined />} onClick={openVersionModal}>
              新建版本
            </Button>
            <Button icon={<ReloadOutlined />} onClick={() => fetchVersions()}>
              刷新
            </Button>
          </Space>
        }
      >
        <Table
          dataSource={versions}
          columns={columns}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1280 }}
          pagination={{
            current,
            pageSize: size,
            total,
            showSizeChanger: true,
            onChange: (page, pageSize) => {
              setCurrent(page);
              setSize(pageSize);
            },
          }}
        />
      </Card>

      <Modal
        title="创建知识版本"
        open={versionModalOpen}
        onOk={handleCreateVersion}
        onCancel={() => {
          setVersionModalOpen(false);
          versionForm.resetFields();
        }}
        destroyOnHidden
      >
        <Form form={versionForm} layout="vertical">
          <Form.Item name="taskId" label="已确认任务" rules={[{ required: true, message: '请选择任务' }]}>
            <Select
              placeholder="请选择任务"
              options={tasks.map((task) => ({
                value: task.id,
                label: `任务 #${task.id} / ${task.type === 'INITIAL' ? '全量' : '增量'} / ${taskStatusLabel[task.status] ?? task.status}`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="versionNum"
            label="版本号"
            rules={[
              { required: true, message: '请输入版本号' },
              { pattern: /^v\d+\.\d+\.\d+$/, message: '请使用语义化版本格式，例如 v1.0.0' },
            ]}
          >
            <Input placeholder="v1.0.0" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="创建合并请求"
        open={mrModalOpen}
        onOk={handleCreateMr}
        onCancel={() => {
          setMrModalOpen(false);
          mrForm.resetFields();
        }}
        destroyOnHidden
      >
        <Descriptions bordered column={1} size="small" style={{ marginBottom: 16 }}>
          <Descriptions.Item label="版本">{activeVersion?.versionNum}</Descriptions.Item>
          <Descriptions.Item label="源分支">{activeVersion?.targetBranch}</Descriptions.Item>
          <Descriptions.Item label="提交">{activeVersion?.targetCommit?.substring(0, 8) || '-'}</Descriptions.Item>
        </Descriptions>
        <Form form={mrForm} layout="vertical">
          <Form.Item name="targetBranch" label="目标分支" initialValue="main" rules={[{ required: true }]}>
            <Select
              options={[
                { value: 'main', label: 'main' },
                { value: 'master', label: 'master' },
                { value: 'develop', label: 'develop' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="mrTitle"
            label="合并请求标题"
            initialValue={`docs: 合并代码洞察知识 ${activeVersion?.versionNum || ''}`}
            rules={[{ required: true }]}
          >
            <Input />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default Push;
