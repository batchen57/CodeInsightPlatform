import React from 'react';
import { Tag, Tooltip } from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  ExperimentOutlined,
  FileSearchOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import type { SystemState } from '../types';

/**
 * 6 态系统状态 Tag
 *  - DRAFT / REPO_CONFIGURED / SCAN_CONFIGURED / PROMPT_CONFIGURED / ACTIVE / DISABLED
 */
const META: Record<SystemState, { color: string; label: string; icon: React.ReactNode; tip: string }> = {
  DRAFT: {
    color: 'default',
    label: '草稿',
    icon: <FileSearchOutlined />,
    tip: '基本信息已填，待配置仓库',
  },
  REPO_CONFIGURED: {
    color: 'cyan',
    label: '已配仓库',
    icon: <ExperimentOutlined />,
    tip: '至少 1 个仓库已添加，待配置入口扫描',
  },
  SCAN_CONFIGURED: {
    color: 'geekblue',
    label: '已配扫描',
    icon: <ClockCircleOutlined />,
    tip: '入口扫描已配置，待配置提示词',
  },
  PROMPT_CONFIGURED: {
    color: 'blue',
    label: '已配提示词',
    icon: <CheckCircleOutlined />,
    tip: '提示词已绑定，可点击「启用」激活系统',
  },
  ACTIVE: {
    color: 'success',
    label: '已启用',
    icon: <ThunderboltOutlined />,
    tip: '系统已启用，可创建知识构建任务',
  },
  DISABLED: {
    color: 'error',
    label: '已停用',
    icon: <CloseCircleOutlined />,
    tip: '系统已停用，不可创建新任务',
  },
};

interface Props {
  state?: SystemState | null;
}

const SystemStatusTag: React.FC<Props> = ({ state }) => {
  const meta = state ? META[state] : META.DRAFT;
  return (
    <Tooltip title={meta.tip}>
      <Tag color={meta.color} icon={meta.icon}>
        {meta.label}
      </Tag>
    </Tooltip>
  );
};

export default SystemStatusTag;
