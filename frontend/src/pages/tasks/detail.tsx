import React, { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Descriptions, Modal, Progress, Row, Space, Steps, Statistic, Tag, Timeline, Typography, message } from 'antd';
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
import { getTask, getTaskExecutionLog, getTaskLogSummary, retryTask, startTask, terminateTask } from '../../api/task';
import { getSystem } from '../../api/system';
import type { PipelineStageStat, System, Task, TaskLogSummary } from '../../types';

/** 构造携带当前任务上下文（systemId + taskId）的复核页跳转链接 */
const buildDraftsHref = (task: Task) => `/drafts/${task.id}`;


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

  // 结构化日志摘要：阶段耗时、文件/切片计数、AI 成功/失败数、Mock 标记、当前进度
  const [summary, setSummary] = useState<TaskLogSummary | null>(null);

  const loadSummary = useCallback(async () => {
    if (!taskId) return;
    try {
      const data = await getTaskLogSummary(taskId);
      setSummary(data);
    } catch {
      // 静默失败：保留上一次成功拉到的摘要，避免运行中一闪而过
    }
  }, [taskId]);

  // 与 raw 卡片日志同步轮询，避免额外定时器漂移
  useEffect(() => {
    loadSummary();
  }, [loadSummary]);

  useEffect(() => {
    if (!task || !runningStatuses.includes(task.status)) return;
    const timer = window.setInterval(loadSummary, 2500);
    return () => window.clearInterval(timer);
  }, [loadSummary, task]);

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

/** 把单个阶段统计转成 antd Timeline 的 item 配置 */
const timelineItem = (s: PipelineStageStat) => {
  const color =
    s.status === 'done' ? 'green'
    : s.status === 'running' ? 'blue'
    : s.status === 'error' ? 'red'
    : 'gray';
  const suffix =
    s.status === 'running' ? ' · 进行中'
    : s.status === 'pending' ? ' · 待开始'
    : s.status === 'error' ? ' · 异常'
    : s.status === 'skipped' ? ' · 已跳过'
    : '';
  const duration = s.durationMs && s.durationMs > 0
    ? `耗时 ${(s.durationMs / 1000).toFixed(1)} 秒`
    : '—';
  return {
    color,
    children: (
      <Space direction="vertical" size={0}>
        <Text strong>{s.label}{suffix}</Text>
        <Text type="secondary" style={{ fontSize: 12 }}>{duration}</Text>
      </Space>
    ),
  };
};

  // 当前进行中的阶段中文名（用于 Timeline 中高亮提示 + 友好提示的默认值）
  const currentStageLabel = summary?.pipeline?.find((s) => s.status === 'running')?.label ?? '';

  // 友好提示：失败时不暴露具体异常，仅指向"查看完整日志"
  const friendlyHint = (() => {
    if (!task) return '';
    const durMs = summary?.durationMs || task.durationMs || 0;
    const sec = durMs > 0 ? (durMs / 1000).toFixed(1) : '0.0';
    if (task.status === 'FAILED') {
      return '任务失败，请查看完整日志';
    }
    if (['PUSHED', 'CANCELLED', 'ARCHIVED'].includes(task.status)) {
      return `任务已结束 · 累计耗时 ${sec} 秒`;
    }
    if (['PENDING_REVIEW', 'REVIEWING', 'CONFIRMED'].includes(task.status)) {
      return `等待人工复核 · 累计耗时 ${sec} 秒`;
    }
    const done = summary?.pipeline?.filter((s) => s.status === 'done' || s.status === 'skipped').length ?? 0;
    const total = summary?.pipeline?.length ?? 8;
    return `正在：${currentStageLabel || meta?.label || task.status} · 已完成 ${done}/${total} 阶段 · 累计 ${sec} 秒`;
  })();

  // Mock 模式 / 真实模型文案
  const aiModeLabel = summary?.aiMock ? 'Mock 模式' : '真实模型';
  const aiModeColor = summary?.aiMock ? 'gold' : 'geekblue';

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
        action={<Button onClick={() => navigate('/tasks')}>返回手动下发</Button>}
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
              <Title level={4}>知识构建任务 #{task.id}</Title>
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
              <Button type="primary" icon={<EditOutlined />} onClick={() => navigate(buildDraftsHref(task))}>
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
            <Button type="primary" icon={<CheckCircleOutlined />} onClick={() => navigate(buildDraftsHref(task))}>
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
                <Text code>{`local://storage/task_${task.id}/pipeline.log`}</Text>
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        {/* 右侧：执行日志卡片（结构化概览；无异常堆栈） */}
        <Col xs={24} xl={12}>
          <Card
            title="执行日志"
            extra={
              <Space size={4} wrap>
                <Tag color={aiModeColor}>{aiModeLabel}</Tag>
                <Tag color="purple">{summary?.modelName || task.modelName || '未指定模型'}</Tag>
                <Button size="small" icon={<FileSearchOutlined />} onClick={openExecLog}>
                  查看完整日志
                </Button>
              </Space>
            }
            style={{ height: '100%' }}
            styles={{ body: { padding: '12px 16px' } }}
          >
            {/* 1. 状态条 + 友好提示 */}
            <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 8 }} wrap>
              <Space size={6}>
                <Tag color={meta.color}>{meta.label}</Tag>
                {currentStageLabel && <Text type="secondary" style={{ fontSize: 12 }}>当前：{currentStageLabel}</Text>}
              </Space>
              <Text type="secondary" style={{ fontSize: 12 }}>{friendlyHint}</Text>
            </Space>
            <Progress
              percent={summary?.progress ?? task.progress}
              size="small"
              status={task.status === 'FAILED' ? 'exception' : 'active'}
            />

            {/* 2. KPI 行：扫描文件 / 代码切片 / AI_ANALYZING AI / GENERATING_DOC AI */}
            <div className="ci-kpi-grid" style={{ marginTop: 12 }}>
              <Card size="small" className="ci-stat-card">
                <Statistic
                  title="扫描文件"
                  value={
                    summary
                      ? `${summary.counters.totalFiles || summary.current.totalFiles || 0}`
                      : 0
                  }
                  valueStyle={{ fontSize: 20 }}
                />
              </Card>
              <Card size="small" className="ci-stat-card">
                <Statistic
                  title="代码切片"
                  value={
                    summary
                      ? summary.counters.totalChunks || summary.current.totalChunks || 0
                      : 0
                  }
                  valueStyle={{ fontSize: 20 }}
                />
              </Card>
              <Card size="small" className="ci-stat-card">
                <Statistic
                  title="模块提炼 AI"
                  value={summary?.hierarchyAiCalls?.success ?? summary?.aiCalls.success ?? 0}
                  valueStyle={{ color: '#16a34a', fontSize: 20 }}
                  prefix={<CheckCircleOutlined />}
                  suffix={
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      / {(summary?.hierarchyAiCalls?.total ?? 0)} 调用
                      {(summary?.hierarchyAiCalls?.failed ?? 0) > 0 &&
                        <Text type="danger" style={{ fontSize: 12 }}> · {(summary?.hierarchyAiCalls?.failed ?? 0)} 失败</Text>
                      }
                    </Text>
                  }
                />
              </Card>
              <Card size="small" className="ci-stat-card">
                <Statistic
                  title="文档生成 AI"
                  value={summary?.docAiCalls?.success ?? 0}
                  valueStyle={{ color: '#16a34a', fontSize: 20 }}
                  prefix={<CheckCircleOutlined />}
                  suffix={
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      / {(summary?.docAiCalls?.total ?? 0)} 调用
                      {(summary?.docAiCalls?.failed ?? 0) > 0 &&
                        <Text type="danger" style={{ fontSize: 12 }}> · {(summary?.docAiCalls?.failed ?? 0)} 失败</Text>
                      }
                    </Text>
                  }
                />
              </Card>
            </div>

            {/* 3. 失败/完成友好提示（仅一行，不暴露堆栈） */}
            {task.status === 'FAILED' && (
              <Alert
                type="warning"
                showIcon
                style={{ marginTop: 12 }}
                message="任务失败，请查看完整日志"
                description={summary?.lastError ? `原因：${summary.lastError}` : undefined}
                action={
                  <Button size="small" icon={<FileSearchOutlined />} onClick={openExecLog}>
                    查看完整日志
                  </Button>
                }
              />
            )}
            {['PUSHED', 'CONFIRMED'].includes(task.status) && (
              <Alert type="success" showIcon style={{ marginTop: 12 }} message="任务已完成" />
            )}

            {/* 4. 阶段 Timeline */}
            {summary?.pipeline && summary.pipeline.length > 0 && (
              <Timeline
                style={{ marginTop: 12 }}
                items={summary.pipeline.map((s) => timelineItem(s))}
              />
            )}
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
        width={960}
        footer={<Button onClick={() => setLogModalOpen(false)}>关闭</Button>}
        destroyOnClose
      >
        {/* 顶部粘性信息条：Mock / 模型 / 总耗时 / 状态 / 日志 URI */}
        <div
          style={{
            position: 'sticky',
            top: 0,
            background: '#fff',
            zIndex: 1,
            padding: '8px 0',
            borderBottom: '1px solid #f0f0f0',
            marginBottom: 8,
          }}
        >
          <Space wrap>
            <Tag color={aiModeColor}>{aiModeLabel}</Tag>
            <Tag color="purple">模型：{summary?.modelName || task.modelName || '未指定'}</Tag>
            <Tag color="blue">
              总耗时 {(((summary?.durationMs ?? task.durationMs) || 0) / 1000).toFixed(1)} 秒
            </Tag>
            <Tag color={meta.color}>{meta.label}</Tag>
            <Text type="secondary" copyable={{ text: `local://storage/task_${task.id}/pipeline.log` }}>
              日志 URI：`local://storage/task_${task.id}/pipeline.log`
            </Text>
            <Button
              size="small"
              icon={<ReloadOutlined />}
              onClick={openExecLog}
              disabled={logLoading}
            >
              刷新
            </Button>
          </Space>
        </div>

        {logLoading ? (
          <Card loading style={{ minHeight: 200 }} />
        ) : (
          <pre
            className="ci-terminal"
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
            {execLogContent || cardLogContent || '(暂无日志)'}
          </pre>
        )}
      </Modal>
    </div>
  );
};

export default TaskDetail;
