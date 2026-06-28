import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Descriptions, Modal, Progress, Row, Space, Steps, Statistic, Tag, Typography, message } from 'antd';
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  EditOutlined,
  FileSearchOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  SwapOutlined,
} from '@ant-design/icons';
import { useNavigate, useParams } from 'react-router-dom';
import { getTask, getTaskExecutionLog, retryTask, startTask, terminateTask } from '../../api/task';
import { getSystem } from '../../api/system';
import type { System, Task } from '../../types';


const { Text, Title } = Typography;

// 运行中的核心状态列表
// 包含 MODULE_HIERARCHY_REVIEW：处于人工调试断点时也要轮询状态，提交保存并继续后会跳到 GENERATING_DOC
const runningStatuses = ['PENDING', 'PULLING_CODE', 'PARSING_CODE', 'SPLITTING_TASK', 'AI_ANALYZING', 'MODULE_HIERARCHY_REVIEW', 'GENERATING_DOC', 'PUSHING'];

// 任务执行管道阶段步骤索引与展示元数据配置说明
// 「模块层级复核」位于「AI 分析」与「生成文档」之间（step 5），是 MODULE_HIERARCHY_REVIEW 状态对应的可视化阶段
const statusMeta: Record<string, { color: string; label: string; step: number }> = {
  DRAFT: { color: 'default', label: '草稿', step: -1 },
  PENDING: { color: 'blue', label: '排队中', step: 0 },
  PULLING_CODE: { color: 'blue', label: '拉取代码', step: 1 },
  PARSING_CODE: { color: 'cyan', label: '解析代码', step: 2 },
  SPLITTING_TASK: { color: 'purple', label: '任务切片', step: 3 },
  AI_ANALYZING: { color: 'orange', label: 'AI 分析中', step: 4 },
  MODULE_HIERARCHY: { color: 'gold', label: '模块层级提炼', step: 4 },
  MODULE_HIERARCHY_REVIEW: { color: 'geekblue', label: '模块层级复核', step: 5 },
  GENERATING_DOC: { color: 'gold', label: '生成文档', step: 6 },
  PENDING_REVIEW: { color: 'magenta', label: '待复核', step: 7 },
  REVIEWING: { color: 'geekblue', label: '复核中', step: 7 },
  CONFIRMED: { color: 'green', label: '已确认', step: 7 },
  PUSHING: { color: 'purple', label: '推送中', step: 7 },
  PUSHED: { color: 'green', label: '已推送', step: 7 },
  FAILED: { color: 'red', label: '失败', step: -1 },
  CANCELLED: { color: 'default', label: '已取消', step: -1 },
};

/**
 * 任务执行详情监控组件 (TaskDetail)
 * 展示任务的静态配置（负责人、代码库 ID、模型、耗时及日志存储路径）、
 * 串联 Steps 指引任务当前流转到哪一步骤，并在底部提供流式模拟终端日志呈现和 Token 预估分析栏。
 */
const TaskDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const taskId = Number(id);

  // 任务实体数据及所属系统实体
  const [task, setTask] = useState<Task | null>(null);
  const [system, setSystem] = useState<System | null>(null);
  
  // 数据加载 loading 与操作按钮的 actionLoading 状态
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);

  // 卡片执行日志内容
  const [cardLogContent, setCardLogContent] = useState('');

  const loadCardLog = useCallback(async () => {
    if (!taskId) return;
    try {
      const content = await getTaskExecutionLog(taskId);
      setCardLogContent(content || '');
    } catch {
      setCardLogContent('');
    }
  }, [taskId]);

  // 任务加载时 + 运行中轮询时刷新卡片日志
  useEffect(() => {
    loadCardLog();
  }, [loadCardLog]);

  useEffect(() => {
    if (!task || !runningStatuses.includes(task.status)) return;
    const timer = window.setInterval(loadCardLog, 2500);
    return () => window.clearInterval(timer);
  }, [loadCardLog, task]);

  // 执行日志弹窗
  const [logModalOpen, setLogModalOpen] = useState(false);
  const [execLogContent, setExecLogContent] = useState('');
  const [logLoading, setLogLoading] = useState(false);

  const openExecLog = async () => {
    if (!taskId) return;
    setLogModalOpen(true);
    setLogLoading(true);
    try {
      const content = await getTaskExecutionLog(taskId);
      setExecLogContent(content || '(暂无日志)');
    } catch {
      setExecLogContent('(加载失败)');
    } finally {
      setLogLoading(false);
    }
  };
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
      } // 忽略捕获以依靠全局 Axios 异常拦截
      finally {
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
      {/* 头部面板与快捷动作操作条 */}
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
            <Button icon={<FileSearchOutlined />} onClick={() => navigate(`/logs?taskId=${task.id}`)}>
              查看日志
            </Button>
          </Space>
        </div>
      </Card>

      {/* 任务管道当前阶段可视化 Steps 指引 */}
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
            { title: '模块层级复核' },
            { title: '生成文档' },
            { title: '复核' },
          ]}
        />
      </Card>

      {/* 模块层级复核提示：统一跳转到「模块层级复核」专用页面处理 */}
      {task.status === 'MODULE_HIERARCHY_REVIEW' && (
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="模块层级需要人工复核"
          description="AI 已完成模块层级提炼，需要您复核并确认模块与功能结构。请点击下方按钮前往「模块层级复核」页面集中处理。"
          action={
            <Button type="primary" icon={<SwapOutlined />} onClick={() => navigate('/tasks/hierarchy-review')}>
              前往模块层级复核
            </Button>
          }
        />
      )}

      {/* 草稿就绪提示横幅 */}
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

      {/* 任务失败错误提示框 */}
      {task.errorReason && (
        <Alert
          type="error"
          showIcon
          message="执行错误"
          description={task.errorReason}
          action={
            <Button size="small" icon={<FileSearchOutlined />} onClick={() => navigate(`/logs?taskId=${task.id}`)}>
              查看日志
            </Button>
          }
        />
      )}

      <Row gutter={[16, 16]}>
        {/* 左侧：任务静态指标表格 */}
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

        {/* 右侧：执行进程日志（全部内容可在卡片内滚动） */}
        <Col xs={24} xl={12}>
          <Card
            title="执行日志"
            extra={
              <Button size="small" icon={<FileSearchOutlined />} onClick={openExecLog}>
                查看完整日志
              </Button>
            }
            style={{ height: '100%' }}
            styles={{ body: { padding: '12px 16px' } }}
          >
            <pre
              className="ci-terminal"
              style={{
                fontSize: 12,
                margin: 0,
                maxHeight: 380,
                overflow: 'auto',
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-all',
                background: '#1e1e1e',
                color: '#d4d4d4',
                padding: 8,
                borderRadius: 4,
              }}
            >
              {cardLogContent || '暂无日志'}
            </pre>
          </Card>
        </Col>
      </Row>

      {/* 底部统计及预估栏目 */}
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

      <Modal
        title={`执行日志 — 任务 #${task.id}`}
        open={logModalOpen}
        onCancel={() => setLogModalOpen(false)}
        width={800}
        footer={<Button onClick={() => setLogModalOpen(false)}>关闭</Button>}
        destroyOnClose
      >
        {logLoading ? (
          <Card loading style={{ minHeight: 200 }} />
        ) : (
          <pre
            style={{
              fontSize: 12,
              maxHeight: 480,
              overflow: 'auto',
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-all',
              margin: 0,
              background: '#1e1e1e',
              color: '#d4d4d4',
              padding: 12,
              borderRadius: 4,
            }}
          >
            {execLogContent}
          </pre>
        )}
      </Modal>
    </div>
  );
};

export default TaskDetail;
