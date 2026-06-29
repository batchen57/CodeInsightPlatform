import React, { useCallback, useState } from 'react';
import { Card, Space, Table, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import {
  changeSystemState,
  deleteSystem,
  updateSystem,
} from '../../api/system';
import { deleteRepository } from '../../api/repository';
import type { Repository, System, SystemState } from '../../types';
import { getSystemColumns } from './columns';
import SystemFilterBar from './SystemFilterBar';
import SystemWizardModal from './SystemWizardModal';
import SystemStatusTag from '../../components/SystemStatusTag';
import RepositoryDrawer from './RepositoryDrawer';
import { useRepositories, useSystemsList } from './hooks';

/**
 * 系统与代码库管理主页面（4 步向导重构版）
 *
 *  关注点拆分：
 *   - 数据获取   → hooks.ts (useSystemsList / useRepositories)
 *   - 视觉组件   → SystemFilterBar / SystemWizardModal / RepositoryDrawer / SystemStatusTag
 *   - 列定义     → columns.tsx
 *   - 本文件     → 编排：handlers + 弹窗状态 + 渲染
 */
const Systems: React.FC = () => {
  const navigate = useNavigate();

  // ===== 数据 =====
  const list = useSystemsList();
  const { systems, total, loading, current, size, setCurrent, setSize } = list;

  // ===== Wizard（新增系统）=====
  const [wizardOpen, setWizardOpen] = useState(false);
  const openWizard = useCallback(() => setWizardOpen(true), []);

  // ===== 编辑基本信息（无向导，仅改 name/nameCn/owner/description）=====
  const handleEdit = useCallback(
    async (record: System) => {
      const name = window.prompt('修改系统名称', record.name);
      if (name && name !== record.name) {
        await updateSystem(record.id, { name });
        message.success('已更新');
        list.fetch();
      }
    },
    [list],
  );

  // ===== 启停切换 =====
  const handleStatusToggle = useCallback(
    async (nextActive: boolean, record: System) => {
      try {
        const target: SystemState = nextActive ? 'ACTIVE' : 'DISABLED';
        await changeSystemState(record.id, target);
        message.success(`${record.name} 已${nextActive ? '启用' : '停用'}`);
        list.fetch();
      } catch (err) {
        // 拦截器已提示
        console.error(err);
      }
    },
    [list],
  );

  // ===== 删除系统 =====
  const handleDeleteSystem = useCallback(
    async (record: System) => {
      try {
        await deleteSystem(record.id);
        message.success(`系统【${record.name}】已删除（含其下代码库）`);
        list.fetch();
      } catch (err) {
        console.error(err);
      }
    },
    [list],
  );

  // ===== Drawer（开/关 + 选中系统）=====
  const [selectedSystem, setSelectedSystem] = useState<System | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const repoHook = useRepositories(drawerOpen ? selectedSystem?.id ?? null : null);

  const openDetailDrawer = useCallback((system: System) => {
    setSelectedSystem(system);
    setDrawerOpen(true);
  }, []);

  // ===== 仓库删除 =====
  const handleDeleteRepository = useCallback(
    async (repo: Repository) => {
      try {
        await deleteRepository(repo.id);
        message.success('代码库已删除');
        if (selectedSystem?.id) {
          repoHook.refresh(selectedSystem.id);
        }
        list.fetch();
      } catch (err) {
        console.error(err);
      }
    },
    [list, repoHook, selectedSystem],
  );

  const handleScan = useCallback(
    (repo: Repository) => {
      navigate(`/tasks/dispatch?systemId=${repo.systemId}&repositoryId=${repo.id}`);
    },
    [navigate],
  );

  // ===== 列配置 =====
  const systemColumns = getSystemColumns({
    onEdit: handleEdit,
    onOpenDetail: openDetailDrawer,
    onDelete: handleDeleteSystem,
    onStatusToggle: handleStatusToggle,
  });

  return (
    <div className="ci-page ci-systems-page">
      <Card className="ci-filter-card">
        <SystemFilterBar
          searchName={list.searchName}
          searchOwner={list.searchOwner}
          searchStatus={list.searchStatus}
          onSearchNameChange={list.setSearchName}
          onSearchOwnerChange={list.setSearchOwner}
          onSearchStatusChange={list.setSearchStatus}
          onSearch={list.handleSearch}
          onReset={list.handleReset}
          onAdd={openWizard}
        />
      </Card>

      <Card
        className="ci-systems-table-card"
        title={
          <Space>
            <span>系统列表</span>
            <SystemStatusTag state="DRAFT" />
            <SystemStatusTag state="REPO_CONFIGURED" />
            <SystemStatusTag state="SCAN_CONFIGURED" />
            <SystemStatusTag state="PROMPT_CONFIGURED" />
            <SystemStatusTag state="ACTIVE" />
            <SystemStatusTag state="DISABLED" />
          </Space>
        }
      >
        <Table<System>
          dataSource={systems}
          columns={systemColumns}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1500 }}
          pagination={{
            current,
            pageSize: size,
            total,
            showSizeChanger: true,
            onChange: (page, pageSize) => {
              setCurrent(page);
              setSize(pageSize);
            },
          }}
        />
      </Card>

      <SystemWizardModal
        open={wizardOpen}
        onClose={() => setWizardOpen(false)}
        onCompleted={() => {
          setWizardOpen(false);
          list.fetch();
        }}
      />

      <RepositoryDrawer
        open={drawerOpen}
        system={selectedSystem}
        repositories={repoHook.repositories}
        loading={repoHook.loading}
        onClose={() => setDrawerOpen(false)}
        onAddRepo={() => {
          // 直接用 wizard 的 Step 2 入口；为简化这里直接打开 drawer + 用户自己点添加
        }}
        onEditRepo={() => {
          // 编辑入口已删除（向导内统一配置）
        }}
        onDeleteRepo={handleDeleteRepository}
        onScan={handleScan}
      />
    </div>
  );
};

export default Systems;
