import React, { useMemo, useState } from 'react';
import { Avatar, Badge, Button, Dropdown, Layout, Menu, Space, Tag, Tooltip, Typography } from 'antd';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
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
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  PlayCircleOutlined,
  SettingOutlined,
  SwapOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useAuthStore } from '../stores/auth';

const { Header, Content, Sider } = Layout;
const { Text } = Typography;

/**
 * 侧边栏导航条数据源配置
 * 定义左侧菜单各个模块的路由路径、图标、标题文本以及详细的功能解释说明说明。
 */
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
    key: '/tasks/hierarchy-review',
    icon: <SwapOutlined />,
    label: <Link to="/tasks/hierarchy-review">模块层级复核</Link>,
    title: '模块层级复核',
    description: '集中处理处于模块层级调试断点的任务，对 AI 提炼的模块 / 子模块 / 功能树进行增删改。',
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

/**
 * 辅助定位匹配当前选中的侧边栏高亮菜单项 Key 值
 * 当路径同时匹配多个菜单（如 /tasks 与 /tasks/hierarchy-review）时，按 key 长度倒序优先匹配最具体的项
 */
const getSelectedKey = (pathname: string) => {
  if (pathname === '/') {
    return '/';
  }
  const matches = navigation
    .filter((item) => item.key !== '/' && pathname.startsWith(item.key))
    .sort((a, b) => b.key.length - a.key.length);
  return matches[0]?.key ?? '/';
};

/**
 * 平台通用骨架布局组件 (BasicLayout)
 * 包含左侧侧边栏导航、顶部页头操作栏、实时动态变化的面包屑标题以及核心内容呈现区（Outlet 组件）。
 */
const BasicLayout: React.FC = () => {
  // 控制左侧侧边栏折叠/展开的状态
  const [collapsed, setCollapsed] = useState(false);
  // 控制移动端小屏幕自适应的状态
  const [isMobile, setIsMobile] = useState(false);
  
  const location = useLocation();
  const navigate = useNavigate();
  const session = useAuthStore((state) => state.session);
  const clearSession = useAuthStore((state) => state.clearSession);
  const selectedKey = getSelectedKey(location.pathname);
  
  // 实时估算当前页面配置数据以更新页头标题解释
  const currentPage = useMemo(
    () => navigation.find((item) => item.key === selectedKey) ?? navigation[0],
    [selectedKey],
  );

  const handleLogout = () => {
    clearSession();
    navigate('/login', { replace: true });
  };

  return (
    <Layout className="ci-shell">
      {/* 响应式侧边栏配置 */}
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
        {/* 系统 LOGO 标识栏 */}
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

        {/* 侧边栏菜单列表 */}
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

        {/* 侧边栏底部:用户信息 + 登出 */}
        <div className="ci-sider-footer">
          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                {
                  key: 'logout',
                  icon: <LogoutOutlined />,
                  label: '退出登录',
                  onClick: handleLogout,
                },
              ],
            }}
            placement="topRight"
          >
            <div
              className={`ci-sider-user ${collapsed ? 'ci-sider-user--collapsed' : ''}`}
              aria-label={`当前用户：${session?.displayName ?? '负责人'}，${session?.role ?? 'ADMIN'}`}
            >
              <Avatar
                size={collapsed ? 32 : 36}
                icon={<UserOutlined />}
                className="ci-sider-user-avatar"
              />
              {!collapsed && (
                <div className="ci-sider-user-copy">
                  <strong>{session?.displayName ?? '负责人'}</strong>
                  <span>{session?.role === 'ADMIN' ? '平台管理员' : '开发负责人'}</span>
                </div>
              )}
            </div>
          </Dropdown>
        </div>
      </Sider>

      {/* 移动端菜单展开时的背景遮罩 */}
      {isMobile && !collapsed && (
        <button
          type="button"
          className="ci-mobile-backdrop"
          aria-label="Close navigation"
          onClick={() => setCollapsed(true)}
        />
      )}
      
      {/* 右侧核心渲染主体区域 */}
      <Layout className="ci-main">
        {/* 顶部操作页头 */}
        <Header className="ci-header">
          {/* 折叠切换按钮 + 页面上下文(标题 + 描述) */}
          <div className="ci-header-context">
            <Tooltip title={collapsed ? '展开导航' : '收起导航'}>
              <Button
                type="text"
                icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                onClick={() => setCollapsed((value) => !value)}
                className="ci-icon-button"
              />
            </Tooltip>
            <span className="ci-header-divider" aria-hidden="true" />
            <div className="ci-header-context-info">
              <Text className="ci-header-context-title">{currentPage.title}</Text>
              {currentPage.description && (
                <Text type="secondary" className="ci-header-context-desc">
                  {currentPage.description}
                </Text>
              )}
            </div>
          </div>

          {/* 顶部右侧快捷标签 */}
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
          </Space>
        </Header>

        {/* 页面正文内容渲染 */}
        <Content className="ci-content">
          {/* 标题与描述已移入 Header 的 ci-header-context,正文直接渲染 */}
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default BasicLayout;
