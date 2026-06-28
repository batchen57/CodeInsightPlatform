import React from 'react';
import { Button, Col, Input, Row, Select, Space } from 'antd';
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';

interface Props {
  searchName: string;
  searchOwner: string;
  searchStatus: number | undefined;
  onSearchNameChange: (v: string) => void;
  onSearchOwnerChange: (v: string) => void;
  onSearchStatusChange: (v: number | undefined) => void;
  onSearch: () => void;
  onReset: () => void;
  onAdd: () => void;
}

/**
 * 系统管理 - 筛选条
 * 纯受控组件，所有状态由父组件管理
 */
const SystemFilterBar: React.FC<Props> = ({
  searchName,
  searchOwner,
  searchStatus,
  onSearchNameChange,
  onSearchOwnerChange,
  onSearchStatusChange,
  onSearch,
  onReset,
  onAdd,
}) => (
  <Row gutter={[12, 12]} align="middle">
    <Col xs={24} md={6}>
      <Input
        placeholder="搜索系统"
        value={searchName}
        onChange={(e) => onSearchNameChange(e.target.value)}
        onPressEnter={onSearch}
        prefix={<SearchOutlined />}
      />
    </Col>
    <Col xs={24} md={6}>
      <Input
        placeholder="筛选负责人"
        value={searchOwner}
        onChange={(e) => onSearchOwnerChange(e.target.value)}
        onPressEnter={onSearch}
      />
    </Col>
    <Col xs={24} md={5}>
      <Select
        placeholder="状态"
        style={{ width: '100%' }}
        allowClear
        value={searchStatus}
        onChange={onSearchStatusChange}
        options={[
          { value: 1, label: '启用' },
          { value: 0, label: '停用' },
        ]}
      />
    </Col>
    <Col xs={24} md={7}>
      <Space className="ci-toolbar-actions" wrap>
        <Button type="primary" icon={<SearchOutlined />} onClick={onSearch}>
          查询
        </Button>
        <Button icon={<ReloadOutlined />} onClick={onReset}>
          重置
        </Button>
        <Button type="primary" icon={<PlusOutlined />} onClick={onAdd}>
          新增系统
        </Button>
      </Space>
    </Col>
  </Row>
);

export default SystemFilterBar;
