import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Drawer,
  Empty,
  Input,
  Popconfirm,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
} from '@ant-design/icons';
import {
  getEntrypointReview,
  rejectEntrypointReview,
  resumeEntrypointReview,
} from '../api/task';
import type { EntrypointReviewItem } from '../types';

const { Text } = Typography;

export interface EntrypointReviewDrawerProps {
  open: boolean;
  taskId: number | null;
  onClose: () => void;
  /** 驳回/确认后通知父组件刷新列表 */
  onSubmitted?: () => void;
}

const ENTRY_TYPE_LABEL: Record<string, { color: string; label: string }> = {
  CONTROLLER:    { color: 'cyan',    label: '控制器' },
  SCHEDULED_JOB: { color: 'purple',  label: '定时任务' },
  MQ_LISTENER:   { color: 'gold',    label: '消息监听' },
  COMPONENT:     { color: 'blue',    label: '组件' },
  APPLICATION:   { color: 'magenta', label: '应用入口' },
  MAIN:          { color: 'magenta', label: 'Main 入口' },
  CUSTOM:        { color: 'default', label: '自定义' },
};

/**
 * 知识入口复核抽屉（只读）。
 * <p>展示任务识别到的入口类与每个类下的关键方法；底部提供两个动作：</p>
 * <ul>
 *   <li>「驳回任务」：终止任务（CANCELLED）</li>
 *   <li>「确认并继续」：推进流水线到 AI_ANALYZING → 模块层级</li>
 * </ul>
 * <p>UI 严格只读：用户不能编辑入口列表。如需修改，请到任务配置调整 entry_scan_config 后重跑。</p>
 */
const EntrypointReviewDrawer: React.FC<EntrypointReviewDrawerProps> = ({
  open,
  taskId,
  onClose,
  onSubmitted,
}) => {
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState<'resume' | 'reject' | null>(null);
  const [items, setItems] = useState<EntrypointReviewItem[]>([]);
  const [rejectReason, setRejectReason] = useState('');

  // 加载入口清单
  useEffect(() => {
    if (!open || taskId == null) {
      setItems([]);
      setRejectReason('');
      return;
    }
    let cancelled = false;
    setLoading(true);
    getEntrypointReview(taskId)
      .then((data) => {
        if (cancelled) return;
        setItems(Array.isArray(data) ? data : []);
      })
      .catch(() => {
        // request.ts 拦截器已统一弹错
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, taskId]);

  const stats = useMemo(() => {
    const totalClasses = items.length;
    const totalMethods = items.reduce(
      (sum, it) => sum + (it.methods?.length ?? 0),
      0,
    );
    const typeCounts: Record<string, number> = {};
    items.forEach((it) => {
      const t = it.entryType || 'UNKNOWN';
      typeCounts[t] = (typeCounts[t] || 0) + 1;
    });
    return { totalClasses, totalMethods, typeCounts };
  }, [items]);

  const handleResume = async () => {
    if (taskId == null) return;
    setSubmitting('resume');
    try {
      await resumeEntrypointReview(taskId);
      message.success('已确认，任务继续执行 AI 阶段');
      onSubmitted?.();
      onClose();
    } finally {
      setSubmitting(null);
    }
  };

  const handleReject = async () => {
    if (taskId == null) return;
    if (!rejectReason.trim()) {
      message.warning('请填写驳回理由');
      return;
    }
    setSubmitting('reject');
    try {
      await rejectEntrypointReview(taskId, rejectReason.trim());
      message.success('已驳回，任务已终止');
      onSubmitted?.();
      onClose();
    } finally {
      setSubmitting(null);
    }
  };

  // 各类型入口的列容器；每个入口类一张小卡，下方表格列方法
  return (
    <Drawer
      title={taskId ? `知识入口复核 #${taskId}` : '知识入口复核'}
      open={open}
      width={1060}
      onClose={onClose}
      destroyOnHidden
      footer={
        <Space style={{ float: 'right' }}>
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
      }
    >
      {taskId == null ? null : loading ? (
        <div style={{ padding: 48, textAlign: 'center' }}>
          <Spin indicator={<LoadingOutlined />} /> 正在加载入口清单...
        </div>
      ) : (
        <div>
          <Alert
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
            message="知识入口复核说明"
            description={
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                <li>
                  本页面是<strong>只读视图</strong>：展示任务配置的扫描与排除规则下识别到的入口类与方法，
                  用户不能直接在此处增删或编辑。
                </li>
                <li>
                  确认后任务继续进入 AI 提炼模块层级；驳回则任务终止（CANCELLED），不会留下任何知识资产。
                </li>
                <li>
                  若清单与预期不一致（例如少了某个 Controller、或混入测试类），请回到「创建任务」或「代码库配置」
                  调整 <Text code>entry_scan_config</Text>（include/exclude 规则）后重新创建任务。
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
                                  {r.httpMethod && (
                                    <Tag color="geekblue">{r.httpMethod}</Tag>
                                  )}
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
        </div>
      )}
    </Drawer>
  );
};

export default EntrypointReviewDrawer;