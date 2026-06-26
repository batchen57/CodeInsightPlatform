import React, { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Col,
  Descriptions,
  Drawer,
  Form,
  Input,
  Modal,
  Row,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  GlobalOutlined,
  PlusOutlined,
  ReloadOutlined,
  SearchOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import { changeSystemStatus, createSystem, listSystems, updateSystem } from '../../api/system';
import { createRepository, listRepositories, testRepositoryConnection, updateRepository } from '../../api/repository';
import type { Repository, System } from '../../types';

const { Text } = Typography;

const Systems: React.FC = () => {
  const [systems, setSystems] = useState<System[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(10);

  const [searchName, setSearchName] = useState('');
  const [searchOwner, setSearchOwner] = useState('');
  const [searchStatus, setSearchStatus] = useState<number | undefined>();

  const [systemModalOpen, setSystemModalOpen] = useState(false);
  const [editingSystem, setEditingSystem] = useState<System | null>(null);
  const [systemForm] = Form.useForm();

  const [selectedSystem, setSelectedSystem] = useState<System | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [repositories, setRepositories] = useState<Repository[]>([]);
  const [repoLoading, setRepoLoading] = useState(false);

  const [repoModalOpen, setRepoModalOpen] = useState(false);
  const [editingRepo, setEditingRepo] = useState<Repository | null>(null);
  const [testingConnection, setTestingConnection] = useState(false);
  const [repoForm] = Form.useForm();

  const fetchSystems = useCallback(
    async (page = current, pageSize = size) => {
      setLoading(true);
      try {
        const data = await listSystems({
          current: page,
          size: pageSize,
          name: searchName || undefined,
          owner: searchOwner || undefined,
          status: searchStatus,
        });
        setSystems(data.records);
        setTotal(data.total);
      } finally {
        setLoading(false);
      }
    },
    [current, searchName, searchOwner, searchStatus, size],
  );

  const fetchRepositories = useCallback(async (systemId: number) => {
    setRepoLoading(true);
    try {
      const data = await listRepositories({ current: 1, size: 50, systemId });
      setRepositories(data.records);
    } finally {
      setRepoLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchSystems();
  }, [fetchSystems]);

  const handleSearch = () => {
    setCurrent(1);
    fetchSystems(1);
  };

  const handleReset = () => {
    setSearchName('');
    setSearchOwner('');
    setSearchStatus(undefined);
    setCurrent(1);
    setTimeout(() => fetchSystems(1), 0);
  };

  const openSystemModal = (system: System | null = null) => {
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
  };

  const handleSystemSubmit = async () => {
    const values = await systemForm.validateFields();
    if (editingSystem) {
      await updateSystem(editingSystem.id, values);
      message.success('系统已更新');
    } else {
      await createSystem(values);
      message.success('系统已创建');
    }
    setSystemModalOpen(false);
    fetchSystems();
  };

  const handleStatusChange = async (checked: boolean, record: System) => {
    const status = checked ? 1 : 0;
    await changeSystemStatus(record.id, status);
    message.success(`${record.name} 已${checked ? '启用' : '停用'}`);
    fetchSystems();
    if (selectedSystem?.id === record.id) {
      setSelectedSystem({ ...selectedSystem, status });
    }
  };

  const openDetailDrawer = (system: System) => {
    setSelectedSystem(system);
    setDrawerOpen(true);
    fetchRepositories(system.id);
  };

  const openRepoModal = (repo: Repository | null = null) => {
    setEditingRepo(repo);
    if (repo) {
      repoForm.setFieldsValue(repo);
    } else {
      repoForm.resetFields();
      repoForm.setFieldsValue({ branch: 'main', scanRoot: '/' });
    }
    setRepoModalOpen(true);
  };

  const handleTestConnection = async () => {
    const values = await repoForm.validateFields(['gitUrl', 'branch', 'username', 'password']);
    setTestingConnection(true);
    try {
      const success = await testRepositoryConnection({ ...values, id: editingRepo?.id });
      if (success) {
        message.success('Git 连接测试成功');
      } else {
        message.error('Git 连接测试失败');
      }
    } finally {
      setTestingConnection(false);
    }
  };

  const handleRepoSubmit = async () => {
    if (!selectedSystem) {
      return;
    }
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
    fetchRepositories(selectedSystem.id);
  };

  const columns = [
    {
      title: '系统',
      dataIndex: 'name',
      key: 'name',
      render: (text: string, record: System) => (
        <Button type="link" className="ci-table-link" onClick={() => openDetailDrawer(record)}>
          {text}
        </Button>
      ),
    },
    {
      title: '负责人',
      dataIndex: 'owner',
      key: 'owner',
      width: 120,
      render: (owner: string) => <Tag color="blue">{owner || '未分配'}</Tag>,
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: number, record: System) => (
        <Switch
          checkedChildren="启用"
          unCheckedChildren="停用"
          checked={status === 1}
          onChange={(checked) => handleStatusChange(checked, record)}
        />
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (time: string) => (time ? new Date(time).toLocaleString() : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 150,
      render: (_: unknown, record: System) => (
        <Space size={8}>
          <Button size="small" onClick={() => openSystemModal(record)}>
            编辑
          </Button>
          <Button size="small" icon={<SettingOutlined />} onClick={() => openDetailDrawer(record)}>
            仓库
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div className="ci-page ci-systems-page">
      <Card className="ci-filter-card">
        <Row gutter={[12, 12]} align="middle">
          <Col xs={24} md={6}>
            <Input
              placeholder="搜索系统"
              value={searchName}
              onChange={(event) => setSearchName(event.target.value)}
              onPressEnter={handleSearch}
              prefix={<SearchOutlined />}
            />
          </Col>
          <Col xs={24} md={6}>
            <Input
              placeholder="筛选负责人"
              value={searchOwner}
              onChange={(event) => setSearchOwner(event.target.value)}
              onPressEnter={handleSearch}
            />
          </Col>
          <Col xs={24} md={5}>
            <Select
              placeholder="状态"
              style={{ width: '100%' }}
              allowClear
              value={searchStatus}
              onChange={setSearchStatus}
              options={[
                { value: 1, label: '启用' },
                { value: 0, label: '停用' },
              ]}
            />
          </Col>
          <Col xs={24} md={7}>
            <Space className="ci-toolbar-actions" wrap>
              <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
                查询
              </Button>
              <Button icon={<ReloadOutlined />} onClick={handleReset}>
                重置
              </Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={() => openSystemModal()}>
                新增系统
              </Button>
            </Space>
          </Col>
        </Row>
      </Card>

      <Card className="ci-systems-table-card">
        <Table
          dataSource={systems}
          columns={columns}
          rowKey="id"
          loading={loading}
          scroll={{ x: 960 }}
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

      <Modal
        title={editingSystem ? '编辑系统' : '创建系统'}
        open={systemModalOpen}
        onOk={handleSystemSubmit}
        onCancel={() => setSystemModalOpen(false)}
        destroyOnHidden
      >
        <Form form={systemForm} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item name="name" label="系统名称" rules={[{ required: true, message: '请输入系统名称' }]}>
            <Input placeholder="order-service" />
          </Form.Item>
          <Form.Item name="owner" label="负责人" rules={[{ required: true, message: '请输入负责人' }]}>
            <Input placeholder="开发负责人" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} placeholder="核心业务职责和技术范围" />
          </Form.Item>
        </Form>
      </Modal>

      <Drawer
        title={selectedSystem ? `代码库设置：${selectedSystem.name}` : '代码库设置'}
        width={1000}
        onClose={() => setDrawerOpen(false)}
        open={drawerOpen}
        destroyOnHidden
      >
        {selectedSystem && (
          <Space direction="vertical" size={18} style={{ width: '100%' }}>
            <Descriptions bordered column={2} size="small">
              <Descriptions.Item label="负责人">
                <Tag color="blue">{selectedSystem.owner}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <SystemStatus status={selectedSystem.status} />
              </Descriptions.Item>
              <Descriptions.Item label="创建时间" span={2}>
                {new Date(selectedSystem.createdAt).toLocaleString()}
              </Descriptions.Item>
              <Descriptions.Item label="描述" span={2}>
                {selectedSystem.description || '暂无描述'}
              </Descriptions.Item>
            </Descriptions>

            <Card
              size="small"
              title="Git 代码库"
              extra={
                <Button type="primary" size="small" icon={<PlusOutlined />} onClick={() => openRepoModal()}>
                  添加代码库
                </Button>
              }
            >
              <Table
                dataSource={repositories}
                rowKey="id"
                loading={repoLoading}
                size="small"
                pagination={false}
                scroll={{ x: 750 }}
                columns={[
                  {
                    title: 'Git 地址',
                    dataIndex: 'gitUrl',
                    key: 'gitUrl',
                    width: 280,
                    render: (url: string) => (
                      <Tooltip title={url}>
                        <Text code style={{ whiteSpace: 'nowrap' }}>{url?.length > 48 ? `${url.substring(0, 45)}...` : url}</Text>
                      </Tooltip>
                    ),
                  },
                  {
                    title: '分支',
                    dataIndex: 'branch',
                    key: 'branch',
                    width: 100,
                    render: (branch: string) => <Tag color="geekblue">{branch}</Tag>,
                  },
                  {
                    title: '扫描根目录',
                    dataIndex: 'scanRoot',
                    key: 'scanRoot',
                    width: 110,
                    render: (root: string) => <Text code>{root}</Text>,
                  },
                  {
                    title: '最近运行',
                    dataIndex: 'lastDecompileAt',
                    key: 'lastDecompileAt',
                    width: 160,
                    render: (time: string) => (time ? new Date(time).toLocaleString() : '-'),
                  },
                  {
                    title: '操作',
                    key: 'action',
                    width: 120,
                    render: (_: unknown, repo: Repository) => (
                      <Space size={8}>
                        <Button size="small" onClick={() => openRepoModal(repo)}>
                          编辑
                        </Button>
                        <Button
                          size="small"
                          icon={<GlobalOutlined />}
                          onClick={async () => {
                            message.loading({ content: '正在测试 Git 连接...', key: 'test-conn' });
                            const connected = await testRepositoryConnection(repo);
                            if (connected) {
                              message.success({ content: 'Git 连接测试成功', key: 'test-conn' });
                            } else {
                              message.error({ content: 'Git 连接测试失败', key: 'test-conn' });
                            }
                          }}
                        >
                          测试
                        </Button>
                      </Space>
                    ),
                  },
                ]}
              />
            </Card>
          </Space>
        )}
      </Drawer>

      <Modal
        title={editingRepo ? '编辑代码库' : '添加代码库'}
        open={repoModalOpen}
        onCancel={() => setRepoModalOpen(false)}
        width={680}
        footer={[
          <Button key="cancel" onClick={() => setRepoModalOpen(false)}>
            取消
          </Button>,
          <Button key="test" loading={testingConnection} onClick={handleTestConnection}>
            测试 Git
          </Button>,
          <Button key="submit" type="primary" onClick={handleRepoSubmit}>
            保存
          </Button>,
        ]}
        destroyOnHidden
      >
        <Form form={repoForm} layout="vertical" style={{ marginTop: 16 }}>
          <Row gutter={16}>
            <Col span={24}>
              <Form.Item name="gitUrl" label="Git 地址" rules={[{ required: true, message: '请输入 Git 地址' }]}>
                <Input placeholder="https://github.com/company/project.git" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="branch" label="分支" rules={[{ required: true, message: '请输入分支' }]}>
                <Input placeholder="main" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="scanRoot" label="扫描根目录" rules={[{ required: true, message: '请输入扫描根目录' }]}>
                <Input placeholder="/ 或 /backend" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="username" label="用户名">
                <Input placeholder="私有仓库账号" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="password" label="密码 / 访问令牌">
                <Input.Password placeholder="仅由后端存储" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="excludeDirs" label="排除目录">
                <Input placeholder=".git,target,test,bin" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="excludeFileTypes" label="排除文件类型">
                <Input placeholder=".md,.txt,.xml,.json" />
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Modal>
    </div>
  );
};

const SystemStatus: React.FC<{ status: number }> = ({ status }) => {
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

export default Systems;
