import React, { useCallback, useState } from 'react';
import {
  Button,
  Card,
  Collapse,
  Drawer,
  Form,
  Select,
  Space,
  Table,
  Typography,
  message,
} from 'antd';
import type { FormInstance } from 'antd';
import { ThunderboltOutlined } from '@ant-design/icons';
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

  // ===== 仓库扫描规则 Drawer =====
  const [scanConfigDrawerOpen, setScanConfigDrawerOpen] = useState(false);
  const [scanConfigRepo, setScanConfigRepo] = useState<Repository | null>(null);
  const [scanConfigForm] = Form.useForm();
  const [savingScanConfig, setSavingScanConfig] = useState(false);

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

  const openScanConfigDrawer = useCallback(
    (repo: Repository) => {
      setScanConfigRepo(repo);
      let config = repo.entryScanConfig;
      if (typeof config === 'string') {
        try {
          config = JSON.parse(config);
        } catch {
          config = {};
        }
      }
      const base = (config || {}) as Record<string, unknown>;
      const merged = {
        ...base,
        excludeClasspaths:
          Array.isArray(base.excludeClasspaths) && (base.excludeClasspaths as string[]).length > 0
            ? base.excludeClasspaths
            : ['**/*Test', '**/*Tests', '**/*TestCase'],
      };
      scanConfigForm.setFieldsValue({ entryScanConfig: merged });
      setScanConfigDrawerOpen(true);
    },
    [scanConfigForm],
  );

  const handleSaveScanConfig = useCallback(async () => {
    if (!scanConfigRepo) return;
    const values = await scanConfigForm.validateFields();
    setSavingScanConfig(true);
    try {
      const merged = {
        ...scanConfigRepo,
        ...values,
        entryScanConfig: values.entryScanConfig ? JSON.stringify(values.entryScanConfig) : null,
      };
      await updateRepository(scanConfigRepo.id, merged);
      message.success('仓库扫描规则已保存');
      setScanConfigDrawerOpen(false);
      if (selectedSystem?.id) {
        repoHook.refresh(selectedSystem.id);
      }
    } finally {
      setSavingScanConfig(false);
    }
  }, [scanConfigForm, scanConfigRepo, selectedSystem, repoHook]);

  const handleFillDefaultScanConfig = useCallback(() => {
    scanConfigForm.setFieldsValue({
      entryScanConfig: {
        includeAnnotations: ['RestController', 'Controller', 'RequestMapping'],
        includeClasspaths: [],
        includeExtends: [],
        excludeClasspaths: ['**/*Test', '**/*Tests', '**/*TestCase'],
        excludePackages: [],
        excludeAnnotations: ['Deprecated'],
      },
    });
    message.success('已填入默认扫描配置，请按需调整后保存');
  }, [scanConfigForm]);

  const watchedScanConfig = Form.useWatch('entryScanConfig', scanConfigForm);
  const hasAnyInclude = (() => {
    if (!watchedScanConfig) return false;
    const cfg = watchedScanConfig as {
      includeAnnotations?: string[];
      includeClasspaths?: string[];
      includeExtends?: string[];
    };
    const ann = Array.isArray(cfg.includeAnnotations) ? cfg.includeAnnotations.length : 0;
    const cp = Array.isArray(cfg.includeClasspaths) ? cfg.includeClasspaths.length : 0;
    const ext = Array.isArray(cfg.includeExtends) ? cfg.includeExtends.length : 0;
    return ann + cp + ext > 0;
  })();

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
        onScanConfig={openScanConfigDrawer}
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

      <Drawer
        title={scanConfigRepo ? `扫描规则：${scanConfigRepo.gitUrl}` : '扫描规则'}
        width={680}
        open={scanConfigDrawerOpen}
        onClose={() => setScanConfigDrawerOpen(false)}
        destroyOnHidden
        footer={
          <Space>
            <Button onClick={() => setScanConfigDrawerOpen(false)}>取消</Button>
            <Button
              type="primary"
              loading={savingScanConfig}
              onClick={handleSaveScanConfig}
              disabled={!hasAnyInclude}
            >
              保存
            </Button>
          </Space>
        }
      >
        <Form
          form={scanConfigForm}
          layout="vertical"
          style={{ marginTop: 16 }}
          initialValues={{
            entryScanConfig: {
              excludeClasspaths: ['**/*Test', '**/*Tests', '**/*TestCase'],
            },
          }}
        >
          <div
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              marginBottom: 12,
              gap: 12,
            }}
          >
            <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
              该配置作为新建反编译任务时的默认值；任务可单独配置覆盖此默认值。
              至少需要 1 个入口识别规则。
            </Typography.Paragraph>
            <Button icon={<ThunderboltOutlined />} onClick={handleFillDefaultScanConfig}>
              填入默认扫描配置
            </Button>
          </div>
          <Collapse
            defaultActiveKey={['include', 'exclude']}
            items={[
              {
                key: 'include',
                label: '入口识别规则（满足任一即视为入口）',
                children: (
                  <>
                    <Form.Item name={['entryScanConfig', 'includeAnnotations']} noStyle>
                      <Select
                        mode="tags"
                        placeholder="注解（如 RestController / Service / 自定义注解）"
                        style={{ width: '100%' }}
                      />
                    </Form.Item>
                    <Form.Item name={['entryScanConfig', 'includeClasspaths']} noStyle style={{ marginTop: 8 }}>
                      <Select
                        mode="tags"
                        placeholder="类路径 Ant 模式（如 com.demo.controller.*）"
                        style={{ width: '100%' }}
                      />
                    </Form.Item>
                    <Form.Item name={['entryScanConfig', 'includeExtends']} noStyle style={{ marginTop: 8 }}>
                      <Select
                        mode="tags"
                        placeholder="继承/实现父类（如 BaseEntry / CommandLineRunner）"
                        style={{ width: '100%' }}
                      />
                    </Form.Item>
                  </>
                ),
              },
              {
                key: 'exclude',
                label: '排除规则（满足任一即从候选中排除）',
                children: (
                  <>
                    <Form.Item name={['entryScanConfig', 'excludeClasspaths']} noStyle>
                      <Select mode="tags" placeholder="排除类路径 Ant 模式（如 *.test.*）" style={{ width: '100%' }} />
                    </Form.Item>
                    <Form.Item name={['entryScanConfig', 'excludePackages']} noStyle style={{ marginTop: 8 }}>
                      <Select mode="tags" placeholder="排除包路径（如 com.legacy.config）" style={{ width: '100%' }} />
                    </Form.Item>
                    <Form.Item name={['entryScanConfig', 'excludeAnnotations']} noStyle style={{ marginTop: 8 }}>
                      <Select mode="tags" placeholder="排除注解（如 Internal / Deprecated）" style={{ width: '100%' }} />
                    </Form.Item>
                  </>
                ),
              },
            ]}
          />
        </Form>
      </Drawer>
    </div>
  );
};

export default Systems;
