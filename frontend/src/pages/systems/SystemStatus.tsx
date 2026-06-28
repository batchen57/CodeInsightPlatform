import React from 'react';
import { Tag } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';

interface Props {
  status: number;
}

/** 启用 / 停用状态徽标（Descriptions 与表格里都用得上） */
const SystemStatus: React.FC<Props> = ({ status }) => {
  if (status === 1) {
    return (
      <Tag color="success" icon={<CheckCircleOutlined />}>
        已启用
      </Tag>
    );
  }
  return (
    <Tag color="error" icon={<CloseCircleOutlined />}>
      已停用
    </Tag>
  );
};

export default SystemStatus;
