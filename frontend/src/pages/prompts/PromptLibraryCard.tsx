import { Button, Card, Input, Table, Tabs } from 'antd';
import type { TableProps } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import type React from 'react';
import type { Prompt } from '../../types';
import type { PromptType } from './constants';
import { PROMPT_TYPE_TABS } from './constants';
import { renderPromptExpandedRow } from './columns';

interface PromptLibraryCardProps {
  activePromptType: PromptType;
  searchName: string;
  prompts: Prompt[];
  columns: TableProps<Prompt>['columns'];
  loading: boolean;
  current: number;
  size: number;
  total: number;
  selectedPromptId?: number;
  onPromptTypeChange: (key: string) => void;
  onSearchNameChange: (value: string) => void;
  onSearch: () => void;
  onReset: () => void;
  onCreate: () => void;
  onPageChange: (page: number, pageSize: number) => void;
}

const PromptLibraryCard: React.FC<PromptLibraryCardProps> = ({
  activePromptType,
  searchName,
  prompts,
  columns,
  loading,
  current,
  size,
  total,
  selectedPromptId,
  onPromptTypeChange,
  onSearchNameChange,
  onSearch,
  onReset,
  onCreate,
  onPageChange,
}) => (
  <Card
    className="ci-workspace-card ci-prompt-library"
    title="提示词模板"
    extra={
      <Button type="primary" icon={<PlusOutlined />} onClick={onCreate}>
        新增提示词
      </Button>
    }
  >
    <Tabs
      activeKey={activePromptType}
      onChange={onPromptTypeChange}
      items={PROMPT_TYPE_TABS.map((item) => ({
        key: item.key,
        label: item.label,
      }))}
    />
    <div className="ci-card-toolbar">
      <Input
        placeholder="搜索提示词"
        value={searchName}
        onChange={(event) => onSearchNameChange(event.target.value)}
        onPressEnter={onSearch}
        prefix={<SearchOutlined />}
      />
      <Button type="primary" onClick={onSearch}>
        查询
      </Button>
      <Button onClick={onReset}>重置</Button>
    </div>
    <Table
      dataSource={prompts}
      columns={columns}
      rowKey="id"
      loading={loading}
      tableLayout="fixed"
      scroll={{ x: 872 }}
      rowClassName={(record) => (selectedPromptId === record.id ? 'ci-selected-row' : '')}
      expandable={{
        expandedRowRender: renderPromptExpandedRow,
        rowExpandable: (record) => !!record.content,
      }}
      pagination={{
        current,
        pageSize: size,
        total,
        onChange: onPageChange,
      }}
    />
  </Card>
);

export default PromptLibraryCard;
