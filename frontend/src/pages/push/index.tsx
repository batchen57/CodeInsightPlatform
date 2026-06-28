import React, { useCallback, useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { Alert, Button, Card, Descriptions, Form, Input, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import { CloudUploadOutlined, DownloadOutlined, PlusOutlined, PullRequestOutlined, ReloadOutlined } from '@ant-design/icons';
import { createVersion, listVersions, pushVersion, listPushTasks, type KnowledgeVersion, type PushTask } from '../../api/knowledge';
import { listSystems } from '../../api/system';
import { listTasks } from '../../api/task';
import type { System, Task } from '../../types';
import { getCurrentOperator } from '../../api/auth';

const { Text } = Typography;

const statusMeta: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '待推送' },
  PUSHING: { color: 'processing', label: '推送中' },
  PUSHED: { color: 'success', label: '已推送' },
  FAILED: { color: 'error', label: '失败' },
};

const pushTaskStatusMeta: Record<string, { color: string; label: string }> = {
  PENDING: { color: 'default', label: '排队中' },
  PROCESSING: { color: 'processing', label: '执行中' },
  SUCCESS: { color: 'success', label: '成功' },
  FAILED: { color: 'error', label: '失败' },
};

const pushMethodLabel: Record<string, string> = {
  GIT: 'Git 推送',
  S3: 'S3 推送',
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
  const [pushTasks, setPushTasks] = useState<Map<number, PushTask[]>>(new Map());
  const [pushingVersions, setPushingVersions] = useState<Set<number>>(new Set());

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

  // Auto-refresh push tasks for versions currently in PUSHING status
  useEffect(() => {
    if (pushingVersions.size === 0) return;
    const interval = setInterval(async () => {
      const newTasks = new Map(pushTasks);
      let hasActive = false;
      for (const versionId of pushingVersions) {
        try {
          const tasks = await listPushTasks(versionId);
          newTasks.set(versionId, tasks);
          // Check if any task is still active
          const hasPending = tasks.some(
            (t) => t.status === 'PENDING' || t.status === 'PROCESSING'
          );
          if (hasPending) hasActive = true;
        } catch {
          // ignore fetch errors
        }
      }
      setPushTasks(newTasks);
      if (!hasActive) {
        setPushingVersions(new Set());
        fetchVersions(); // refresh version list to get final status
      }
    }, 3000);
    return () => clearInterval(interval);
  }, [pushingVersions, pushTasks, fetchVersions]);

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
    await createVersion(values.taskId, values.versionNum, getCurrentOperator());
    message.success('知识版本已创建');
    setVersionModalOpen(false);
    versionForm.resetFields();
    fetchVersions();
  };

  const handlePush = async (versionId: number, method: string = 'GIT') => {
    const version = versions.find((v) => v.id === versionId);
    const methodLabel = pushMethodLabel[method] || method;
    Modal.confirm({
      title: `推送版本（${methodLabel}）？`,
      content: (
        <div>
          <p style={{ marginBottom: 6 }}>
            即将通过 <Text strong>{methodLabel}</Text> 推送版本 <Text strong>{version?.versionNum ?? `#${versionId}`}</Text>。
            此操作不可撤销。
          </p>
          <p style={{ marginBottom: 0, fontSize: 12, color: '#818aa0' }}>
            推送任务将进入队列异步执行。推送后该任务将被锁定，无法继续修改。
          </p>
        </div>
      ),
      okText: '加入推送队列',
      cancelText: '取消',
      onOk: async () => {
        message.loading({ content: '推送任务加入队列...', key: 'pushing' });
        try {
          await pushVersion(versionId, method);
          message.success({ content: '推送任务已加入队列，后台异步执行中', key: 'pushing', duration: 3 });
          setPushingVersions((prev) => new Set(prev).add(versionId));
          fetchVersions();
        } catch {
          message.error({ content: '推送任务提交失败', key: 'pushing' });
        }
      },
    });
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
      width: 100,
      render: (text: string) => <Text strong>{text}</Text>,
    },
    {
      title: '版本状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (status: string) => {
        const meta = statusMeta[status] ?? { color: 'default', label: status };
        return <Tag color={meta.color}>{meta.label}</Tag>;
      },
    },
    {
      title: '推送方式',
      dataIndex: 'pushMethod',
      key: 'pushMethod',
      width: 90,
      render: (method: string) => pushMethodLabel[method] || method || 'GIT',
    },
    {
      title: '队列状态',
      key: 'pushTaskStatus',
      width: 100,
      render: (_: unknown, record: KnowledgeVersion) => {
        const tasks = pushTasks.get(record.id);
        if (!tasks || tasks.length === 0) return <Text type="secondary">-</Text>;
        const latest = tasks[0];
        const meta = pushTaskStatusMeta[latest.status] ?? { color: 'default', label: latest.status };
        return (
          <Space size={4}>
            <Tag color={meta.color}>{meta.label}</Tag>
            {latest.retryCount > 0 && (
              <Text type="secondary" style={{ fontSize: 11 }}>
                ({latest.retryCount}/{latest.maxRetries})
              </Text>
            )}
          </Space>
        );
      },
    },
    { title: '源分支', dataIndex: 'sourceBranch', key: 'sourceBranch', width: 90 },
    {
      title: '源 Commit',
      dataIndex: 'sourceCommit',
      key: 'sourceCommit',
      width: 100,
      render: (commit: string) => <Text code>{commit?.substring(0, 8) || '-'}</Text>,
    },
    {
      title: '目标 Commit',
      dataIndex: 'targetCommit',
      key: 'targetCommit',
      width: 100,
      render: (commit: string | null) => (commit ? <Text code>{commit.substring(0, 8)}</Text> : <Text type="secondary">-</Text>),
    },
    { title: '确认人', dataIndex: 'confirmedBy', key: 'confirmedBy', width: 80 },
    {
      title: '推送时间',
      dataIndex: 'pushedAt',
      key: 'pushedAt',
      width: 150,
      render: (text: string | null) => (text ? new Date(text).toLocaleString() : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 260,
      fixed: 'right' as const,
      render: (_: unknown, record: KnowledgeVersion) => {
        const isPushing = pushingVersions.has(record.id);
        return (
          <Space size={8}>
            {record.status === 'DRAFT' || record.status === 'FAILED' ? (
              <Button
                type="primary"
                size="small"
                icon={<CloudUploadOutlined />}
                loading={isPushing}
                onClick={() => handlePush(record.id, 'GIT')}
              >
                推送 Git
              </Button>
            ) : record.status === 'PUSHING' ? (
              <Button size="small" loading disabled>
                推送中...
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
        );
      },
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
