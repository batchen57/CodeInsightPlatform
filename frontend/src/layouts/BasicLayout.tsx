import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Avatar, Badge, Breadcrumb, Button, Dropdown, Layout, Menu, Space, Tag, Tooltip, Typography } from 'antd';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import {
  ApartmentOutlined,
  AuditOutlined,
  BarChartOutlined,
  BellOutlined,
  BranchesOutlined,
  ClockCircleOutlined,
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
  UnorderedListOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useAuthStore } from '../stores/auth';

const { Header, Content, Sider } = Layout;
const { Text, Title } = Typography;

interface NavItem {
  key: string;
  icon: React.ReactNode;
  label: React.ReactNode;
  title: string;
  description: string;
  /** 子菜单：用于把『手动下发 / 定时任务』挂在『反编译任务』下 */
  children?: NavItem[];
}

/**
 * 侧边栏导航条数据源配置
 * 定义左侧菜单各个模块的路由路径、图标、标题文本以及详细的功能解释说明说明。
 * 支持父子结构：父菜单本身可点击进入默认页，下方子菜单用于聚合相关子页面。
 */
const navigation: NavItem[] = [
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
    // 父菜单 label 不用 Link，保留纯文本：点击只展开/折叠，避免 SubMenu 渲染带链接标题时样式发暗；
    // 默认进入父菜单时直接跳到第一个子页（手动下发）。
    label: '反编译任务',
    title: '反编译任务',
    description: '管理反编译任务：手动下发的实例与按 cron 周期触发的定时调度配置。',
    children: [
      {
        key: '/tasks',
        icon: <UnorderedListOutlined />,
        label: <Link to="/tasks">手动下发</Link>,
        title: '手动下发的反编译任务',
        description: '查看由用户手动创建并启动的反编译任务实例，支持启动 / 终止 / 重试 / 进入复核。',
      },
      {
        key: '/schedules',
        icon: <ClockCircleOutlined />,
        label: <Link to="/schedules">定时任务</Link>,
        title: '定时任务调度',
        description: '配置 cron 表达式，按指定周期自动创建并执行反编译任务；支持跳过 / 排队 / 并行三种冲突策略。',
      },
    ],
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
 * 把 NavItem 树拍平为 AntD Menu items 需要的结构。
 * 子项 key 在父项 key 之下，selectedKey 取最深匹配。
 */
const buildMenuItems = (items: NavItem[]): { menuItems: any[]; flatMap: Map<string, NavItem> } => {
  const flatMap = new Map<string, NavItem>();
  const menuItems = items.map((item) => {
    flatMap.set(item.key, item);
    const mi: any = { key: item.key, icon: item.icon, label: item.label };
    if (item.children && item.children.length > 0) {
      mi.children = item.children.map((c) => {
        flatMap.set(c.key, c);
        return { key: c.key, icon: c.icon, label: c.label };
      });
    }
    return mi;
  });
  return { menuItems, flatMap };
};

const { menuItems, flatMap: navFlatMap } = buildMenuItems(navigation);

/**
 * 计算 selectedKey 与 openKeys：
 * - selectedKey 取最深匹配的菜单 key（子菜单优先于父菜单）
 * - selectedKeys 额外包含所有祖先父菜单 key，让父菜单在子项被选中时也高亮
 * - openKeys 取所有祖先父菜单 key
 */
const computeMenuState = (pathname: string) => {
  let selectedKey = '/';
  let bestDepth = -1;
  const selectedKeys = new Set<string>(['/']);
  const openKeys = new Set<string>();

  const visit = (item: NavItem, ancestors: string[]) => {
    const matches =
      item.key === '/'
        ? pathname === '/'
        : pathname === item.key || pathname.startsWith(item.key + '/');
    if (matches) {
      const depth = item.key.split('/').length;
      if (depth > bestDepth) {
        bestDepth = depth;
        selectedKey = item.key;
      }
      selectedKeys.add(item.key);
      ancestors.forEach((a) => {
        selectedKeys.add(a);
        openKeys.add(a);
      });
    }
    if (item.children) {
      for (const child of item.children) {
        visit(child, [...ancestors, item.key]);
      }
    }
  };

  for (const item of navigation) {
    visit(item, []);
  }

  return {
    selectedKey,
    selectedKeys: Array.from(selectedKeys),
    openKeys: Array.from(openKeys),
  };
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

  // 计算当前选中的菜单 key 与需要展开的父菜单 key
  const { selectedKey, selectedKeys, openKeys: initialOpenKeys } = useMemo(
    () => computeMenuState(location.pathname),
    [location.pathname],
  );
  // 用户也可手动展开/折叠父菜单
  // 行为约定：
  // - 初次进入页面：openKeys 初始化为命中路径的父菜单（initialOpenKeys）
  // - 路由变化且命中其他父菜单：自动把新父菜单追加到 openKeys（不删除用户已展开的）
  // - 用户手动操作过一次后：不再受路由变化影响（除非用户再次操作）
  const [openKeys, setOpenKeys] = useState<string[]>(initialOpenKeys);
  const userTouchedRef = useRef(false);
  useEffect(() => {
    if (userTouchedRef.current) return;
    setOpenKeys(initialOpenKeys);
  }, [initialOpenKeys]);

  const handleOpenChange = (keys: string[]) => {
    userTouchedRef.current = true;
    setOpenKeys(keys);
  };

  // 实时估算当前页面配置数据以更新页头标题解释
  // 优先取子菜单（如 /schedules 命中"定时任务"），否则回退到父菜单
  const currentPage = useMemo(
    () => navFlatMap.get(selectedKey) ?? navigation[0],
    [selectedKey],
  );

  // 如果当前选中的是子菜单，取其父菜单用于面包屑
  const parentNavItem = useMemo(() => {
    for (const item of navigation) {
      if (item.children?.some((c) => c.key === selectedKey)) {
        return item;
      }
    }
    return null;
  }, [selectedKey]);

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
            selectedKeys={selectedKeys}
            openKeys={openKeys}
            onOpenChange={handleOpenChange}
            onClick={() => {
              if (isMobile) setCollapsed(true);
              // 父菜单（带 children 的项）点击不跳转——只通过 onOpenChange 展开/折叠。
              // 必须用户点击某个具体子菜单项才路由跳转。
            }}
            items={menuItems}
            className="ci-menu"
          />
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
          {/* 折叠切换按钮 */}
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

          {/* 顶部右侧快捷标签与用户属性 */}
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
            >
              <div className="ci-user" aria-label={`当前用户：${session?.displayName ?? '负责人'}，${session?.role ?? 'ADMIN'}`}>
                <Avatar size={34} icon={<UserOutlined />} className="ci-user-avatar" />
                <div className="ci-user-copy">
                  <strong>{session?.displayName ?? '负责人'}</strong>
                  <span>{session?.role === 'ADMIN' ? '平台管理员' : '开发负责人'}</span>
                </div>
              </div>
            </Dropdown>
          </Space>
        </Header>

        {/* 页面正文内容渲染 */}
        <Content className="ci-content">
          {/* 统一的面包屑及描述页头 */}
          <section className="ci-page-heading">
            <div className="ci-page-heading-copy">
              {parentNavItem && (
                <Breadcrumb
                  className="ci-page-breadcrumb"
                  items={[
                    { title: <Link to={parentNavItem.key}>{parentNavItem.title}</Link> },
                    { title: currentPage.title },
                  ]}
                />
              )}
              <Text className="ci-page-kicker">WORKSPACE</Text>
              <Title level={2}>{currentPage.title}</Title>
              <Text type="secondary">{currentPage.description}</Text>
            </div>
            <div className="ci-page-heading-status">
              <Badge status="processing" />
              <span>Live workspace</span>
            </div>
          </section>
          
          {/* 嵌套子组件渲染 Outlet */}
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  );
};

export default BasicLayout;
