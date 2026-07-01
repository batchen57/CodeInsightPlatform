import React, { useCallback, useEffect, useState } from 'react';
import { Card, Form, Space, Table, message } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  changeSystemState,
  deleteSystem,
  updateSystem,
} from '../../api/system';
import {
  createRepository,
  deleteRepository,
  testRepositoryConnection,
  updateRepository,
} from '../../api/repository';
import type { Repository, System, SystemState } from '../../types';
import { getSystemColumns } from './columns';
import SystemFilterBar from './SystemFilterBar';
import SystemFormModal, { type SystemFormValues } from './SystemFormModal';
import SystemWizardModal from './SystemWizardModal';
import SystemPromptBindModal from './SystemPromptBindModal';
import SystemStatusTag from '../../components/SystemStatusTag';
import RepositoryDrawer from './RepositoryDrawer';
import RepositoryFormModal, { type RepositoryFormValues } from './RepositoryFormModal';
import RepositoryScanConfigModal, { type ScanConfigFormValues } from './RepositoryScanConfigModal';
import { parseRepoEntryScanConfig } from './repositoryUtils';
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
  const [searchParams, setSearchParams] = useSearchParams();

  // ===== 数据 =====
  const list = useSystemsList();
  const { systems, total, loading, current, size, setCurrent, setSize } = list;

  // ===== Wizard（新增系统）=====
  const [wizardOpen, setWizardOpen] = useState(false);
  const openWizard = useCallback(() => setWizardOpen(true), []);

  // ===== 编辑基本信息（name / nameCn / owner / description）=====
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingSystem, setEditingSystem] = useState<System | null>(null);
  const [editSubmitting, setEditSubmitting] = useState(false);
  const [editForm] = Form.useForm<SystemFormValues>();

  const handleEdit = useCallback(
    (record: System) => {
      setEditingSystem(record);
      editForm.setFieldsValue({
        name: record.name,
        nameCn: record.nameCn,
        owner: record.owner,
        description: record.description,
      });
      setEditModalOpen(true);
    },
    [editForm],
  );

  const handleEditSubmit = useCallback(async () => {
    if (!editingSystem) return;
    try {
      const values = await editForm.validateFields();
      setEditSubmitting(true);
      await updateSystem(editingSystem.id, values);
      message.success('系统信息已更新');
      setEditModalOpen(false);
      setEditingSystem(null);
      list.fetch();
    } catch (err) {
      if (err && typeof err === 'object' && 'errorFields' in err) return;
      console.error(err);
    } finally {
      setEditSubmitting(false);
    }
  }, [editForm, editingSystem, list]);

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
  const [selectedRepo, setSelectedRepo] = useState<Repository | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const repoHook = useRepositories(drawerOpen ? selectedSystem?.id ?? null : null);

  const openDetailDrawer = useCallback((system: System) => {
    setSelectedSystem(system);
    setDrawerOpen(true);
  }, []);

  // ===== 提示词聚焦编辑弹窗 =====
  const [promptBindOpen, setPromptBindOpen] = useState(false);
  const openPromptBind = useCallback((system: System) => {
    setSelectedSystem(system);
    setPromptBindOpen(true);
  }, []);

  // 从任务下发等页面深链打开提示词绑定弹窗：/systems?systemId=1&action=prompts
  useEffect(() => {
    const action = searchParams.get('action');
    const sysId = Number(searchParams.get('systemId'));
    if (action !== 'prompts' || !Number.isFinite(sysId) || sysId <= 0 || systems.length === 0) {
      return;
    }
    const system = systems.find((s) => s.id === sysId);
    if (!system) return;
    openPromptBind(system);
    const next = new URLSearchParams(searchParams);
    next.delete('systemId');
    next.delete('action');
    setSearchParams(next, { replace: true });
  }, [openPromptBind, searchParams, setSearchParams, systems]);

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

  // ===== 代码库添加 / 编辑 =====
  const [repoModalOpen, setRepoModalOpen] = useState(false);
  const [editingRepo, setEditingRepo] = useState<Repository | null>(null);
  const [repoTesting, setRepoTesting] = useState(false);
  const [repoSubmitting, setRepoSubmitting] = useState(false);
  const [repoForm] = Form.useForm<RepositoryFormValues>();

  const openAddRepo = useCallback(() => {
    if (!selectedSystem) return;
    setEditingRepo(null);
    repoForm.resetFields();
    repoForm.setFieldsValue({ branch: 'main', scanRoot: '/' });
    setRepoModalOpen(true);
  }, [repoForm, selectedSystem]);

  const openEditRepo = useCallback(
    (repo: Repository) => {
      setEditingRepo(repo);
      repoForm.setFieldsValue({
        gitUrl: repo.gitUrl,
        branch: repo.branch,
        scanRoot: repo.scanRoot,
        username: repo.username,
        password: repo.password,
        excludeDirs: repo.excludeDirs,
        excludeFileTypes: repo.excludeFileTypes,
      });
      setRepoModalOpen(true);
    },
    [repoForm],
  );

  const handleRepoSubmit = useCallback(async () => {
    if (!selectedSystem) return;
    try {
      const values = await repoForm.validateFields();
      setRepoSubmitting(true);
      if (editingRepo) {
        await updateRepository(editingRepo.id, { id: editingRepo.id, ...values });
        message.success('代码库已更新');
      } else {
        await createRepository({ systemId: selectedSystem.id, ...values });
        message.success('代码库已添加');
      }
      setRepoModalOpen(false);
      setEditingRepo(null);
      repoHook.refresh(selectedSystem.id);
      list.fetch();
    } catch (err) {
      if (err && typeof err === 'object' && 'errorFields' in err) return;
      console.error(err);
    } finally {
      setRepoSubmitting(false);
    }
  }, [editingRepo, list, repoForm, repoHook, selectedSystem]);

  const handleRepoTest = useCallback(async () => {
    try {
      const values = await repoForm.validateFields(['gitUrl', 'branch', 'username', 'password']);
      setRepoTesting(true);
      const payload = editingRepo ? { id: editingRepo.id, ...values } : values;
      const ok = await testRepositoryConnection(payload);
      if (ok) message.success('Git 连接测试成功');
      else message.error('Git 连接测试失败');
    } catch (err) {
      if (err && typeof err === 'object' && 'errorFields' in err) return;
      console.error(err);
    } finally {
      setRepoTesting(false);
    }
  }, [editingRepo, repoForm]);

  // ===== 代码库入口扫描规则 =====
  const [scanModalOpen, setScanModalOpen] = useState(false);
  const [scanConfigRepo, setScanConfigRepo] = useState<Repository | null>(null);
  const [scanSubmitting, setScanSubmitting] = useState(false);
  const [scanForm] = Form.useForm<ScanConfigFormValues>();

  const openScanConfig = useCallback(
    (repo: Repository) => {
      setScanConfigRepo(repo);
      scanForm.setFieldsValue({ entryScanConfig: parseRepoEntryScanConfig(repo) });
      setScanModalOpen(true);
    },
    [scanForm],
  );

  const handleScanConfigSubmit = useCallback(async () => {
    if (!scanConfigRepo || !selectedSystem) return;
    try {
      const values = await scanForm.validateFields();
      setScanSubmitting(true);
      await updateRepository(scanConfigRepo.id, {
        id: scanConfigRepo.id,
        entryScanConfig: values.entryScanConfig
          ? JSON.stringify(values.entryScanConfig)
          : null,
      } as Partial<Repository>);
      message.success('入口扫描规则已保存');
      setScanModalOpen(false);
      setScanConfigRepo(null);
      repoHook.refresh(selectedSystem.id);
      list.fetch();
    } catch (err) {
      if (err && typeof err === 'object' && 'errorFields' in err) return;
      console.error(err);
    } finally {
      setScanSubmitting(false);
    }
  }, [list, repoHook, scanConfigRepo, scanForm, selectedSystem]);

  // ===== 列配置 =====
  const systemColumns = getSystemColumns({
    onEdit: handleEdit,
    onEditPrompts: openPromptBind,
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

      <SystemFormModal
        open={editModalOpen}
        form={editForm}
        submitting={editSubmitting}
        onCancel={() => {
          setEditModalOpen(false);
          setEditingSystem(null);
        }}
        onSubmit={handleEditSubmit}
      />

      <RepositoryDrawer
        open={drawerOpen}
        system={selectedSystem}
        repositories={repoHook.repositories}
        loading={repoHook.loading}
        onClose={() => setDrawerOpen(false)}
        onAddRepo={openAddRepo}
        onEditRepo={openEditRepo}
        onBindPrompts={(repo) => { setSelectedRepo(repo); setPromptBindOpen(true); }}
        onDeleteRepo={handleDeleteRepository}
        onScan={handleScan}
        onScanConfig={openScanConfig}
      />

      <SystemPromptBindModal
        open={promptBindOpen}
        system={selectedSystem}
        onClose={() => {
          setPromptBindOpen(false);
          setSelectedSystem(null);
        }}
        onSaved={() => {
          list.fetch();
        }}
      />

      <RepositoryFormModal
        open={repoModalOpen}
        editing={!!editingRepo}
        testing={repoTesting}
        submitting={repoSubmitting}
        form={repoForm}
        onCancel={() => {
          setRepoModalOpen(false);
          setEditingRepo(null);
        }}
        onSubmit={handleRepoSubmit}
        onTest={handleRepoTest}
      />

      <RepositoryScanConfigModal
        open={scanModalOpen}
        form={scanForm}
        repoId={scanConfigRepo?.id}
        systemId={selectedSystem?.id}
        submitting={scanSubmitting}
        onCancel={() => {
          setScanModalOpen(false);
          setScanConfigRepo(null);
        }}
        onSubmit={handleScanConfigSubmit}
      />
    </div>
  );
};

export default Systems;
