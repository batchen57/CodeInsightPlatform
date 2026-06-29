import { Button, Popconfirm, Space, Switch, Tag, Tooltip, Typography } from 'antd';
import { DeleteOutlined, EditOutlined, SettingOutlined } from '@ant-design/icons';
import type { System, SystemState } from '../../types';
import SystemStatusTag from '../../components/SystemStatusTag';

const { Text } = Typography;

/** 系统表各操作列的事件回调 */
export interface SystemColumnHandlers {
  onEdit: (system: System) => void;
  onOpenDetail: (system: System) => void;
  onDelete: (system: System) => void;
  /** 启用 / 停用：state 在 PROMPT_CONFIGURED / ACTIVE / DISABLED 之间切换 */
  onStatusToggle: (nextActive: boolean, system: System) => void;
}

/**
 * 状态 → 是否能切换启用/停用：仅 PROMPT_CONFIGURED / ACTIVE / DISABLED 可手动切
 */
function canToggleState(state?: SystemState): boolean {
  return (
    state === 'PROMPT_CONFIGURED' ||
    state === 'ACTIVE' ||
    state === 'DISABLED'
  );
}

const NEXT_ACTIVE_STATE: Record<string, 'ACTIVE' | 'DISABLED'> = {
  PROMPT_CONFIGURED: 'ACTIVE',
  ACTIVE: 'DISABLED',
  DISABLED: 'ACTIVE',
};

/**
 * 系统主表列定义工厂
 * 把 handlers 注入后返回 columns 数组（避免在组件里写大段 render）
 */
export const getSystemColumns = (handlers: SystemColumnHandlers) => [
  {
    title: '系统',
    dataIndex: 'name',
    key: 'name',
    width: 200,
    fixed: 'left' as const,
    render: (text: string, record: System) => (
      <Button type="link" className="ci-table-link" onClick={() => handlers.onOpenDetail(record)}>
        {text}
      </Button>
    ),
  },
  {
    title: '中文名称',
    dataIndex: 'nameCn',
    key: 'nameCn',
    width: 180,
    render: (nameCn?: string) => nameCn || <Text type="secondary">未填写</Text>,
  },
  {
    title: '负责人',
    dataIndex: 'owner',
    key: 'owner',
    width: 110,
    render: (owner: string) => <Tag color="blue">{owner || '未分配'}</Tag>,
  },
  {
    title: '状态',
    dataIndex: 'state',
    key: 'state',
    width: 130,
    render: (state: SystemState | undefined) => <SystemStatusTag state={state} />,
  },
  {
    title: '代码库数',
    dataIndex: 'repositoryCount',
    key: 'repositoryCount',
    width: 100,
    render: (n?: number) =>
      typeof n === 'number' ? <Tag color={n > 0 ? 'geekblue' : 'default'}>{n}</Tag> : '-',
  },
  {
    title: '知识版本',
    dataIndex: 'knowledgeVersionCount',
    key: 'knowledgeVersionCount',
    width: 100,
    render: (n?: number) =>
      typeof n === 'number' ? <Tag color={n > 0 ? 'green' : 'default'}>{n}</Tag> : '-',
  },
  {
    title: '最近扫描',
    dataIndex: 'lastDecompileAt',
    key: 'lastDecompileAt',
    width: 160,
    render: (time?: string) =>
      time ? new Date(time).toLocaleString() : <Text type="secondary">未扫描</Text>,
  },
  {
    title: '启停',
    key: 'toggle',
    width: 110,
    render: (_: unknown, record: System) => {
      const state = record.state;
      const enabled = state === 'ACTIVE';
      const toggleable = canToggleState(state);
      const nextActive = !enabled;
      const tip = !toggleable
        ? '请先完成基本信息 / 仓库 / 入口扫描 / 提示词 4 步配置'
        : enabled
          ? '点击停用'
          : `点击启用（目标态：${NEXT_ACTIVE_STATE[state as string] || 'ACTIVE'}）`;
      return (
        <Tooltip title={tip}>
          <Switch
            checkedChildren="启用"
            unCheckedChildren="停用"
            checked={enabled}
            disabled={!toggleable}
            onChange={() => handlers.onStatusToggle(nextActive, record)}
          />
        </Tooltip>
      );
    },
  },
  {
    title: '创建时间',
    dataIndex: 'createdAt',
    key: 'createdAt',
    width: 170,
    render: (time: string) => (time ? new Date(time).toLocaleString() : '-'),
  },
  {
    title: '操作',
    key: 'action',
    width: 240,
    fixed: 'right' as const,
    render: (_: unknown, record: System) => (
      <Space size={6} wrap>
        <Button size="small" icon={<EditOutlined />} onClick={() => handlers.onEdit(record)}>
          编辑
        </Button>
        <Button
          size="small"
          icon={<SettingOutlined />}
          onClick={() => handlers.onOpenDetail(record)}
        >
          仓库
        </Button>
        <Popconfirm
          title={`删除系统【${record.name}】？`}
          description="将级联软删除该系统下所有代码库，存在未完成任务时会拒绝。"
          okText="确认删除"
          cancelText="取消"
          okButtonProps={{ danger: true }}
          onConfirm={() => handlers.onDelete(record)}
        >
          <Button size="small" danger icon={<DeleteOutlined />}>
            删除
          </Button>
        </Popconfirm>
      </Space>
    ),
  },
];
