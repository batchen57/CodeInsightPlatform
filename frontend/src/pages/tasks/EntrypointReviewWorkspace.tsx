import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Empty,
  Input,
  Popconfirm,
  Segmented,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Tree,
  Typography,
  message,
} from 'antd';
import type { DataNode } from 'antd/es/tree';
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import {
  getEntrypointReview,
  getTask,
  rejectEntrypointReview,
  resumeEntrypointReview,
} from '../../api/task';
import type { EntrypointReviewItem, Task } from '../../types';

const { Text } = Typography;

const ENTRY_TYPE_LABEL: Record<string, { color: string; label: string }> = {
  CONTROLLER: { color: 'cyan', label: '控制器' },
  SCHEDULED_JOB: { color: 'purple', label: '定时任务' },
  MQ_LISTENER: { color: 'gold', label: '消息监听' },
  COMPONENT: { color: 'blue', label: '组件' },
  APPLICATION: { color: 'magenta', label: '应用入口' },
  MAIN: { color: 'magenta', label: 'Main 入口' },
  CUSTOM: { color: 'default', label: '自定义' },
};

/** 从全限定类名中提取简短类名（如 com.demo.UserController → UserController） */
function shortClassName(fq: string): string {
  const idx = fq.lastIndexOf('.');
  return idx >= 0 ? fq.slice(idx + 1) : fq;
}

export interface EntrypointReviewWorkspaceProps {
  taskId: number;
  /** 确认/驳回成功后的回调（如刷新父级列表） */
  onSubmitted?: () => void;
}

/**
 * 入口复核详情：只读展示入口类与方法，支持确认继续或驳回任务。
 */
const EntrypointReviewWorkspace: React.FC<EntrypointReviewWorkspaceProps> = ({
  taskId,
  onSubmitted,
}) => {
  const navigate = useNavigate();
  const [task, setTask] = useState<Task | null>(null);
  const [taskLoading, setTaskLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState<'resume' | 'reject' | null>(null);
  const [items, setItems] = useState<EntrypointReviewItem[]>([]);
  const [rejectReason, setRejectReason] = useState('');
  const [viewMode, setViewMode] = useState<'list' | 'tree'>('list');

  useEffect(() => {
    setTaskLoading(true);
    getTask(taskId)
      .then(setTask)
      .catch(() => setTask(null))
      .finally(() => setTaskLoading(false));
  }, [taskId]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    getEntrypointReview(taskId)
      .then((data) => {
        if (!cancelled) setItems(Array.isArray(data) ? data : []);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [taskId]);

  const stats = useMemo(() => {
    const totalClasses = items.length;
    const totalMethods = items.reduce((sum, it) => sum + (it.methods?.length ?? 0), 0);
    const typeCounts: Record<string, number> = {};
    items.forEach((it) => {
      const t = it.entryType || 'UNKNOWN';
      typeCounts[t] = (typeCounts[t] || 0) + 1;
    });
    return { totalClasses, totalMethods, typeCounts };
  }, [items]);

  const handleResume = async () => {
    setSubmitting('resume');
    try {
      await resumeEntrypointReview(taskId);
      message.success('已确认，任务继续执行 AI 阶段');
      onSubmitted?.();
      navigate('/tasks/entrypoint-review');
    } finally {
      setSubmitting(null);
    }
  };

  const handleReject = async () => {
    if (!rejectReason.trim()) {
      message.warning('请填写驳回理由');
      return;
    }
    setSubmitting('reject');
    try {
      await rejectEntrypointReview(taskId, rejectReason.trim());
      message.success('已驳回，任务已终止');
      onSubmitted?.();
      navigate('/tasks/entrypoint-review');
    } finally {
      setSubmitting(null);
    }
  };

  /** 树形视图数据：按入口类型分组 → 类 → 方法 */
  const treeData = useMemo<DataNode[]>(() => {
    const groups: Record<string, EntrypointReviewItem[]> = {};
    items.forEach((it) => {
      const t = it.entryType || 'UNKNOWN';
      if (!groups[t]) groups[t] = [];
      groups[t].push(it);
    });
    // 按组内数量降序排列
    const sortedGroups = Object.entries(groups).sort(([, a], [, b]) => b.length - a.length);
    return sortedGroups.map(([typeKey, classList]) => {
      const typeMeta = ENTRY_TYPE_LABEL[typeKey] || { color: 'default', label: typeKey };
      return {
        key: `type-${typeKey}`,
        title: (
          <Space size={4}>
            <Tag color={typeMeta.color} style={{ marginRight: 0 }}>{typeMeta.label}</Tag>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {classList.length} 类 · {classList.reduce((s, c) => s + (c.methods?.length ?? 0), 0)} 方法
            </Text>
          </Space>
        ),
        selectable: false,
        children: classList.map((cls) => ({
          key: `class-${cls.id}`,
          title: (
            <Space size={4}>
              <Text strong>{shortClassName(cls.className)}</Text>
              {cls.annotation && (
                <Tag style={{ fontSize: 11 }}>
                  {cls.annotation.length > 24 ? cls.annotation.slice(0, 22) + '…' : cls.annotation}
                </Tag>
              )}
              {cls.remark && (
                <Text type="secondary" style={{ fontSize: 11 }}>
                  {cls.remark.length > 40 ? cls.remark.slice(0, 38) + '…' : cls.remark}
                </Text>
              )}
            </Space>
          ),
          children: (cls.methods || []).map((m, mi) => ({
            key: `method-${cls.id}-${mi}`,
            isLeaf: true,
            title: (
              <Space size={4} style={{ fontSize: 12 }}>
                {m.httpMethod && <Tag color="geekblue" style={{ fontSize: 11, lineHeight: '16px' }}>{m.httpMethod}</Tag>}
                {m.httpPath && <Text code style={{ fontSize: 11 }}>{m.httpPath}</Text>}
                {m.annotation && !m.httpMethod && (
                  <Tag style={{ fontSize: 11, lineHeight: '16px' }}>{m.annotation}</Tag>
                )}
                <Text style={{ fontSize: 12 }}>{m.methodSignature || m.methodName}</Text>
              </Space>
            ),
          })),
        })),
      };
    });
  }, [items]);

  const canReview = task?.status === 'ENTRYPOINT_REVIEW';

  return (
    <div className="ci-page ci-entrypoint-review-detail-page">
      <Card
        title={
          <Space wrap>
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              onClick={() => navigate('/tasks/entrypoint-review')}
            >
              返回任务列表
            </Button>
            <Text strong>入口复核 · 任务 #{taskId}</Text>
            {task && (
              <>
                <Tag color={task.type === 'INITIAL' ? 'geekblue' : 'green'}>
                  {task.type === 'INITIAL' ? '全量' : '增量'}
                </Tag>
                <Tag color="cyan">{task.status}</Tag>
              </>
            )}
            {taskLoading && <Text type="secondary">加载中…</Text>}
          </Space>
        }
        extra={
          <Space size={12}>
            <Segmented
              size="large"
              options={[
                { value: 'list', label: '列表视图' },
                { value: 'tree', label: '树形视图' },
              ]}
              value={viewMode}
              onChange={(v) => setViewMode(v as 'list' | 'tree')}
            />
            {canReview ? (
              <Space>
                <Popconfirm
                title="确认驳回任务？"
                description={
                  <div style={{ width: 280 }}>
                    <div style={{ marginBottom: 8 }}>
                      任务将被终止，不会进入 AI 阶段。请填写驳回理由：
                    </div>
                    <Input.TextArea
                      rows={3}
                      placeholder="例如：扫描配置有误，请调整 entry_scan_config 后重跑"
                      value={rejectReason}
                      onChange={(e) => setRejectReason(e.target.value)}
                    />
                  </div>
                }
                okText="确认驳回"
                cancelText="再检查下"
                okButtonProps={{ danger: true, loading: submitting === 'reject' }}
                onConfirm={handleReject}
              >
                <Button danger icon={<CloseCircleOutlined />} loading={submitting === 'reject'}>
                  驳回任务
                </Button>
              </Popconfirm>
              <Button
                type="primary"
                icon={<CheckCircleOutlined />}
                loading={submitting === 'resume'}
                onClick={handleResume}
              >
                确认并继续
              </Button>
            </Space>
          ) : (
            <Text type="secondary">当前任务不在入口复核状态，仅可浏览历史清单</Text>
          )}
          </Space>
        }
      >
        {loading ? (
          <div style={{ padding: 48, textAlign: 'center' }}>
            <Spin indicator={<LoadingOutlined />} /> 正在加载入口清单...
          </div>
        ) : (
          <>
            <Alert
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
              message="入口复核说明"
              description={
                <ul style={{ margin: 0, paddingLeft: 18 }}>
                  <li>
                    本页为<strong>只读视图</strong>：展示扫描规则下识别到的入口类与方法，不能直接增删改。
                  </li>
                  <li>确认后任务进入 AI 分析；驳回则任务终止（CANCELLED）。</li>
                  <li>
                    若清单与预期不一致，请调整 <Text code>entry_scan_config</Text> 后重新创建任务。
                  </li>
                </ul>
              }
            />

            <Space size="large" style={{ marginBottom: 16 }}>
              <Statistic title="入口类数" value={stats.totalClasses} suffix="个" />
              <Statistic title="方法总数" value={stats.totalMethods} suffix="个" />
              <Space size={4} wrap>
                {Object.entries(stats.typeCounts).map(([t, n]) => {
                  const meta = ENTRY_TYPE_LABEL[t] || { color: 'default', label: t };
                  return (
                    <Tag color={meta.color} key={t}>
                      {meta.label} {n}
                    </Tag>
                  );
                })}
              </Space>
            </Space>

            {items.length === 0 ? (
              <Empty description="未识别到任何入口。请调整 entry_scan_config 后重新创建任务。" />
            ) : viewMode === 'tree' ? (
              <div style={{ border: '1px solid #f0f0f0', borderRadius: 6, padding: 12, background: '#fafafa' }}>
                <Tree
                  treeData={treeData}
                  defaultExpandAll
                  showLine={{ showLeafIcon: false }}
                  blockNode
                  style={{ fontSize: 13 }}
                />
              </div>
            ) : (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                {items.map((it) => {
                  const typeMeta = ENTRY_TYPE_LABEL[it.entryType || ''] || {
                    color: 'default',
                    label: it.entryType || 'UNKNOWN',
                  };
                  return (
                    <div
                      key={it.id}
                      style={{
                        border: '1px solid #f0f0f0',
                        borderRadius: 6,
                        padding: 12,
                        background: '#fafafa',
                      }}
                    >
                      <Space size={8} wrap style={{ marginBottom: 4 }}>
                        <Tag color={typeMeta.color}>{typeMeta.label}</Tag>
                        <Text strong>{it.className}</Text>
                        {it.annotation && (
                          <Tag>
                            <Text type="secondary" style={{ fontSize: 12 }}>
                              触发注解：{it.annotation}
                            </Text>
                          </Tag>
                        )}
                        {it.remark && (
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            路径：{it.remark}
                          </Text>
                        )}
                      </Space>
                      {it.filePath && (
                        <div style={{ marginBottom: 8 }}>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {it.filePath}
                          </Text>
                        </div>
                      )}
                      {(it.methods?.length ?? 0) === 0 ? (
                        <Text type="secondary">（该入口未识别到方法）</Text>
                      ) : (
                        <Table<NonNullable<EntrypointReviewItem['methods'][number]>>
                          size="small"
                          rowKey={(r, idx) => `${it.id}-${idx}-${r.methodName}`}
                          dataSource={it.methods}
                          pagination={false}
                          columns={[
                            {
                              title: '方法签名',
                              dataIndex: 'methodSignature',
                              key: 'methodSignature',
                              width: 280,
                              render: (v?: string) => <Text code>{v}</Text>,
                            },
                            {
                              title: '方法名',
                              dataIndex: 'methodName',
                              key: 'methodName',
                              width: 160,
                            },
                            {
                              title: '注解',
                              dataIndex: 'annotation',
                              key: 'annotation',
                              width: 200,
                              render: (v?: string) => (v ? <Tag>{v}</Tag> : '-'),
                            },
                            {
                              title: 'HTTP 路径 / 方法',
                              key: 'http',
                              render: (_: unknown, r) =>
                                r.httpPath ? (
                                  <Space size={4}>
                                    {r.httpMethod && <Tag color="geekblue">{r.httpMethod}</Tag>}
                                    <Text code style={{ fontSize: 12 }}>
                                      {r.httpPath}
                                    </Text>
                                  </Space>
                                ) : (
                                  <Text type="secondary">-</Text>
                                ),
                            },
                          ]}
                        />
                      )}
                    </div>
                  );
                })}
              </Space>
            )}
          </>
        )}
      </Card>
    </div>
  );
};

export default EntrypointReviewWorkspace;
