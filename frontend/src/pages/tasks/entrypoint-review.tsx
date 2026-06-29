import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Empty,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  EditOutlined,
  EyeOutlined,
  LoadingOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { listTasks } from '../../api/task';
import { listSystems } from '../../api/system';
import type { System, Task } from '../../types';
import EntrypointReviewDrawer from '../../components/EntrypointReviewDrawer';

const { Text } = Typography;

const statusMeta: Record<string, { color: string; label: string }> = {
  ENTRYPOINT_REVIEW: { color: 'cyan', label: '知识入口复核' },
  AI_ANALYZING:      { color: 'orange', label: 'AI 分析中' },
  MODULE_HIERARCHY:  { color: 'gold', label: '模块层级提炼' },
  MODULE_HIERARCHY_REVIEW: { color: 'geekblue', label: '模块层级复核' },
  GENERATING_DOC:    { color: 'gold', label: '生成文档' },
  PENDING_REVIEW:    { color: 'magenta', label: '已生成文档' },
  FAILED:            { color: 'red', label: '调试中失败' },
  CANCELLED:         { color: 'default', label: '已驳回' },
};

/**
 * 知识入口调试专用页：列出所有处于或曾经处于 ENTRYPOINT_REVIEW 的任务，
 * 用户可点击行进入 EntrypointReviewDrawer 确认入口清单或驳回任务。
 */
const EntrypointReview: React.FC = () => {
  const navigate = useNavigate();
  const [tasks, setTasks] = useState<Task[]>([]);
  const [systems, setSystems] = useState<System[]>([]);
  const [loading, setLoading] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [currentTaskId, setCurrentTaskId] = useState<number | null>(null);

  const fetchReviewTasks = useCallback(async () => {
    setLoading(true);
    try {
      const inProgress = await listTasks({ current: 1, size: 200, status: 'ENTRYPOINT_REVIEW' });
      let records: Task[] = inProgress.records;
      if (showHistory) {
        const [generating, finished, cancelled] = await Promise.all([
          listTasks({ current: 1, size: 50, status: 'AI_ANALYZING' }),
          listTasks({ current: 1, size: 50, status: 'PENDING_REVIEW' }),
          listTasks({ current: 1, size: 50, status: 'CANCELLED' }),
        ]);
        // 去重 + 按 id 降序
        const map = new Map<number, Task>();
        [...records, ...generating.records, ...finished.records, ...cancelled.records]
          .forEach((t) => map.set(t.id, t));
        records = Array.from(map.values()).sort((a, b) => b.id - a.id);
      }
      setTasks(records);
    } finally {
      setLoading(false);
    }
  }, [showHistory]);

  useEffect(() => {
    fetchReviewTasks();
  }, [fetchReviewTasks]);

  useEffect(() => {
    listSystems({ current: 1, size: 200, status: 1 }).then((data) => setSystems(data.records));
  }, []);

  const openDrawer = (taskId: number) => {
    setCurrentTaskId(taskId);
    setDrawerOpen(true);
  };
  const closeDrawer = () => {
    setDrawerOpen(false);
    setCurrentTaskId(null);
  };

  const reviewCount = useMemo(
    () => tasks.filter((t) => t.status === 'ENTRYPOINT_REVIEW').length,
    [tasks],
  );

  const columns = [
    {
      title: '任务',
      dataIndex: 'id',
      key: 'id',
      width: 90,
      render: (id: number) => <Text code>#{id}</Text>,
    },
    {
      title: '系统',
      dataIndex: 'systemId',
      key: 'systemName',
      width: 180,
      render: (sysId: number) => systems.find((s) => s.id === sysId)?.name ?? `系统 #${sysId}`,
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 110,
      render: (type: Task['type']) => (
        <Tag color={type === 'INITIAL' ? 'geekblue' : 'green'}>
          {type === 'INITIAL' ? '全量' : '增量'}
        </Tag>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 160,
      render: (status: string) => {
        const meta = statusMeta[status] ?? { color: 'default', label: status };
        return (
          <Tag
            color={meta.color}
            icon={status === 'ENTRYPOINT_REVIEW' ? <LoadingOutlined /> : undefined}
          >
            {meta.label}
          </Tag>
        );
      },
    },
    {
      title: '知识入口复核',
      dataIndex: 'requireEntrypointReview',
      key: 'requireEntrypointReview',
      width: 140,
      render: (v: boolean | undefined) =>
        v === false ? <Tag>跳过</Tag> : <Tag color="cyan">启用</Tag>,
    },
    {
      title: '进度',
      dataIndex: 'progress',
      key: 'progress',
      width: 100,
      render: (p: number) => `${p}%`,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (t: string) => (t ? new Date(t).toLocaleString() : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_: unknown, record: Task) => (
        <Space>
          <Button size="small" icon={<EyeOutlined />} onClick={() => navigate(`/tasks/${record.id}`)}>
            详情
          </Button>
          {record.status === 'ENTRYPOINT_REVIEW' && (
            <Tooltip title="进入知识入口复核抽屉">
              <Button
                size="small"
                type="primary"
                icon={<EditOutlined />}
                onClick={() => openDrawer(record.id)}
              >
                开始复核
              </Button>
            </Tooltip>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div className="ci-page ci-entrypoint-review-page">
      <Card
        title={
          <Space>
            <span>待复核任务</span>
            <Tag color="cyan">等待复核 {reviewCount}</Tag>
          </Space>
        }
        extra={
          <Space>
            <Button icon={<ReloadOutlined />} loading={loading} onClick={fetchReviewTasks}>
              刷新
            </Button>
            <Button type={showHistory ? 'primary' : 'default'} onClick={() => setShowHistory((v) => !v)}>
              {showHistory ? '仅看待复核' : '查看近期历史'}
            </Button>
          </Space>
        }
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message="知识入口复核说明"
          description={
            <ul style={{ margin: 0, paddingLeft: 18 }}>
              <li>仅启用「知识入口复核」的任务会停在此状态等待人工确认。</li>
              <li>
                复核抽屉是<strong>只读视图</strong>：展示识别到的入口类与每个类下的关键方法；
                用户只能<strong>确认并继续</strong>或<strong>驳回任务</strong>，不能直接增删改入口。
              </li>
              <li>
                如发现入口清单与预期不一致（例如少了 Controller / 混入测试类），请回到
                「创建任务」或「代码库配置」调整 <Text code>entry_scan_config</Text>（include / exclude
                规则）后重新创建任务。
              </li>
              <li>
                「确认」后任务进入 AI_ANALYZING → 模块层级（按 requireHierarchyReview 决定是否再触发模块层级复核）；
                「驳回」后任务直接终止（CANCELLED），不会留下任何知识资产。
              </li>
            </ul>
          }
        />

        {tasks.length === 0 && !loading ? (
          <Empty description="暂无需要复核的任务" />
        ) : (
          <Table
            dataSource={tasks}
            columns={columns}
            rowKey="id"
            loading={loading}
            pagination={{ pageSize: 20, showSizeChanger: true }}
          />
        )}
      </Card>

      <EntrypointReviewDrawer
        open={drawerOpen}
        taskId={currentTaskId}
        onClose={closeDrawer}
        onSubmitted={() => {
          message.success('已提交');
          fetchReviewTasks();
        }}
      />
    </div>
  );
};

export default EntrypointReview;