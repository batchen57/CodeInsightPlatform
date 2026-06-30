import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  Card,
  DatePicker,
  Drawer,
  Empty,
  Input,
  Segmented,
  Select,
  Skeleton,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  ClearOutlined,
  CopyOutlined,
  DownloadOutlined,
  FilterOutlined,
  ReloadOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { Dayjs } from 'dayjs';
import { listSystems } from '../../api/system';
import { listTasks } from '../../api/task';
import {
  getKnowledgeBrowseContent,
  listKnowledgeBrowse,
} from '../../api/knowledge-browse';
import MarkdownView from '../../components/MarkdownView';
import type {
  KnowledgeBrowseFileType,
  KnowledgeBrowseItem,
  KnowledgeBrowseQuery,
  System,
  Task,
} from '../../types';

const { Text, Paragraph } = Typography;
const { RangePicker } = DatePicker;

const TYPE_OPTIONS: { value: KnowledgeBrowseFileType | 'ALL'; label: string }[] = [
  { value: 'ALL', label: '全部' },
  { value: 'DRAFT', label: '知识文档' },
  { value: 'INDEX', label: '索引文档' },
  { value: 'MANIFEST', label: '清单文件' },
];

const TYPE_TAG_META: Record<KnowledgeBrowseFileType, { color: string; label: string }> = {
  DRAFT: { color: 'blue', label: '知识文档' },
  INDEX: { color: 'cyan', label: '索引文档' },
  MANIFEST: { color: 'purple', label: '清单文件' },
  ALL: { color: 'default', label: '全部' },
};

const STATUS_OPTIONS = [
  { value: 'DRAFT', label: 'DRAFT' },
  { value: 'EDITING', label: 'EDITING' },
  { value: 'CONFIRMED', label: 'CONFIRMED' },
  { value: 'PUSHED', label: 'PUSHED' },
  { value: 'ARCHIVED', label: 'ARCHIVED' },
];

/** Markdown / YAML / JSON 文件 → 用 MarkdownView 渲染；其它纯文本走 <pre> */
function PreviewContent({ text, type }: { text: string; type: KnowledgeBrowseFileType }) {
  const lowerName = (text || '').toLowerCase();
  if (type === 'DRAFT' || type === 'INDEX') {
    return <MarkdownView content={text} />;
  }
  // MANIFEST：可能是 yaml 或 json；尝试用 markdown（代码块降级）
  if (lowerName.trimStart().startsWith('{') || lowerName.trimStart().startsWith('[')) {
    return <MarkdownView content={'```json\n' + text + '\n```'} />;
  }
  // YAML 走代码块
  return <MarkdownView content={'```yaml\n' + text + '\n```'} />;
}

const KnowledgeBrowse: React.FC = () => {
  // ────────── 上下文 ──────────
  const [systems, setSystems] = useState<System[]>([]);
  const [systemId, setSystemId] = useState<number | undefined>(undefined);
  const [type, setType] = useState<KnowledgeBrowseFileType | 'ALL'>('ALL');
  const [keyword, setKeyword] = useState<string>('');

  // ────────── 高级筛选 ──────────
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [status, setStatus] = useState<string | undefined>(undefined);
  const [taskId, setTaskId] = useState<number | undefined>(undefined);
  const [dateRange, setDateRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);

  // ────────── 任务列表（按 system 过滤，给高级筛选用） ──────────
  const [tasks, setTasks] = useState<Task[]>([]);

  // ────────── 主列表 ──────────
  const [items, setItems] = useState<KnowledgeBrowseItem[]>([]);
  const [loading, setLoading] = useState(false);

  // ────────── 内容预览 ──────────
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewText, setPreviewText] = useState<string>('');
  const [previewItem, setPreviewItem] = useState<KnowledgeBrowseItem | null>(null);

  // ────────── 副作用：拉系统列表 ──────────
  useEffect(() => {
    listSystems({ current: 1, size: 200 })
      .then((data) => setSystems(data.records ?? []))
      .catch(() => {/* request.ts 已统一弹错 */});
  }, []);

  // ────────── 副作用：选系统后拉任务列表 ──────────
  useEffect(() => {
    if (systemId == null) {
      setTasks([]);
      return;
    }
    listTasks({ current: 1, size: 200, systemId })
      .then((data) => setTasks(data.records ?? []))
      .catch(() => {/* request.ts 已统一弹错 */});
  }, [systemId]);

  // ────────── 拉主列表 ──────────
  const fetchItems = useCallback(async () => {
    if (systemId == null) {
      setItems([]);
      return;
    }
    const query: KnowledgeBrowseQuery = {
      systemId,
      type: type === 'ALL' ? 'ALL' : type,
      keyword: keyword.trim() || undefined,
      taskId,
      status: type === 'DRAFT' ? status : undefined,
      createdAtStart: dateRange?.[0]?.startOf('day').toISOString(),
      createdAtEnd: dateRange?.[1]?.endOf('day').toISOString(),
    };
    setLoading(true);
    try {
      const data = await listKnowledgeBrowse(query);
      setItems(data ?? []);
    } catch (e) {
      // 已在拦截器弹错
    } finally {
      setLoading(false);
    }
  }, [systemId, type, keyword, taskId, status, dateRange]);

  useEffect(() => {
    // 进入页面 / 任意过滤条件变化 → 重新拉
    fetchItems();
  }, [fetchItems]);

  // ────────── 内容预览 ──────────
  const openPreview = async (it: KnowledgeBrowseItem) => {
    setPreviewItem(it);
    setPreviewText('');
    setPreviewOpen(true);
    setPreviewLoading(true);
    try {
      const params =
        it.type === 'DRAFT'
          ? { type: it.type, id: Number(it.id.split(':')[1]) }
          : {
              type: it.type,
              taskId: it.taskId,
              filePath: it.filePath,
            };
      const text = await getKnowledgeBrowseContent(params);
      setPreviewText(text ?? '');
    } catch (e) {
      setPreviewText('');
    } finally {
      setPreviewLoading(false);
    }
  };

  const closePreview = () => {
    setPreviewOpen(false);
    setPreviewItem(null);
    setPreviewText('');
  };

  // ────────── 工具 ──────────
  const copyPreview = async () => {
    try {
      await navigator.clipboard.writeText(previewText);
      message.success('已复制到剪贴板');
    } catch {
      message.error('复制失败（浏览器可能未授权剪贴板权限）');
    }
  };

  const downloadPreview = () => {
    if (!previewItem) return;
    const blob = new Blob([previewText], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = previewItem.name || 'document';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  };

  const resetAdvanced = () => {
    setStatus(undefined);
    setTaskId(undefined);
    setDateRange(null);
  };

  const formatBytes = (n?: number) => {
    if (n == null) return '-';
    if (n < 1024) return `${n} B`;
    if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
    return `${(n / 1024 / 1024).toFixed(2)} MB`;
  };

  const advancedActiveCount = useMemo(() => {
    let n = 0;
    if (status) n += 1;
    if (taskId) n += 1;
    if (dateRange && (dateRange[0] || dateRange[1])) n += 1;
    return n;
  }, [status, taskId, dateRange]);

  // ────────── 表格列 ──────────
  const columns = [
    {
      title: '文件名',
      dataIndex: 'name',
      key: 'name',
      width: 280,
      ellipsis: true,
      render: (v: string, r: KnowledgeBrowseItem) => (
        <Tooltip title={r.filePath}>
          <Space size={4}>
            <Text strong>{v}</Text>
            {r.filePath && (
              <Text type="secondary" style={{ fontSize: 12 }}>
                {r.filePath}
              </Text>
            )}
          </Space>
        </Tooltip>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 110,
      render: (t: KnowledgeBrowseFileType) => (
        <Tag color={TYPE_TAG_META[t]?.color}>{TYPE_TAG_META[t]?.label ?? t}</Tag>
      ),
      filters: TYPE_OPTIONS.map((o) => ({ text: o.label, value: o.value })),
      onFilter: (value: any, record: KnowledgeBrowseItem) => record.type === value,
    },
    {
      title: '任务',
      dataIndex: 'taskId',
      key: 'taskId',
      width: 100,
      render: (id?: number) => (id ? <Text code>#{id}</Text> : '-'),
    },
    {
      title: '版本',
      dataIndex: 'versionNum',
      key: 'versionNum',
      width: 110,
      render: (v?: string) => (v ? <Tag>{v}</Tag> : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (s: string | undefined, r: KnowledgeBrowseItem) => {
        if (!s) return '-';
        if (r.type === 'DRAFT') {
          const color =
            s === 'CONFIRMED' || s === 'PUSHED'
              ? 'green'
              : s === 'EDITING'
              ? 'gold'
              : s === 'ARCHIVED'
              ? 'default'
              : 'blue';
          return <Tag color={color}>{s}</Tag>;
        }
        return <Tag color="cyan">已生成</Tag>;
      },
    },
    {
      title: '大小',
      dataIndex: 'size',
      key: 'size',
      width: 100,
      render: (n: number) => <Text type="secondary">{formatBytes(n)}</Text>,
    },
    {
      title: '来源',
      dataIndex: 'source',
      key: 'source',
      width: 90,
      render: (s: string) => (
        <Tag color={s === 'DB' ? 'blue' : 'orange'}>
          {s === 'DB' ? '数据库' : '临时仓库'}
        </Tag>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 170,
      render: (s?: string) => (s ? new Date(s).toLocaleString() : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 90,
      render: (_: unknown, r: KnowledgeBrowseItem) => (
        <Button type="link" size="small" onClick={() => openPreview(r)}>
          查看
        </Button>
      ),
    },
  ];

  return (
    <div className="ci-page ci-knowledge-browse-page">
      <Card>
        <Space size={12} wrap style={{ width: '100%', justifyContent: 'space-between' }}>
          <Space size={12} wrap>
            <Space size={4}>
              <Text type="secondary">系统</Text>
              <Select
                placeholder="请选择系统（必填）"
                value={systemId}
                onChange={(v) => setSystemId(v)}
                style={{ width: 240 }}
                showSearch
                optionFilterProp="label"
                options={systems.map((s) => ({ value: s.id, label: s.name }))}
                allowClear
              />
            </Space>
            <Segmented
              options={TYPE_OPTIONS.map((o) => ({ value: o.value, label: o.label }))}
              value={type}
              onChange={(v) => setType(v as KnowledgeBrowseFileType | 'ALL')}
            />
          </Space>
          <Space size={8} wrap>
            <Input
              placeholder="按文件名搜索…"
              prefix={<SearchOutlined />}
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onPressEnter={() => fetchItems()}
              allowClear
              style={{ width: 240 }}
            />
            <Tooltip title="高级筛选">
              <Badge count={advancedActiveCount} size="small" offset={[-4, 4]}>
                <Button
                  icon={<FilterOutlined />}
                  onClick={() => setAdvancedOpen(true)}
                >
                  高级筛选
                </Button>
              </Badge>
            </Tooltip>
            <Tooltip title="刷新">
              <Button
                icon={<ReloadOutlined />}
                loading={loading}
                onClick={() => fetchItems()}
              />
            </Tooltip>
          </Space>
        </Space>
      </Card>

      {systemId == null ? (
        <Card style={{ marginTop: 16 }}>
          <Empty description="请先选择系统" />
        </Card>
      ) : (
        <Card style={{ marginTop: 16 }} bodyStyle={{ padding: 0 }}>
          <Table<KnowledgeBrowseItem>
            dataSource={items}
            columns={columns as any}
            rowKey="id"
            loading={loading}
            pagination={{
              pageSize: 20,
              showSizeChanger: true,
              showTotal: (t) => `共 ${t} 条`,
            }}
            locale={{ emptyText: <Empty description="该条件下没有可浏览的文件" /> }}
            onRow={(record) => ({
              onClick: () => openPreview(record),
              style: { cursor: 'pointer' },
            })}
          />
        </Card>
      )}

      {/* 高级筛选 Drawer */}
      <Drawer
        title="高级筛选"
        width={360}
        open={advancedOpen}
        onClose={() => setAdvancedOpen(false)}
        extra={
          <Space>
            <Button icon={<ClearOutlined />} onClick={resetAdvanced}>
              清空
            </Button>
            <Button type="primary" onClick={() => setAdvancedOpen(false)}>
              应用筛选
            </Button>
          </Space>
        }
      >
        <Space direction="vertical" size={20} style={{ width: '100%' }}>
          <div>
            <Text type="secondary" style={{ display: 'block', marginBottom: 6 }}>
              状态（仅对知识文档生效）
            </Text>
            <Select
              placeholder="全部状态"
              value={status}
              onChange={(v) => setStatus(v)}
              allowClear
              style={{ width: '100%' }}
              options={STATUS_OPTIONS}
              disabled={type !== 'DRAFT'}
            />
          </div>
          <div>
            <Text type="secondary" style={{ display: 'block', marginBottom: 6 }}>
              所属任务
            </Text>
            <Select
              placeholder="全部任务"
              value={taskId}
              onChange={(v) => setTaskId(v)}
              allowClear
              showSearch
              optionFilterProp="label"
              style={{ width: '100%' }}
              options={tasks.map((t) => ({
                value: t.id,
                label: `#${t.id} ${t.type === 'INITIAL' ? '全量' : '增量'}`,
              }))}
            />
          </div>
          <div>
            <Text type="secondary" style={{ display: 'block', marginBottom: 6 }}>
              更新时间区间
            </Text>
            <RangePicker
              value={dateRange as any}
              onChange={(v) => setDateRange(v as any)}
              style={{ width: '100%' }}
              allowClear
            />
          </div>
          <Alert
            type="info"
            showIcon
            message="筛选说明"
            description={
              <ul style={{ margin: 0, paddingLeft: 18 }}>
                <li>「状态」仅对「知识文档」类型生效。</li>
                <li>索引 / 清单文件读自临时仓库，文件被清理后不会出现在列表中。</li>
                <li>修改后点「应用筛选」或刷新即可。</li>
              </ul>
            }
          />
        </Space>
      </Drawer>

      {/* 内容预览 Drawer */}
      <Drawer
        title={
          previewItem ? (
            <Space>
              <Text strong>{previewItem.name}</Text>
              <Tag color={TYPE_TAG_META[previewItem.type]?.color}>
                {TYPE_TAG_META[previewItem.type]?.label}
              </Tag>
              {previewItem.taskId && <Text type="secondary">#{previewItem.taskId}</Text>}
            </Space>
          ) : (
            '文件内容'
          )
        }
        width="62%"
        open={previewOpen}
        onClose={closePreview}
        destroyOnClose
        extra={
          <Space>
            <Button
              icon={<CopyOutlined />}
              onClick={copyPreview}
              disabled={!previewText}
            >
              复制
            </Button>
            <Button
              icon={<DownloadOutlined />}
              onClick={downloadPreview}
              disabled={!previewText}
            >
              下载
            </Button>
          </Space>
        }
      >
        {previewLoading ? (
          <Skeleton active paragraph={{ rows: 12 }} />
        ) : !previewItem ? null : !previewText ? (
          <Empty description="无可显示内容（文件可能为空或读取失败）" />
        ) : (
          <>
            <Paragraph
              type="secondary"
              style={{ fontSize: 12, marginBottom: 12 }}
              copyable={{ text: previewItem.filePath }}
            >
              路径：{previewItem.filePath} · {formatBytes(previewItem.size)}
            </Paragraph>
            <PreviewContent text={previewText} type={previewItem.type} />
          </>
        )}
      </Drawer>
    </div>
  );
};

export default KnowledgeBrowse;