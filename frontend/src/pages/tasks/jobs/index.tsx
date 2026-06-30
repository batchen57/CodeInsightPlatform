import type React from 'react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Badge,
  Button,
  Card,
  Empty,
  Input,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  message,
} from 'antd';
import {
  ClockCircleOutlined,
  DeleteOutlined,
  EditOutlined,
  HistoryOutlined,
  PlusOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { Link, useNavigate } from 'react-router-dom';
import {
  deleteSchedule,
  disableSchedule,
  enableSchedule,
  listSchedules,
  triggerScheduleNow,
} from '../../../api/schedule';
import { listSystems } from '../../../api/system';
import { listRepositories } from '../../../api/repository';
import type {
  FireStrategy,
  OverlapStrategy,
  Repository,
  ScheduleTask,
  System,
} from '../../../types';
import { cronPresets } from '../../schedules/cron-presets';

/**
 * 「JOB配置」页签：定时任务列表
 *
 * 由原 /schedules 页面迁移而来：
 *  - "新建定时任务" 跳到 /tasks/jobs/new（全屏页面）
 *  - 表格行 "编辑" 跳到 /tasks/jobs/:id/edit
 *  - 名称点击仍可进入 /tasks/jobs/:id 详情
 */
const JobsList: React.FC = () => {
  const navigate = useNavigate();

  const [listLoading, setListLoading] = useState(false);
  const [records, setRecords] = useState<ScheduleTask[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);
  const [filterSystemId, setFilterSystemId] = useState<number | undefined>();
  const [filterRepositoryId, setFilterRepositoryId] = useState<number | undefined>();
  const [filterEnabled, setFilterEnabled] = useState<boolean | undefined>();
  const [keyword, setKeyword] = useState('');

  const [systems, setSystems] = useState<System[]>([]);
  const [repositories, setRepositories] = useState<Repository[]>([]);

  const systemMap = useMemo(() => new Map(systems.map((s) => [s.id, s.name])), [systems]);
  const repositoryMap = useMemo(
    () => new Map(repositories.map((r) => [r.id, r.gitUrl])),
    [repositories],
  );

  const loadSchedules = useCallback(async () => {
    setListLoading(true);
    try {
      const res = await listSchedules({
        current,
        size,
        systemId: filterSystemId,
        repositoryId: filterRepositoryId,
        enabled: filterEnabled,
        keyword: keyword || undefined,
      });
      setRecords(res.records);
      setTotal(res.total);
    } catch {
      // ignore
    } finally {
      setListLoading(false);
    }
  }, [current, size, filterSystemId, filterRepositoryId, filterEnabled, keyword]);

  const loadBaseOptions = useCallback(async () => {
    try {
      const [sysPage, repoPage] = await Promise.all([
        listSystems({ current: 1, size: 100 }),
        listRepositories({ current: 1, size: 200 }),
      ]);
      setSystems(sysPage.records);
      setRepositories(repoPage.records);
    } catch {
      // ignore
    }
  }, []);

  useEffect(() => {
    loadBaseOptions();
  }, [loadBaseOptions]);

  useEffect(() => {
    loadSchedules();
  }, [loadSchedules]);

  const handleToggleEnabled = async (s: ScheduleTask, next: boolean) => {
    try {
      if (next) await enableSchedule(s.id);
      else await disableSchedule(s.id);
      message.success(next ? '已启用' : '已禁用');
      loadSchedules();
    } catch {
      // ignore
    }
  };

  const handleTrigger = async (s: ScheduleTask) => {
    try {
      const res = await triggerScheduleNow(s.id);
      message.success(res.taskId ? `已触发，创建任务 #${res.taskId}` : '已触发');
      if (res.taskId) {
        if (window.confirm('已创建知识构建任务，是否前往任务详情？')) {
          navigate(`/tasks/${res.taskId}`);
        }
      }
      loadSchedules();
    } catch {
      // ignore
    }
  };

  const handleDelete = async (s: ScheduleTask) => {
    try {
      await deleteSchedule(s.id);
      message.success('已删除');
      loadSchedules();
    } catch {
      // ignore
    }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    {
      title: '名称',
      dataIndex: 'name',
      render: (v: string, r: ScheduleTask) => (
        <Link to={`/tasks/jobs/${r.id}`}>{v}</Link>
      ),
    },
    {
      title: '系统',
      dataIndex: 'systemId',
      width: 140,
      render: (id: number) => systemMap.get(id) ?? `#${id}`,
    },
    {
      title: '代码库',
      dataIndex: 'repositoryId',
      width: 220,
      ellipsis: true,
      render: (id: number) => repositoryMap.get(id) ?? `#${id}`,
    },
    {
      title: '触发策略',
      dataIndex: 'fireStrategy',
      width: 110,
      render: (v: FireStrategy) =>
        v === 'INITIAL' ? <Tag color="purple">全量</Tag> : <Tag color="blue">增量</Tag>,
    },
    {
      title: '冲突策略',
      dataIndex: 'overlapStrategy',
      width: 110,
      render: (v: OverlapStrategy) => {
        const map: Record<OverlapStrategy, { color: string; text: string }> = {
          SKIP: { color: 'default', text: '跳过' },
          QUEUE: { color: 'orange', text: '排队' },
          PARALLEL: { color: 'green', text: '并行' },
        };
        const m = map[v] ?? { color: 'default', text: v };
        return <Tag color={m.color}>{m.text}</Tag>;
      },
    },
    {
      title: 'Cron',
      dataIndex: 'cronExpression',
      width: 130,
      render: (v: string) => (
        <Tooltip title={cronPresets.find((c) => c.value === v)?.label ?? v}>
          <code>{v}</code>
        </Tooltip>
      ),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 80,
      render: (v: number, r: ScheduleTask) => (
        <Switch
          size="small"
          checked={v === 1}
          onChange={(next) => handleToggleEnabled(r, next)}
        />
      ),
    },
    {
      title: '上次触发',
      width: 220,
      render: (_: unknown, r: ScheduleTask) =>
        r.lastFiredAt ? (
          <Space direction="vertical" size={2}>
            <span>{dayjs(r.lastFiredAt).format('YYYY-MM-DD HH:mm:ss')}</span>
            <Tag
              color={
                r.lastStatus === 'SUCCESS'
                  ? 'green'
                  : r.lastStatus === 'FAILED'
                    ? 'red'
                    : r.lastStatus === 'SKIPPED'
                      ? 'default'
                      : r.lastStatus === 'QUEUED'
                        ? 'orange'
                        : 'blue'
              }
            >
              {r.lastStatus ?? '-'}
            </Tag>
            {r.lastTaskId && <Link to={`/tasks/${r.lastTaskId}`}>#{r.lastTaskId}</Link>}
          </Space>
        ) : (
          <span style={{ color: '#999' }}>尚未触发</span>
        ),
    },
    {
      title: '下次触发',
      dataIndex: 'nextFireAt',
      width: 150,
      render: (v?: string) =>
        v ? (
          <Space size={4}>
            <ClockCircleOutlined />
            {dayjs(v).format('YYYY-MM-DD HH:mm:ss')}
          </Space>
        ) : (
          <span style={{ color: '#999' }}>-</span>
        ),
    },
    {
      title: '统计',
      width: 140,
      render: (_: unknown, r: ScheduleTask) => (
        <Tooltip
          title={
            <div>
              <div>触发次数：{r.totalFired}</div>
              <div>成功：{r.totalSuccess}</div>
              <div>失败：{r.totalFailed}</div>
              <div>跳过：{r.totalSkipped}</div>
            </div>
          }
        >
          <Badge
            status={r.totalFailed > 0 ? 'error' : r.totalSuccess > 0 ? 'success' : 'default'}
            text={`${r.totalSuccess ?? 0}/${r.totalFired ?? 0}`}
          />
        </Tooltip>
      ),
    },
    {
      title: '操作',
      width: 220,
      fixed: 'right' as const,
      render: (_: unknown, r: ScheduleTask) => (
        <Space size={4}>
          <Tooltip title="立即触发">
            <Button
              size="small"
              type="link"
              icon={<ThunderboltOutlined />}
              onClick={() => handleTrigger(r)}
              disabled={r.enabled === 0}
            />
          </Tooltip>
          <Tooltip title="触发历史">
            <Button
              size="small"
              type="link"
              icon={<HistoryOutlined />}
              onClick={() => navigate(`/tasks/jobs/${r.id}`)}
            />
          </Tooltip>
          <Tooltip title="编辑">
            <Button
              size="small"
              type="link"
              icon={<EditOutlined />}
              onClick={() => navigate(`/tasks/jobs/${r.id}/edit`)}
            />
          </Tooltip>
          <Popconfirm
            title="确认删除？"
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true }}
            onConfirm={() => handleDelete(r)}
          >
            <Button size="small" type="link" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div className="ci-page">
      <Card bordered={false} className="ci-filterbar">
        <Space wrap size={12}>
          <Input.Search
            allowClear
            placeholder="搜索名称或描述"
            style={{ width: 220 }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onSearch={() => {
              setCurrent(1);
              loadSchedules();
            }}
          />
          <Select
            allowClear
            placeholder="系统"
            style={{ width: 180 }}
            value={filterSystemId}
            onChange={(v) => {
              setFilterSystemId(v);
              setCurrent(1);
            }}
            options={systems.map((s) => ({ label: s.name, value: s.id }))}
          />
          <Select
            allowClear
            placeholder="代码库"
            style={{ width: 220 }}
            value={filterRepositoryId}
            onChange={(v) => {
              setFilterRepositoryId(v);
              setCurrent(1);
            }}
            options={repositories.map((r) => ({ label: r.gitUrl, value: r.id }))}
          />
          <Select
            allowClear
            placeholder="启用状态"
            style={{ width: 130 }}
            value={filterEnabled}
            onChange={(v) => {
              setFilterEnabled(v);
              setCurrent(1);
            }}
            options={[
              { label: '启用', value: true },
              { label: '禁用', value: false },
            ]}
          />
          <Button
            icon={<ReloadOutlined />}
            onClick={() => {
              setCurrent(1);
              loadSchedules();
            }}
          >
            刷新
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate('/tasks/jobs/new')}
          >
            新建定时任务
          </Button>
        </Space>
      </Card>

      <Card bordered={false} className="ci-table-card">
        <Table
          rowKey="id"
          loading={listLoading}
          columns={columns}
          dataSource={records}
          scroll={{ x: 1480 }}
          pagination={{
            current,
            pageSize: size,
            total,
            showSizeChanger: true,
            onChange: (p, ps) => {
              setCurrent(p);
              setSize(ps);
            },
          }}
          locale={{ emptyText: <Empty description="暂无定时任务" /> }}
        />
      </Card>
    </div>
  );
};

export default JobsList;
