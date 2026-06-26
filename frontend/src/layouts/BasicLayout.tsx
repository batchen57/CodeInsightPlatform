import React, { useMemo, useState } from 'react';
import { Avatar, Badge, Button, Layout, Menu, Space, Tag, Tooltip, Typography } from 'antd';
import { Link, Outlet, useLocation } from 'react-router-dom';
import {
  ApartmentOutlined,
  AuditOutlined,
  BarChartOutlined,
  BellOutlined,
  BranchesOutlined,
  CloudUploadOutlined,
  CodeOutlined,
  DashboardOutlined,
  EditOutlined,
  FileSearchOutlined,
  FileTextOutlined,
  HistoryOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  PlayCircleOutlined,
  SettingOutlined,
  UserOutlined,
} from '@ant-design/icons';

const { Header, Content, Sider } = Layout;
const { Text, Title } = Typography;

const navigation = [
  {
    key: '/',
    icon: <DashboardOutlined />,
    label: <Link to="/">工作台</Link>,
    title: '知识运营工作台',
    description: '跟踪系统接入、代码分析任务、草稿复核、知识推送和 Token 成本。',
  },
  {
    key: '/systems',
    icon: <ApartmentOutlined />,
    label: <Link to="/systems">系统与仓库</Link>,
    title: '系统与代码库管理',
    description: '维护业务系统、负责人、Git 仓库、扫描范围和排除规则。',
  },
  {
    key: '/models',
    icon: <SettingOutlined />,
    label: <Link to="/models">模型配置</Link>,
    title: '模型配置管理',
    description: '配置 AI 大模型、调用 ID、API 密钥、接口地址及模型排序与默认项。',
  },
  {
    key: '/prompts',
    icon: <FileTextOutlined />,
    label: <Link to="/prompts">提示词</Link>,

    title: '提示词配置',
    description: '管理 AI 归纳模板、版本、启停、复制和试跑。',
  },
  {
    key: '/tasks',
    icon: <PlayCircleOutlined />,
    label: <Link to="/tasks">反编译任务</Link>,
    title: '反编译任务',
    description: '创建、启动、终止、重试并监控代码知识生成任务。',
  },
  {
    key: '/drafts',
    icon: <EditOutlined />,
    label: <Link to="/drafts">知识复核</Link>,
    title: '知识复核工作区',
    description: '结合代码来源、复核意见和修订记录，复核 AI 生成的 Markdown 草稿。',
  },
  {
    key: '/push',
    icon: <CloudUploadOutlined />,
    label: <Link to="/push">知识推送</Link>,
    title: '知识推送中心',
    description: '创建确认版本、执行推送校验、导出 ZIP 包并推送到 Git。',
  },
  {
    key: '/audit',
    icon: <BarChartOutlined />,
    label: <Link to="/audit">Token 审计</Link>,
    title: 'Token 审计',
    description: '分析模型调用、Token 用量、成本趋势和额度风险。',
  },
  {
    key: '/logs',
    icon: <HistoryOutlined />,
    label: <Link to="/logs">操作日志</Link>,
    title: '操作日志',
    description: '追踪系统、仓库、任务、草稿和推送操作。',
  },
];

const getSelectedKey = (pathname: string) => {
  if (pathname === '/') {
    return '/';
  }
  const match = navigation.find((item) => item.key !== '/' && pathname.startsWith(item.key));
  return match?.key ?? '/';
};

const BasicLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const location = useLocation();
  const selectedKey = getSelectedKey(location.pathname);
  const currentPage = useMemo(
    () => navigation.find((item) => item.key === selectedKey) ?? navigation[0],
    [selectedKey],
  );

  return (
    <Layout className="ci-shell">
      <Sider
        width={236}
        collapsedWidth={isMobile ? 0 : 72}
        collapsed={collapsed}
        trigger={null}
        breakpoint="lg"
        onBreakpoint={(broken) => {
          setIsMobile(broken);
          setCollapsed(broken);
        }}
        className="ci-sider"
      >
        <div className="ci-brand">
          <div className="ci-brand-mark">
            <CodeOutlined />
          </div>
          {!collapsed && (
            <div className="ci-brand-copy">
              <strong>代码洞察</strong>
              <span>代码知识平台</span>
            </div>
          )}
        </div>

        <div className="ci-sider-section">
          {!collapsed && <span className="ci-sider-label">核心流程</span>}
          <Menu
            mode="inline"
            selectedKeys={[selectedKey]}
            items={navigation.map(({ key, icon, label }) => ({ key, icon, label }))}
            onClick={() => isMobile && setCollapsed(true)}
            className="ci-menu"
          />
        </div>


      </Sider>

      {isMobile && !collapsed && (
        <button
          type="button"
          className="ci-mobile-backdrop"
          aria-label="Close navigation"
          onClick={() => setCollapsed(true)}
        />
      )}
      <Layout className="ci-main">
        <Header className="ci-header">
          <Space size={12} className="ci-header-context">
            <Tooltip title={collapsed ? '展开导航' : '收起导航'}>
              <Button
                type="text"
                icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                onClick={() => setCollapsed((value) => !value)}
                className="ci-icon-button"
              />
            </Tooltip>
          </Space>

          <Space size={16} className="ci-header-actions">
            <div className="ci-pipeline">
              <Tag icon={<BranchesOutlined />} color="blue">
                扫描
              </Tag>
              <Tag icon={<FileSearchOutlined />} color="cyan">
                AI 归纳
              </Tag>
              <Tag icon={<AuditOutlined />} color="green">
                复核
              </Tag>
            </div>
            <Tooltip title="待处理事项">
              <Badge count={3} size="small">
                <Button type="text" icon={<BellOutlined />} className="ci-icon-button" />
              </Badge>
            </Tooltip>
            <div className="ci-user" aria-label="当前用户：负责人，开发负责人">
              <Avatar size={34} icon={<UserOutlined />} className="ci-user-avatar" />
              <div className="ci-user-copy">
                <strong>负责人</strong>
                <span>开发负责人</span>
              </div>
            </div>
          </Space>
        </Header>

        <Content className="ci-content">
          <section className="ci-page-heading">
            <div className="ci-page-heading-copy">
              <Text className="ci-page-kicker">WORKSPACE</Text>
              <Title level={2}>{currentPage.title}</Title>
              <Text type="secondary">{currentPage.description}</Text>
            </div>
            <div className="ci-page-heading-status">
              <Badge status="processing" />
              <span>Live workspace</span>
            </div>
          </section>
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default BasicLayout;
