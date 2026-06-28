import React from 'react';
import { Button, Drawer, Popconfirm, Space, Switch, Table, Tag, Typography } from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import type { AiModelPreset } from '../../types';

const { Text } = Typography;

const capabilityMap: Record<string, { label: string; color: string }> = {
  text: { label: '文本', color: 'blue' },
  image: { label: '图片', color: 'green' },
  video: { label: '视频', color: 'purple' },
};

interface Props {
  open: boolean;
  presets: AiModelPreset[];
  loading: boolean;
  onClose: () => void;
  onCreate: () => void;
  onEdit: (preset: AiModelPreset) => void;
  onDelete: (id: number) => void;
  onToggleStatus: (preset: AiModelPreset, checked: boolean) => void;
}

const ModelPresetManagerDrawer: React.FC<Props> = ({
  open,
  presets,
  loading,
  onClose,
  onCreate,
  onEdit,
  onDelete,
  onToggleStatus,
}) => {
  const columns = [
    {
      title: '预设名称',
      key: 'name',
      width: 220,
      render: (_: unknown, record: AiModelPreset) => (
        <Space direction="vertical" size={2}>
          <Text strong>{record.name}</Text>
          <Text type="secondary" code style={{ fontSize: 12 }}>
            {record.identifier}
          </Text>
        </Space>
      ),
    },
    {
      title: '供应商',
      dataIndex: 'provider',
      key: 'provider',
      width: 120,
      render: (provider: string) => <Tag color="blue">{provider}</Tag>,
    },
    {
      title: 'Endpoint',
      dataIndex: 'baseUrl',
      key: 'baseUrl',
      ellipsis: true,
      render: (baseUrl?: string) =>
        baseUrl ? (
          <Text type="secondary" copyable style={{ fontSize: 12 }}>
            {baseUrl}
          </Text>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
    {
      title: '能力',
      dataIndex: 'capabilities',
      key: 'capabilities',
      width: 150,
      render: (capabilities?: string) =>
        capabilities ? (
          <Space size={4} wrap>
            {capabilities.split(',').map((capability) => {
              const item = capabilityMap[capability] ?? {
                label: capability,
                color: 'default',
              };
              return (
                <Tag key={capability} color={item.color}>
                  {item.label}
                </Tag>
              );
            })}
          </Space>
        ) : (
          <Text type="secondary">—</Text>
        ),
    },
    {
      title: '启用',
      key: 'status',
      width: 92,
      render: (_: unknown, record: AiModelPreset) => {
        const checked = (record.status ?? 1) === 1;
        return (
          <Switch
            size="small"
            checked={checked}
            checkedChildren="启"
            unCheckedChildren="停"
            onChange={(nextChecked) => onToggleStatus(record, nextChecked)}
          />
        );
      },
    },
    {
      title: '排序',
      dataIndex: 'sortOrder',
      key: 'sortOrder',
      width: 80,
      render: (sortOrder?: number) => <Text>{sortOrder ?? 0}</Text>,
    },
    {
      title: '操作',
      key: 'action',
      width: 120,
      fixed: 'right' as const,
      render: (_: unknown, record: AiModelPreset) => (
        <Space size={4}>
          <Button
            type="text"
            size="small"
            icon={<EditOutlined />}
            onClick={() => onEdit(record)}
          />
          <Popconfirm
            title="确认删除该预设模板吗？"
            okText="删除"
            cancelText="取消"
            onConfirm={() => onDelete(record.id)}
          >
            <Button type="text" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Drawer
      title="模型预设管理"
      open={open}
      onClose={onClose}
      width={920}
      destroyOnHidden
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={onCreate}>
          新增预设
        </Button>
      }
    >
      <Table<AiModelPreset>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={presets}
        size="middle"
        scroll={{ x: 860 }}
        pagination={false}
      />
    </Drawer>
  );
};

export default ModelPresetManagerDrawer;
