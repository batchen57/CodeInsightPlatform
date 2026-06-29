import { Button, Popconfirm, Space, Switch, Tag, Typography } from 'antd';
import {
  BarChartOutlined,
  DeleteOutlined,
  EditOutlined,
  ExperimentOutlined,
  HolderOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import type { AiModel, AiModelMetricSummary } from '../../../types';

const { Text } = Typography;

const formatCompact = (value?: number) =>
  new Intl.NumberFormat('zh-CN', {
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(Number(value ?? 0));

const formatCost = (value?: number) => `$${Number(value ?? 0).toFixed(4)}`;

export interface ModelColumnHandlers {
  onEdit: (model: AiModel) => void;
  onDelete: (id: number) => void;
  onTest: (model: AiModel) => void;
  onViewMetrics: (model: AiModel) => void;
  onToggleDefault: (checked: boolean, model: AiModel) => void;
  onToggleStatus: (checked: boolean, model: AiModel) => void;
  onDragHandleEnter: () => void;
  onDragHandleLeave: () => void;
}

export interface ModelColumnOptions {
  metricsByModel: Record<string, AiModelMetricSummary | undefined>;
  testingModelId?: number | null;
}

/** 模型表列定义工厂
 *  helper 函数（拖拽把手、capabilities 渲染）都内联在列 render 里，
 *  避免模块级非组件函数触发 fast-refresh lint。
 */
export const getModelColumns = (handlers: ModelColumnHandlers, options: ModelColumnOptions) => [
  {
    title: '排序',
    key: 'order',
    width: 80,
    render: (_: unknown, _record: AiModel, index: number) => (
      <div
        onMouseEnter={handlers.onDragHandleEnter}
        onMouseLeave={handlers.onDragHandleLeave}
        style={{ display: 'flex', alignItems: 'center', padding: '4px', cursor: 'grab' }}
        title="拖拽排序"
      >
        <HolderOutlined style={{ fontSize: '15px', color: '#8c8c8c' }} />
        <Tag
          color="cyan"
          style={{
            margin: 0,
            marginLeft: 4,
            minWidth: '22px',
            height: '22px',
            lineHeight: '20px',
            textAlign: 'center',
            borderRadius: '4px',
            fontWeight: 600,
            border: 'none',
          }}
        >
          {index + 1}
        </Tag>
      </div>
    ),
  },
  {
    title: '模型信息',
    key: 'modelInfo',
    render: (_: unknown, record: AiModel) => (
      <Space direction="vertical" size={2} style={{ width: '100%' }}>
        <Space size={8} style={{ flexWrap: 'wrap' }}>
          <Text strong style={{ fontSize: '14px' }}>{record.name}</Text>
          <Tag color="blue" style={{ border: 'none', borderRadius: '4px', margin: 0 }}>
            {record.provider}
          </Tag>
          {record.isDefault === 'true' && (
            <Tag
              color="gold"
              icon={<ThunderboltOutlined />}
              style={{ border: 'none', borderRadius: '4px', margin: 0 }}
            >
              默认
            </Tag>
          )}
        </Space>
        <div style={{ marginTop: 2 }}>
          <Text type="secondary" code style={{ fontSize: '12px' }}>
            {record.identifier}
          </Text>
        </div>
      </Space>
    ),
  },
  {
    title: 'Endpoint URL (接口地址)',
    dataIndex: 'baseUrl',
    key: 'baseUrl',
    ellipsis: { showTitle: true },
    render: (text: string) =>
      text ? (
        <Text type="secondary" copyable style={{ fontSize: '13px', fontFamily: 'monospace' }}>
          {text}
        </Text>
      ) : (
        <Text type="secondary">—</Text>
      ),
  },
  {
    title: '支持能力',
    dataIndex: 'capabilities',
    key: 'capabilities',
    width: 180,
    render: (text?: string) => {
      if (!text) return <Text type="secondary" italic>无</Text>;
      const map: Record<string, { label: string; color: string }> = {
        text: { label: '文本', color: 'blue' },
        image: { label: '图片', color: 'green' },
        video: { label: '视频', color: 'purple' },
      };
      return (
        <Space size={4}>
          {text.split(',').map((cap) => {
            const m = map[cap] ?? { label: cap, color: 'default' };
            return (
              <Tag key={cap} color={m.color}>
                {m.label}
              </Tag>
            );
          })}
        </Space>
      );
    },
  },
  {
    title: '总调用',
    key: 'totalCalls',
    width: 110,
    render: (_: unknown, record: AiModel) => {
      const metrics = options.metricsByModel[record.identifier];
      return <Text strong>{formatCompact(metrics?.totalCalls)}</Text>;
    },
  },
  {
    title: '总 Token',
    key: 'totalTokens',
    width: 120,
    render: (_: unknown, record: AiModel) => {
      const metrics = options.metricsByModel[record.identifier];
      return <Text>{formatCompact(metrics?.totalTokens)}</Text>;
    },
  },
  {
    title: '总成本',
    key: 'totalCost',
    width: 110,
    render: (_: unknown, record: AiModel) => {
      const metrics = options.metricsByModel[record.identifier];
      return <Text>{formatCost(metrics?.totalCost)}</Text>;
    },
  },
  {
    title: '状态',
    key: 'status',
    width: 100,
    render: (_: unknown, record: AiModel) => {
      const enabled = (record.status ?? 1) === 1;
      return (
        <Switch
          checkedChildren="启用"
          unCheckedChildren="停用"
          checked={enabled}
          onChange={(checked) => handlers.onToggleStatus(checked, record)}
        />
      );
    },
  },
  {
    title: '默认模型',
    key: 'isDefault',
    width: 100,
    render: (_: unknown, record: AiModel) => (
      <Switch
        checked={record.isDefault === 'true'}
        onChange={(checked) => handlers.onToggleDefault(checked, record)}
      />
    ),
  },
  {
    title: '操作',
    key: 'action',
    width: 250,
    fixed: 'right' as const,
    render: (_: unknown, record: AiModel) => (
      <Space size={8}>
        <Button
          type="link"
          size="small"
          icon={<ExperimentOutlined />}
          loading={options.testingModelId === record.id}
          onClick={() => handlers.onTest(record)}
        >
          测试
        </Button>
        <Button
          type="link"
          size="small"
          icon={<BarChartOutlined />}
          onClick={() => handlers.onViewMetrics(record)}
        >
          详情
        </Button>
        <Button
          type="link"
          size="small"
          icon={<EditOutlined />}
          onClick={() => handlers.onEdit(record)}
        >
          编辑
        </Button>
        <Popconfirm
          title="确认要删除该模型配置吗？"
          onConfirm={() => handlers.onDelete(record.id)}
          okText="确认"
          cancelText="取消"
        >
          <Button type="link" size="small" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      </Space>
    ),
  },
];
