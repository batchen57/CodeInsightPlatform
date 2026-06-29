import type React from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Descriptions,
  Empty,
  Popconfirm,
  Progress,
  Radio,
  Row,
  Space,
  Switch,
  Table,
  Tag,
  Timeline,
  Tooltip,
  message,
} from 'antd';
import {
  ArrowLeftOutlined,
  ClockCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  EyeOutlined,
  HistoryOutlined,
  PauseCircleOutlined,
  PlayCircleOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { Link, useNavigate, useParams } from 'react-router-dom';
import dayjs from 'dayjs';
import {
  deleteSchedule,
  disableSchedule,
  enableSchedule,
  getSchedule,
  listFireRecords,
  listScheduleTasks,
  triggerScheduleNow,
} from '../../../api/schedule';
import type { EntryScanConfig, ScheduleFireRecord, ScheduleTask, Task } from '../../../types';

const fireStatusColor: Record<string, string> = {
  CREATED: 'blue',
  RUNNING: 'processing',
  SUCCESS: 'green',
  FAILED: 'red',
  SKIPPED: 'default',
  QUEUED: 'orange',
};

const taskStatusColor: Record<string, string> = {
  DRAFT: 'default',
  PENDING: 'gold',
  PULLING_CODE: 'gold',
  PARSING_CODE: 'gold',
  SPLITTING_TASK: 'gold',
  AI_ANALYZING: 'processing',
  MODULE_HIERARCHY: 'processing',
  MODULE_HIERARCHY_REVIEW: 'cyan',
  GENERATING_DOC: 'processing',
  PENDING_REVIEW: 'blue',
  REVIEWING: 'blue',
  CONFIRMED: 'green',
  PUSHING: 'gold',
  PUSHED: 'green',
  FAILED: 'red',
  CANCELLED: 'default',
  ARCHIVED: 'default',
};

/**
 * 定时任务详情（/tasks/jobs/:id）
 *
 * 从 /schedules/:id 迁移：
 *  - 路径更新
 *  - 「编辑」跳到 /tasks/jobs/:id/edit
 *  - 不再内嵌 Modal
 */
const JobDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const scheduleId = Number(id);
  const navigate = useNavigate();

  const [schedule, setSchedule] = useState<ScheduleTask | null>(null);
  const [records, setRecords] = useState<ScheduleFireRecord[]>([]);
  const [recordTotal, setRecordTotal] = useState(0);
  const [recordCurrent, setRecordCurrent] = useState(1);
  const [recordSize, setRecordSize] = useState(10);
  const [recordsLoading, setRecordsLoading] = useState(false);
  const [loading, setLoading] = useState(true);

  const [triggeredTasks, setTriggeredTasks] = useState<Task[]>([]);
  const [triggeredTotal, setTriggeredTotal] = useState(0);
  const [triggeredCurrent, setTriggeredCurrent] = useState(1);
  const [triggeredSize, setTriggeredSize] = useState(10);
  const [triggeredLoading, setTriggeredLoading] = useState(false);
  const [triggeredType, setTriggeredType] = useState<string | undefined>();

  const load = useCallback(async () => {
    if (!scheduleId) return;
    setLoading(true);
    try {
      const s = await getSchedule(scheduleId);
      setSchedule(s);
    } catch {
      // ignore
    } finally {
      setLoading(false);
    }
  }, [scheduleId]);

  const loadRecords = useCallback(async () => {
    if (!scheduleId) return;
    setRecordsLoading(true);
    try {
      const res = await listFireRecords(scheduleId, {
        current: recordCurrent,
        size: recordSize,
      });
      setRecords(res.records);
      setRecordTotal(res.total);
    } catch {
      // ignore
    } finally {
      setRecordsLoading(false);
    }
  }, [scheduleId, recordCurrent, recordSize]);

  const loadTriggeredTasks = useCallback(async () => {
    if (!scheduleId) return;
    setTriggeredLoading(true);
    try {
      const res = await listScheduleTasks(scheduleId, {
        current: triggeredCurrent,
        size: triggeredSize,
        type: triggeredType,
      });
      setTriggeredTasks(res.records);
      setTriggeredTotal(res.total);
    } catch {
      // ignore
    } finally {
      setTriggeredLoading(false);
    }
  }, [scheduleId, triggeredCurrent, triggeredSize, triggeredType]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    loadRecords();
  }, [loadRecords]);

  useEffect(() => {
    loadTriggeredTasks();
  }, [loadTriggeredTasks]);

  // 触发中任务轮询
  useEffect(() => {
    if (triggeredTasks.length === 0) return;
    const hasRunning = triggeredTasks.some((t) =>
      [
        'PENDING',
        'PULLING_CODE',
        'PARSING_CODE',
        'SPLITTING_TASK',
        'AI_ANALYZING',
        'MODULE_HIERARCHY',
        'MODULE_HIERARCHY_REVIEW',
        'GENERATING_DOC',
        'PUSHING',
      ].includes(t.status),
    );
    if (!hasRunning) return;
    const timer = window.setInterval(() => {
      loadTriggeredTasks();
    }, 2500);
    return () => window.clearInterval(timer);
  }, [triggeredTasks, loadTriggeredTasks]);

  const handleToggleEnabled = async (next: boolean) => {
    try {
      if (next) await enableSchedule(scheduleId);
      else await disableSchedule(scheduleId);
      message.success(next ? '已启用' : '已禁用');
      load();
    } catch {
      // ignore
    }
  };

  const handleTrigger = async () => {
    try {
      const res = await triggerScheduleNow(scheduleId);
      message.success(res.taskId ? `已触发，创建任务 #${res.taskId}` : '已触发');
      load();
      loadRecords();
      loadTriggeredTasks();
      if (res.taskId) {
        setTimeout(() => {
          if (window.confirm('已创建反编译任务，是否前往任务详情？')) {
            navigate(`/tasks/${res.taskId}`);
          }
        }, 100);
      }
    } catch {
      // ignore
    }
  };

  const handleDelete = async () => {
    try {
      await deleteSchedule(scheduleId);
      message.success('已删除');
      navigate('/tasks/jobs');
    } catch {
      // ignore
    }
  };

  const scanCfg = (schedule?.entryScanConfig ?? {}) as EntryScanConfig;
  const scanCfgSummary = useMemo(() => formatScanConfig(scanCfg), [scanCfg]);

  if (loading && !schedule) {
    return <Empty description="加载中" />;
  }
  if (!schedule) {
    return <Empty description="未找到该定时任务" />;
  }

  return (
    <div className="ci-page">
      <Card bordered={false} className="ci-summary-card">
        <Row gutter={16} align="middle">
          <Col flex="auto">
            <Space direction="vertical" size={6}>
              <Space size={12} align="center">
                <Button
                  type="text"
                  icon={<ArrowLeftOutlined />}
                  onClick={() => navigate('/tasks/jobs')}
                >
                  返回 JOB 配置
                </Button>
                <h2 style={{ margin: 0 }}>{schedule.name}</h2>
                <Tag color={schedule.enabled === 1 ? 'green' : 'default'}>
                  {schedule.enabled === 1 ? '已启用' : '已禁用'}
                </Tag>
                <Tag color={schedule.fireStrategy === 'INITIAL' ? 'purple' : 'blue'}>
                  {schedule.fireStrategy === 'INITIAL' ? '全量' : '增量'}
                </Tag>
                <Tag
                  color={
                    schedule.overlapStrategy === 'SKIP'
                      ? 'default'
                      : schedule.overlapStrategy === 'QUEUE'
                        ? 'orange'
                        : 'green'
                  }
                >
                  {schedule.overlapStrategy === 'SKIP'
                    ? '冲突：跳过'
                    : schedule.overlapStrategy === 'QUEUE'
                      ? '冲突：排队'
                      : '冲突：并行'}
                </Tag>
              </Space>
              <div style={{ color: '#888' }}>
                ID #{schedule.id} · 创建于{' '}
                {dayjs(schedule.createdAt).format('YYYY-MM-DD HH:mm:ss')}
                {schedule.description ? ` · ${schedule.description}` : ''}
              </div>
            </Space>
          </Col>
          <Col>
            <Space>
              <Tooltip title="立即触发一次（不依赖 cron）">
                <Button
                  type="primary"
                  icon={<ThunderboltOutlined />}
                  onClick={handleTrigger}
                  disabled={schedule.enabled === 0}
                >
                  立即触发
                </Button>
              </Tooltip>
              <Tooltip title={schedule.enabled === 1 ? '禁用' : '启用'}>
                <Switch
                  checked={schedule.enabled === 1}
                  onChange={handleToggleEnabled}
                  checkedChildren={<PlayCircleOutlined />}
                  unCheckedChildren={<PauseCircleOutlined />}
                />
              </Tooltip>
              <Button
                icon={<EditOutlined />}
                onClick={() => navigate(`/tasks/jobs/${schedule.id}/edit`)}
              >
                编辑
              </Button>
              <Popconfirm
                title="确认删除？"
                okText="删除"
                cancelText="取消"
                okButtonProps={{ danger: true }}
                onConfirm={handleDelete}
              >
                <Button danger icon={<DeleteOutlined />}>
                  删除
                </Button>
              </Popconfirm>
              <Button icon={<ReloadOutlined />} onClick={() => { load(); loadRecords(); }}>
                刷新
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={14}>
          <Card title="调度配置" bordered={false}>
            <Descriptions column={2} size="small">
              <Descriptions.Item label="cron">
                <code>{schedule.cronExpression}</code>
              </Descriptions.Item>
              <Descriptions.Item label="时区">{schedule.timezone}</Descriptions.Item>
              <Descriptions.Item label="下次触发">
                {schedule.nextFireAt ? (
                  <Space size={4}>
                    <ClockCircleOutlined />
                    {dayjs(schedule.nextFireAt).format('YYYY-MM-DD HH:mm:ss')}
                  </Space>
                ) : (
                  '-'
                )}
              </Descriptions.Item>
              <Descriptions.Item label="上次触发">
                {schedule.lastFiredAt
                  ? dayjs(schedule.lastFiredAt).format('YYYY-MM-DD HH:mm:ss')
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="上次状态">
                {schedule.lastStatus ? (
                  <Tag color={fireStatusColor[schedule.lastStatus] ?? 'blue'}>
                    {schedule.lastStatus}
                  </Tag>
                ) : (
                  '-'
                )}
              </Descriptions.Item>
              <Descriptions.Item label="最近任务">
                {schedule.lastTaskId ? (
                  <Link to={`/tasks/${schedule.lastTaskId}`}>#{schedule.lastTaskId}</Link>
                ) : (
                  '-'
                )}
              </Descriptions.Item>
              <Descriptions.Item label="模块层级调试">
                {schedule.requireHierarchyReview === 1 ? '启用' : '跳过'}
              </Descriptions.Item>
              <Descriptions.Item label="AI 模型">
                {schedule.modelName || '（系统默认）'}
              </Descriptions.Item>
            </Descriptions>

            <div style={{ marginTop: 12 }}>
              <div style={{ marginBottom: 8, color: '#888' }}>入口扫描配置</div>
              {scanCfgSummary ? (
                <pre style={{ background: '#f6f8fa', padding: 12, borderRadius: 6, margin: 0 }}>
                  {scanCfgSummary}
                </pre>
              ) : (
                <span style={{ color: '#999' }}>未配置，使用仓库默认 / Controller/JOB/MQ 兜底</span>
              )}
            </div>
          </Card>
        </Col>

        <Col span={10}>
          <Card title="运行统计" bordered={false}>
            <Row gutter={16}>
              <Col span={6}>
                <Statistic label="总触发" value={schedule.totalFired ?? 0} />
              </Col>
              <Col span={6}>
                <Statistic label="成功" value={schedule.totalSuccess ?? 0} color="green" />
              </Col>
              <Col span={6}>
                <Statistic label="失败" value={schedule.totalFailed ?? 0} color="red" />
              </Col>
              <Col span={6}>
                <Statistic label="跳过" value={schedule.totalSkipped ?? 0} color="orange" />
              </Col>
            </Row>
          </Card>

          <Card title="最近触发" bordered={false} style={{ marginTop: 16 }}>
            {records.length === 0 ? (
              <Empty description="暂无触发记录" />
            ) : (
              <Timeline
                items={records.slice(0, 6).map((r) => ({
                  color:
                    r.status === 'SUCCESS'
                      ? 'green'
                      : r.status === 'FAILED'
                        ? 'red'
                        : r.status === 'SKIPPED'
                          ? 'gray'
                          : r.status === 'QUEUED'
                            ? 'orange'
                            : 'blue',
                  children: (
                    <div>
                      <div>
                        <Tag color={fireStatusColor[r.status] ?? 'blue'}>{r.status}</Tag>
                        <span style={{ color: '#888' }}>
                          {dayjs(r.fireTime).format('YYYY-MM-DD HH:mm:ss')}
                        </span>
                      </div>
                      {r.taskId && (
                        <div>
                          任务 <Link to={`/tasks/${r.taskId}`}>#{r.taskId}</Link>
                        </div>
                      )}
                      {r.skipReason && (
                        <div style={{ color: '#999', fontSize: 12 }}>{r.skipReason}</div>
                      )}
                      {r.errorMessage && (
                        <div style={{ color: 'red', fontSize: 12 }}>{r.errorMessage}</div>
                      )}
                    </div>
                  ),
                }))}
              />
            )}
          </Card>
        </Col>
      </Row>

      <Card
        title={
          <Space>
            <HistoryOutlined />
            触发历史
          </Space>
        }
        bordered={false}
        style={{ marginTop: 16 }}
      >
        <Table
          rowKey="id"
          loading={recordsLoading}
          dataSource={records}
          pagination={{
            current: recordCurrent,
            pageSize: recordSize,
            total: recordTotal,
            showSizeChanger: true,
            onChange: (p, ps) => {
              setRecordCurrent(p);
              setRecordSize(ps);
            },
          }}
          columns={[
            {
              title: '触发时间',
              dataIndex: 'fireTime',
              width: 170,
              render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm:ss'),
            },
            {
              title: '计划时间',
              dataIndex: 'plannedTime',
              width: 170,
              render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm:ss'),
            },
            {
              title: '状态',
              dataIndex: 'status',
              width: 110,
              render: (v: string) => <Tag color={fireStatusColor[v] ?? 'blue'}>{v}</Tag>,
            },
            {
              title: '关联任务',
              dataIndex: 'taskId',
              width: 120,
              render: (id?: number) =>
                id ? <Link to={`/tasks/${id}`}>#{id}</Link> : <span style={{ color: '#999' }}>-</span>,
            },
            {
              title: '跳过原因 / 错误信息',
              dataIndex: 'skipReason',
              render: (_: unknown, r: ScheduleFireRecord) =>
                r.skipReason ? (
                  <span style={{ color: '#999' }}>{r.skipReason}</span>
                ) : r.errorMessage ? (
                  <span style={{ color: 'red' }}>{r.errorMessage}</span>
                ) : (
                  '-'
                ),
            },
          ]}
        />
      </Card>

      <Card
        title={
          <Space>
            <PlayCircleOutlined />
            已触发的反编译任务
            <Tag color="blue">SCHEDULED</Tag>
          </Space>
        }
        bordered={false}
        style={{ marginTop: 16 }}
        extra={
          <Space>
            <Radio.Group
              size="small"
              value={triggeredType ?? 'ALL'}
              onChange={(e) => {
                setTriggeredType(e.target.value === 'ALL' ? undefined : e.target.value);
                setTriggeredCurrent(1);
              }}
            >
              <Radio.Button value="ALL">全部</Radio.Button>
              <Radio.Button value="INITIAL">全量</Radio.Button>
              <Radio.Button value="INCREMENTAL">增量</Radio.Button>
            </Radio.Group>
            <Button size="small" icon={<ReloadOutlined />} onClick={loadTriggeredTasks}>
              刷新
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          loading={triggeredLoading}
          dataSource={triggeredTasks}
          pagination={{
            current: triggeredCurrent,
            pageSize: triggeredSize,
            total: triggeredTotal,
            showSizeChanger: true,
            onChange: (p, ps) => {
              setTriggeredCurrent(p);
              setTriggeredSize(ps);
            },
          }}
          columns={[
            {
              title: '任务',
              dataIndex: 'id',
              width: 90,
              render: (id: number) => <Link to={`/tasks/${id}`}>#{id}</Link>,
            },
            {
              title: '类型',
              dataIndex: 'type',
              width: 100,
              render: (v: string) => (
                <Tag color={v === 'INITIAL' ? 'purple' : 'blue'}>
                  {v === 'INITIAL' ? '全量' : '增量'}
                </Tag>
              ),
            },
            {
              title: '状态',
              dataIndex: 'status',
              width: 180,
              render: (v: string) => <Tag color={taskStatusColor[v] ?? 'default'}>{v}</Tag>,
            },
            {
              title: '进度',
              dataIndex: 'progress',
              width: 180,
              render: (p: number, r: Task) => (
                <Progress
                  percent={p ?? 0}
                  size="small"
                  status={r.status === 'FAILED' ? 'exception' : (p ?? 0) === 100 ? 'success' : 'active'}
                />
              ),
            },
            {
              title: 'AI 模型',
              dataIndex: 'modelName',
              width: 130,
              render: (v?: string) => v || '-',
            },
            {
              title: '耗时',
              dataIndex: 'durationMs',
              width: 100,
              render: (ms: number) => (ms ? `${(ms / 1000).toFixed(1)}s` : '-'),
            },
            {
              title: '创建时间',
              dataIndex: 'createdAt',
              width: 170,
              render: (v: string) => (v ? dayjs(v).format('YYYY-MM-DD HH:mm:ss') : '-'),
            },
            {
              title: '操作',
              width: 100,
              render: (_: unknown, r: Task) => (
                <Button
                  size="small"
                  type="link"
                  icon={<EyeOutlined />}
                  onClick={() => navigate(`/tasks/${r.id}`)}
                >
                  详情
                </Button>
              ),
            },
          ]}
          locale={{ emptyText: <Empty description="该定时任务尚未触发过反编译任务" /> }}
        />
      </Card>
    </div>
  );
};

const Statistic: React.FC<{ label: string; value: number; color?: string }> = ({
  label,
  value,
  color,
}) => (
  <div style={{ textAlign: 'center' }}>
    <div style={{ color: '#888', fontSize: 12 }}>{label}</div>
    <div style={{ fontSize: 28, fontWeight: 600, color: color ?? '#1677ff' }}>{value}</div>
  </div>
);

function formatScanConfig(cfg: EntryScanConfig): string {
  const lines: string[] = [];
  const includeAny = cfg.includeAnnotations || cfg.includeClasspaths || cfg.includeExtends;
  const excludeAny = cfg.excludeClasspaths || cfg.excludePackages || cfg.excludeAnnotations;
  if (includeAny) {
    lines.push('【入口识别】');
    if (cfg.includeAnnotations?.length) lines.push(`  注解：${cfg.includeAnnotations.join(', ')}`);
    if (cfg.includeClasspaths?.length) lines.push(`  类路径：${cfg.includeClasspaths.join(', ')}`);
    if (cfg.includeExtends?.length) lines.push(`  继承/实现：${cfg.includeExtends.join(', ')}`);
  }
  if (excludeAny) {
    lines.push('【排除规则】');
    if (cfg.excludeClasspaths?.length) lines.push(`  类路径：${cfg.excludeClasspaths.join(', ')}`);
    if (cfg.excludePackages?.length) lines.push(`  包路径：${cfg.excludePackages.join(', ')}`);
    if (cfg.excludeAnnotations?.length) lines.push(`  注解：${cfg.excludeAnnotations.join(', ')}`);
  }
  return lines.join('\n');
}

export default JobDetail;
