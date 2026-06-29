import { Button, Popconfirm, Space, Switch, Tag, Tooltip, Typography } from 'antd';
import type { TableProps } from 'antd';
import { CopyOutlined, PlayCircleOutlined } from '@ant-design/icons';
import type { Prompt } from '../../../types';
import { formatDateTime } from './utils';

const { Text } = Typography;

interface PromptColumnHandlers {
  onOpenTrial: (prompt: Prompt) => void;
  onEdit: (prompt: Prompt) => void;
  onClone: (prompt: Prompt) => void;
  onDelete: (id: number) => void;
  onStatusChange: (checked: boolean, prompt: Prompt) => void;
}

export const createPromptColumns = ({
  onOpenTrial,
  onEdit,
  onClone,
  onDelete,
  onStatusChange,
}: PromptColumnHandlers): TableProps<Prompt>['columns'] => [
  {
    title: '提示词',
    dataIndex: 'name',
    key: 'name',
    width: 250,
    render: (text: string, record) => (
      <div className="ci-prompt-name-cell">
        <Tooltip title={text} placement="topLeft">
          <Button type="link" className="ci-table-link ci-prompt-name-link" onClick={() => onOpenTrial(record)}>
            <span>{text}</span>
          </Button>
        </Tooltip>
        {record.isDefault === 1 && <Tag color="gold">默认</Tag>}
      </div>
    ),
  },
  {
    title: '版本',
    dataIndex: 'version',
    key: 'version',
    width: 72,
    render: (version: number) => <Tag color="purple">v{version}</Tag>,
  },
  {
    title: '启用',
    dataIndex: 'status',
    key: 'status',
    width: 82,
    render: (status: number, record) => (
      <Switch checkedChildren="开" unCheckedChildren="关" checked={status === 1} onChange={(checked) => onStatusChange(checked, record)} />
    ),
  },
  {
    title: '创建时间',
    dataIndex: 'createdAt',
    key: 'createdAt',
    width: 166,
    render: (time: string) => formatDateTime(time),
  },
  {
    title: '操作',
    key: 'action',
    width: 270,
    fixed: 'right',
    render: (_: unknown, record) => (
      <Space size={6} className="ci-prompt-row-actions">
        <Button size="small" onClick={() => onEdit(record)}>
          编辑
        </Button>
        <Button size="small" icon={<CopyOutlined />} onClick={() => onClone(record)}>
          复制
        </Button>
        <Button size="small" color="primary" variant="outlined" icon={<PlayCircleOutlined />} onClick={() => onOpenTrial(record)}>
          试跑
        </Button>
        <Popconfirm
          title="确定要删除该提示词模板吗？"
          description="删除后无法恢复！"
          onConfirm={() => onDelete(record.id)}
          okText="确定"
          cancelText="取消"
        >
          <Button size="small" danger>
            删除
          </Button>
        </Popconfirm>
      </Space>
    ),
  },
];

export const renderPromptExpandedRow = (record: Prompt) => (
  <div className="ci-prompt-expanded">
    <div className="ci-prompt-expanded-title">
      <Text type="secondary" strong>
        提示词正文模板：
      </Text>
    </div>
    <pre className="ci-prompt-expanded-content">{record.content}</pre>
  </div>
);
