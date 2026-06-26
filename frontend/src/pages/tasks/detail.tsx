import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Col, Descriptions, Progress, Row, Space, Steps, Statistic, Tag, Typography, message } from 'antd';
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  CodeOutlined,
  EditOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import { getTask, retryTask, startTask, terminateTask } from '../../api/task';
import { getSystem } from '../../api/system';
import type { System, Task } from '../../types';

const { Text, Title } = Typography;

const runningStatuses = ['PENDING', 'PULLING_CODE', 'PARSING_CODE', 'SPLITTING_TASK', 'AI_ANALYZING', 'GENERATING_DOC', 'PUSHING'];

// 任务执行管道阶段步骤索引与展示元数据配置
const statusMeta: Record<string, { color: string; label: string; step: number }> = {
  DRAFT: { color: 'default', label: '草稿', step: -1 },
  PENDING: { color: 'blue', label: '排队中', step: 0 },
  PULLING_CODE: { color: 'blue', label: '拉取代码', step: 1 },
  PARSING_CODE: { color: 'cyan', label: '解析代码', step: 2 },
  SPLITTING_TASK: { color: 'purple', label: '任务切片', step: 3 },
  AI_ANALYZING: { color: 'orange', label: 'AI 分析中', step: 4 },
  GENERATING_DOC: { color: 'gold', label: '生成文档', step: 5 },
  PENDING_REVIEW: { color: 'magenta', label: '待复核', step: 6 },
  REVIEWING: { color: 'geekblue', label: '复核中', step: 6 },
  CONFIRMED: { color: 'green', label: '已确认', step: 6 },
  PUSHING: { color: 'purple', label: '推送中', step: 6 },
  PUSHED: { color: 'green', label: '已推送', step: 6 },
  FAILED: { color: 'red', label: '失败', step: -1 },
  CANCELLED: { color: 'default', label: '已取消', step: -1 },
};

const TaskDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const taskId = Number(id);

  const [task, setTask] = useState<Task | null>(null);
  const [system, setSystem] = useState<System | null>(null);
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);

  // 获取任务详情及关联的系统基本元数据
  const fetchTaskDetails = useCallback(
    async (showLoading = true) => {
      if (!taskId) {
        return;
      }
      if (showLoading) {
        setLoading(true);
      }
      try {
        const taskData = await getTask(taskId);
        setTask(taskData);
        const systemData = await getSystem(taskData.systemId);
        setSystem(systemData);
      } finally {
        if (showLoading) {
          setLoading(false);
        }
      }
    },
    [taskId],
  );

  useEffect(() => {
    fetchTaskDetails();
  }, [fetchTaskDetails]);

  /**
   * 启动任务轮询机制
   * 若任务处于运行态，则每 2.5 秒刷新一次最新进度与状态，在组件卸载或任务结束时自动销毁定时器。
   */
  useEffect(() => {
    if (!task || !runningStatuses.includes(task.status)) {
      return;
    }
    const timer = window.setInterval(() => fetchTaskDetails(false), 2500);
    return () => window.clearInterval(timer);
  }, [fetchTaskDetails, task]);

  // 包装各类流程操作事件（启动/重跑/终止），并在操作完成后自动重载数据
  const runAction = async (action: () => Promise<void>, success: string) => {
    if (!task) {
      return;
    }
    setActionLoading(true);
    try {
      await action();
      message.success(success);
      fetchTaskDetails();
    } finally {
      setActionLoading(false);
    }
  };

  const meta = task ? statusMeta[task.status] ?? { color: 'default', label: task.status, step: -1 } : null;

  /**
   * 依据当前任务状态及基本属性，动态拼接控制台输出的模拟状态日志
   */
  const logLines = useMemo(() => {
    if (!task) {
      return [];
    }
    const base = [
      `[task:${task.id}] type=${task.type} status=${task.status}`,
      `[system:${task.systemId}] ${system?.name ?? '正在加载系统元数据'}`,
      `[progress] 已完成 ${task.progress}%`,
    ];
    if (task.status === 'DRAFT') {
      return [...base, '[next] 启动任务后将进入执行队列'];
    }
    if (runningStatuses.includes(task.status)) {
      return [...base, '[worker] 执行管道运行中，进度正在刷新'];
    }
    if (task.status === 'FAILED') {
      return [...base, `[error] ${task.errorReason || '未知失败'}`];
    }
    if (['PENDING_REVIEW', 'REVIEWING', 'CONFIRMED', 'PUSHED'].includes(task.status)) {
      return [...base, '[result] Markdown 草稿已生成，可进入复核或版本推送'];
    }
    return base;
  }, [system?.name, task]);

  if (loading) {
    return <Card loading style={{ minHeight: 420 }} />;
  }

  if (!task || !meta) {
    return (
      <Alert
        type="error"
        showIcon
        message="任务不存在"
        description={`未找到任务 #${taskId}。`}
        action={<Button onClick={() => navigate('/tasks')}>返回任务列表</Button>}
      />
    );
  }

  return (
    <div className="ci-page ci-task-detail-page">
      <Card>
        <div className="ci-detail-header">
          <Space wrap>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/tasks')}>
              返回
            </Button>
            <div>
              <Title level={4}>反编译任务 #{task.id}</Title>
              <Text type="secondary">{system?.name ?? `系统 #${task.systemId}`}</Text>
            </div>
            <Tag color={meta.color}>{meta.label}</Tag>
            <Tag color={task.type === 'INITIAL' ? 'geekblue' : 'green'}>{task.type === 'INITIAL' ? '全量扫描' : '增量扫描'}</Tag>
          </Space>

          <Space wrap>
            {task.status === 'DRAFT' && (
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                loading={actionLoading}
                onClick={() => runAction(() => startTask(task.id), '任务已启动')}
              >
                启动
              </Button>
            )}
            {runningStatuses.includes(task.status) && (
              <Button
                danger
                icon={<CloseCircleOutlined />}
                loading={actionLoading}
                onClick={() => runAction(() => terminateTask(task.id), '终止请求已发送')}
              >
                终止
              </Button>
            )}
            {['FAILED', 'CANCELLED'].includes(task.status) && (
              <Button
                type="primary"
                icon={<ReloadOutlined />}
                loading={actionLoading}
                onClick={() => runAction(() => retryTask(task.id), '任务已重新启动')}
              >
                重试
              </Button>
            )}
            {['PENDING_REVIEW', 'REVIEWING', 'CONFIRMED'].includes(task.status) && (
              <Button type="primary" icon={<EditOutlined />} onClick={() => navigate('/drafts')}>
                复核草稿
              </Button>
            )}
          </Space>
        </div>
      </Card>

      <Card title="执行流程状态">
        <Steps
          size="small"
          current={meta.step}
          status={task.status === 'FAILED' ? 'error' : 'process'}
          items={[
            { title: '排队' },
            { title: '拉取代码' },
            { title: '静态解析' },
            { title: '切片' },
            { title: 'AI 分析' },
            { title: '生成文档' },
            { title: '复核' },
          ]}
        />
      </Card>

      {['PENDING_REVIEW', 'REVIEWING', 'CONFIRMED'].includes(task.status) && (
        <Alert
          type="success"
          showIcon
          message="知识草稿已就绪"
          description="生成的 Markdown 已进入平台草稿区，仍需人工复核后才能成为确认的知识版本。"
          action={
            <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => navigate('/drafts')}>
              打开复核
            </Button>
          }
        />
      )}

      {task.errorReason && <Alert type="error" showIcon message="执行错误" description={task.errorReason} />}

      <Row gutter={[16, 16]}>
        <Col xs={24} xl={12}>
          <Card title="任务配置" style={{ height: '100%' }}>
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="业务系统">{system?.name ?? `系统 #${task.systemId}`}</Descriptions.Item>
              <Descriptions.Item label="负责人">{system?.owner || '-'}</Descriptions.Item>
              <Descriptions.Item label="代码库 ID">{task.repositoryId}</Descriptions.Item>
              <Descriptions.Item label="提示词版本">v{task.promptVersion || 1}</Descriptions.Item>
              <Descriptions.Item label="AI模型">{task.modelName || '-'}</Descriptions.Item>
              <Descriptions.Item label="进度">
                <Progress percent={task.progress} size="small" status={task.status === 'FAILED' ? 'exception' : 'active'} />
              </Descriptions.Item>
              <Descriptions.Item label="耗时">{task.durationMs ? `${(task.durationMs / 1000).toFixed(1)} 秒` : '-'}</Descriptions.Item>
              <Descriptions.Item label="开始时间">{task.startedAt ? new Date(task.startedAt).toLocaleString() : '-'}</Descriptions.Item>
              <Descriptions.Item label="结束时间">{task.endedAt ? new Date(task.endedAt).toLocaleString() : '-'}</Descriptions.Item>
              <Descriptions.Item label="日志 URI">
                <Text code>{task.logUri || `local://storage/tasks/${task.id}.log`}</Text>
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        <Col xs={24} xl={12}>
          <Card title="执行日志" extra={<CodeOutlined />} style={{ height: '100%' }}>
            <pre className="ci-terminal">{logLines.join('\n')}</pre>
          </Card>
        </Col>
      </Row>

      {['PENDING_REVIEW', 'REVIEWING', 'CONFIRMED', 'PUSHED'].includes(task.status) && (
        <div className="ci-kpi-grid">
          <Card size="small">
            <Statistic title="预估切片数" value={65} />
          </Card>
          <Card size="small">
            <Statistic title="AI 调用数" value={65} />
          </Card>
          <Card size="small">
            <Statistic title="Token 预估" value={98420} />
          </Card>
          <Card size="small">
            <Statistic title="成本预估" value={0.59} prefix="$" precision={2} />
          </Card>
        </div>
      )}
    </div>
  );
};

export default TaskDetail;
