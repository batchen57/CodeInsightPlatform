import React, { useCallback, useState } from 'react';
import { Card, Form, message, Table } from 'antd';
import type { FormInstance } from 'antd';
import { useNavigate } from 'react-router-dom';
import {
  changeSystemStatus,
  createSystem,
  deleteSystem,
  updateSystem,
} from '../../api/system';
import {
  createRepository,
  deleteRepository,
  testRepositoryConnection,
  updateRepository,
} from '../../api/repository';
import type { Repository, System } from '../../types';
import { getSystemColumns } from './columns';
import SystemFilterBar from './SystemFilterBar';
import SystemFormModal, { type SystemFormValues } from './SystemFormModal';
import RepositoryDrawer from './RepositoryDrawer';
import RepositoryFormModal, { type RepositoryFormValues } from './RepositoryFormModal';
import { useRepositories, useSystemsList } from './hooks';

/**
 * 系统与代码库管理主页面
 *
 * 关注点拆分：
 *  - 数据获取  → hooks.ts（useSystemsList / useRepositories）
 *  - 视觉组件  → SystemFilterBar / *Modal / Drawer / Status
 *  - 列定义    → columns.tsx（系统表）/ drawerColumns.tsx（仓库表）
 *  - 本文件    → 仅编排：handlers + 弹窗状态 + 渲染
 */
const Systems: React.FC = () => {
  const navigate = useNavigate();

  // ===== 数据（已抽到 hooks）=====
  const list = useSystemsList();
  const { systems, total, loading, current, size, setCurrent, setSize } = list;

  // ===== 系统表单 =====
  const [systemModalOpen, setSystemModalOpen] = useState(false);
  const [editingSystem, setEditingSystem] = useState<System | null>(null);
  const [systemForm] = Form.useForm<SystemFormValues>();

  // ===== Drawer（开/关 + 选中系统）=====
  const [selectedSystem, setSelectedSystem] = useState<System | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const repoHook = useRepositories(drawerOpen ? selectedSystem?.id ?? null : null);

  // ===== 代码库表单 =====
  const [repoModalOpen, setRepoModalOpen] = useState(false);
  const [editingRepo, setEditingRepo] = useState<Repository | null>(null);
  const [testingConnection, setTestingConnection] = useState(false);
  const [repoForm] = Form.useForm<RepositoryFormValues>();

  // ===== 系统操作 =====
  const openSystemModal = useCallback(
    (system: System | null = null) => {
      setEditingSystem(system);
      if (system) {
        systemForm.setFieldsValue({
          name: system.name,
          owner: system.owner,
          description: system.description,
        });
      } else {
        systemForm.resetFields();
      }
      setSystemModalOpen(true);
    },
    [systemForm],
  );

  const handleSystemSubmit = useCallback(async () => {
    const values = await systemForm.validateFields();
    if (editingSystem) {
      await updateSystem(editingSystem.id, values);
      message.success('系统已更新');
    } else {
      await createSystem(values);
      message.success('系统已创建');
    }
    setSystemModalOpen(false);
    list.fetch();
  }, [editingSystem, systemForm, list]);

  const handleStatusChange = useCallback(
    async (checked: boolean, record: System) => {
      const status = checked ? 1 : 0;
      await changeSystemStatus(record.id, status);
      message.success(`${record.name} 已${checked ? '启用' : '停用'}`);
      list.fetch();
      if (selectedSystem?.id === record.id) {
        setSelectedSystem({ ...selectedSystem, status });
      }
    },
    [list, selectedSystem],
  );

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

  // ===== Drawer 操作 =====
  const openDetailDrawer = useCallback(
    (system: System) => {
      setSelectedSystem(system);
      setDrawerOpen(true);
    },
    [],
  );

  // ===== 代码库操作 =====
  const openRepoModal = useCallback(
    (repo: Repository | null = null) => {
      setEditingRepo(repo);
      if (repo) {
        repoForm.setFieldsValue(repo);
      } else {
        repoForm.resetFields();
        repoForm.setFieldsValue({ branch: 'main', scanRoot: '/' });
      }
      setRepoModalOpen(true);
    },
    [repoForm],
  );

  const handleTestConnection = useCallback(async () => {
    const values = await repoForm.validateFields([
      'gitUrl',
      'branch',
      'username',
      'password',
    ]);
    setTestingConnection(true);
    try {
      const success = await testRepositoryConnection({ ...values, id: editingRepo?.id });
      if (success) message.success('Git 连接测试成功');
      else message.error('Git 连接测试失败');
    } finally {
      setTestingConnection(false);
    }
  }, [repoForm, editingRepo]);

  const handleRepoSubmit = useCallback(async () => {
    if (!selectedSystem) return;
    const values = await repoForm.validateFields();
    const payload = { ...values, systemId: selectedSystem.id };
    if (editingRepo) {
      await updateRepository(editingRepo.id, payload);
      message.success('代码库已更新');
    } else {
      await createRepository(payload);
      message.success('代码库已添加');
    }
    setRepoModalOpen(false);
    if (selectedSystem.id) {
      repoHook.refresh(selectedSystem.id);
    }
    list.fetch();
  }, [editingRepo, repoForm, selectedSystem, repoHook, list]);

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
      navigate(`/tasks?systemId=${repo.systemId}&repositoryId=${repo.id}&openCreate=1`);
    },
    [navigate],
  );

  // ===== 列配置 =====
  const systemColumns = getSystemColumns({
    onEdit: openSystemModal,
    onOpenDetail: openDetailDrawer,
    onDelete: handleDeleteSystem,
    onStatusChange: handleStatusChange,
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
          onAdd={() => openSystemModal()}
        />
      </Card>

      <Card className="ci-systems-table-card">
        <Table<System>
          dataSource={systems}
          columns={systemColumns}
          rowKey="id"
          loading={loading}
          scroll={{ x: 1180 }}
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

      <SystemFormModal
        open={systemModalOpen}
        editing={editingSystem}
        form={systemForm as FormInstance<SystemFormValues>}
        onCancel={() => setSystemModalOpen(false)}
        onSubmit={handleSystemSubmit}
      />

      <RepositoryDrawer
        open={drawerOpen}
        system={selectedSystem}
        repositories={repoHook.repositories}
        loading={repoHook.loading}
        onClose={() => setDrawerOpen(false)}
        onAddRepo={() => openRepoModal()}
        onEditRepo={openRepoModal}
        onDeleteRepo={handleDeleteRepository}
        onScan={handleScan}
      />

      <RepositoryFormModal
        open={repoModalOpen}
        editing={!!editingRepo}
        testing={testingConnection}
        form={repoForm as FormInstance<RepositoryFormValues>}
        onCancel={() => setRepoModalOpen(false)}
        onSubmit={handleRepoSubmit}
        onTest={handleTestConnection}
      />
    </div>
  );
};

export default Systems;
