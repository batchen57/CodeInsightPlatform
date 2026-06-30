import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Avatar, Badge, Breadcrumb, Button, Dropdown, Layout, Menu, Space, Tag, Tooltip, Typography } from 'antd';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import PageTabs from '../components/PageTabs';
import TabLink from '../components/TabLink';
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
  KeyOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  PlayCircleOutlined,
  SettingOutlined,
  SwapOutlined,
  ThunderboltOutlined,
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
  /** 子菜单：用于把『手动下发 / 定时任务』挂在『知识构建任务』下 */
  children?: NavItem[];
}

/**
 * 侧边栏导航数据源配置（按三大类归档）
 *
 * - 仪表盘 / 看板大类 {@link dashboardNav}：运营层视图与全局统计
 *   任务概览 / AI 模型用量 / 流水线分析 / 系统覆盖报表 / Token 审计 / 操作日志
 *
 * - 知识生成大类 {@link knowledgeNav}：端到端知识生产流水线
 *   系统与仓库 → 知识构建任务 → 模块层级 / 知识入口复核 → 知识查看 / 复核 → 知识推送
 *
 * - 基础配置大类 {@link basicNav}：后台管理类页面（模型、提示词、权限、流量）
 *
 * 支持父子结构：父菜单本身可点击进入默认页，下方子菜单用于聚合相关子页面。
 */

/**
 * 仪表盘 / 看板 大类：运营层视图与全局统计
 */
const dashboardNav: NavItem[] = [
  {
    key: '/dashboard/tasks',
    icon: <DashboardOutlined />,
    label: <TabLink to="/dashboard/tasks">任务概览</TabLink>,
    title: '任务概览',
    description: '按状态 / 类型 / 系统多维分析知识构建任务：分布饼图、时间趋势、成功率与平均耗时。',
  },
  {
    key: '/dashboard/ai-usage',
    icon: <BarChartOutlined />,
    label: <TabLink to="/dashboard/ai-usage">AI 模型用量</TabLink>,
    title: 'AI 模型用量',
    description: '按模型 / 阶段分组的 Token 用量、调用次数、成本排行、成功率。',
  },
  {
    key: '/dashboard/pipeline',
    icon: <BranchesOutlined />,
    label: <TabLink to="/dashboard/pipeline">流水线分析</TabLink>,
    title: '流水线分析',
    description: '各阶段（PULLING / PARSING / SPLITTING / AI_ANALYZING ...）的平均耗时与失败率，定位瓶颈。',
  },
  {
    key: '/dashboard/coverage',
    icon: <ApartmentOutlined />,
    label: <TabLink to="/dashboard/coverage">系统覆盖报表</TabLink>,
    title: '系统覆盖报表',
    description: '所有系统的最近反编译时间、任务 / 草稿 / 推送版本数量与覆盖情况。',
  },
  {
    key: '/audit',
    icon: <ThunderboltOutlined />,
    label: <TabLink to="/audit">Token 审计</TabLink>,
    title: 'Token 审计',
    description: '原始 Token 消耗与计费记录查询（明细级）。',
  },
  {
    key: '/logs',
    icon: <HistoryOutlined />,
    label: <TabLink to="/logs">操作日志</TabLink>,
    title: '操作日志',
    description: '追踪系统、仓库、任务、草稿和推送操作。',
  },
];

/**
 * 知识生成 大类：端到端知识生产流水线（生产侧）
 *
 * 知识构建任务下 3 个已实现的子页（任务查询 / JOB配置 / 手动下发）不再挂在某个父菜单下，
 * 全部独立成菜单项。Tasks 容器内的对应 Tab 仍然保留。
 * 注：原 Tasks 容器中还有一个『任务队列』Tab，但尚未有对应的路由实现，因此未加入菜单。
 */
const knowledgeNav: NavItem[] = [
  {
    key: '/systems',
    icon: <ApartmentOutlined />,
    label: <TabLink to="/systems">系统与仓库</TabLink>,
    title: '系统与代码库管理',
    description: '维护业务系统、负责人、Git 仓库、扫描范围和排除规则。',
  },
  {
    key: '/tasks/query',
    icon: <UnorderedListOutlined />,
    label: <TabLink to="/tasks/query">任务查询</TabLink>,
    title: '任务查询',
    description: '按系统 / 状态 / 类型多维查询知识构建任务实例，支持启动 / 终止 / 重试 / 进入复核。',
  },
  {
    key: '/tasks/jobs',
    icon: <ClockCircleOutlined />,
    label: <TabLink to="/tasks/jobs">JOB配置</TabLink>,
    title: 'JOB配置',
    description: '配置 cron 表达式，按指定周期自动创建并执行知识构建任务；支持跳过 / 排队 / 并行三种冲突策略。',
  },
  {
    key: '/tasks/dispatch',
    icon: <PlayCircleOutlined />,
    label: <TabLink to="/tasks/dispatch">手动下发</TabLink>,
    title: '手动下发',
    description: '手动选择系统与仓库，配置入口扫描与提示词后下发知识构建任务。',
  },
  {
    key: '/tasks/hierarchy-review',
    icon: <SwapOutlined />,
    label: <TabLink to="/tasks/hierarchy-review">模块层级复核</TabLink>,
    title: '模块层级复核',
    description: '集中处理处于模块层级调试断点的任务，对 AI 提炼的模块 / 子模块 / 功能树进行增删改。',
  },
  {
    key: '/tasks/entrypoint-review',
    icon: <ApartmentOutlined />,
    label: <TabLink to="/tasks/entrypoint-review">知识入口复核</TabLink>,
    title: '知识入口复核',
    description: '集中处理处于知识入口调试断点的任务，确认入口类清单后由 AI 继续提炼模块层级。',
  },
  {
    key: '/drafts',
    icon: <EditOutlined />,
    label: <TabLink to="/drafts">知识复核</TabLink>,
    title: '知识复核工作区',
    description: '结合代码来源、复核意见和修订记录，复核 AI 生成的 Markdown 草稿。',
  },
  {
    key: '/push',
    icon: <CloudUploadOutlined />,
    label: <TabLink to="/push">知识推送</TabLink>,
    title: '知识推送中心',
    description: '创建确认版本、执行推送校验、导出 ZIP 包并推送到 Git。',
  },
];

/**
 * 知识查看 大类：消费侧 / 只读浏览入口
 */
const knowledgeBrowseNav: NavItem[] = [
  {
    key: '/knowledge/browse',
    icon: <FileSearchOutlined />,
    label: <TabLink to="/knowledge/browse">知识查看</TabLink>,
    title: '知识查看',
    description: '按系统聚合浏览知识文档、索引文件与清单文件（只读，不修改任何资产）。',
  },
];

/**
 * 基础配置 大类：聚合后台管理类页面（模型、提示词、权限、流量）。
 */
const basicNav: NavItem[] = [
  {
    key: '/basic/models',
    icon: <SettingOutlined />,
    label: <TabLink to="/basic/models">模型配置</TabLink>,
    title: '模型配置管理',
    description: '配置 AI 大模型、调用 ID、API 密钥、接口地址及模型排序与默认项。',
  },
  {
    key: '/basic/prompts',
    icon: <FileTextOutlined />,
    label: <TabLink to="/basic/prompts">提示词</TabLink>,
    title: '提示词配置',
    description: 'AI 归纳提示词统一维护：草稿编辑 / 试跑 / 发布 / 设为默认；每种类型支持多个发布版本，仅 1 条当前生效。',
  },
  {
    key: '/basic/permissions',
    icon: <KeyOutlined />,
    label: <TabLink to="/basic/permissions">权限管理</TabLink>,
    title: '权限管理',
    description: '当前账号信息与角色（后续接入 UM/SSO 后扩展为完整 RBAC）。',
  },
  {
    key: '/basic/quota',
    icon: <ThunderboltOutlined />,
    label: <TabLink to="/basic/quota">流量管控</TabLink>,
    title: '流量管控',
    description: '全局限流配置、用户级 Token 额度、AI 调用并发控制。',
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

// 四大类导航 → 拍平为 AntD Menu items + 统一 flatMap（key → NavItem）
const { menuItems: basicMenuItems, flatMap: basicFlat } = buildMenuItems(basicNav);
const { menuItems: knowledgeMenuItems, flatMap: knowledgeFlat } = buildMenuItems(knowledgeNav);
const { menuItems: knowledgeBrowseMenuItems, flatMap: knowledgeBrowseFlat } = buildMenuItems(knowledgeBrowseNav);
const { menuItems: dashboardMenuItems, flatMap: dashboardFlat } = buildMenuItems(dashboardNav);

// 合并 flatMap：currentPage 查找可命中四大类任意 key
const navFlatMap = new Map<string, NavItem>([
  ...basicFlat,
  ...knowledgeFlat,
  ...knowledgeBrowseFlat,
  ...dashboardFlat,
]);

// 兜底 currentPage：取基础配置第一项（保证页面顶部 kicker / title 一定有值）
const fallbackCurrentPage = basicNav[0];

/**
 * 计算 selectedKey 与 openKeys：
 * - selectedKey 取最深匹配的菜单 key（子菜单优先于父菜单）
 * - selectedKeys 额外包含所有祖先父菜单 key，让父菜单在子项被选中时也高亮
 * - openKeys 取所有祖先父菜单 key
 */
const computeMenuState = (pathname: string) => {
  let selectedKey = basicNav[0].key;
  let bestDepth = -1;
  const selectedKeys = new Set<string>([selectedKey]);
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

  for (const item of basicNav) {
    visit(item, []);
  }
  for (const item of knowledgeNav) {
    visit(item, []);
  }
  for (const item of knowledgeBrowseNav) {
    visit(item, []);
  }
  for (const item of dashboardNav) {
    visit(item, []);
  }

  return {
    selectedKey,
    selectedKeys: Array.from(selectedKeys),
    openKeys: Array.from(openKeys),
  };
};

/** 在四大类里找当前选中节点的父菜单（用于面包屑）。 */
function findParentNavItem(key: string): NavItem | null {
  for (const group of [basicNav, knowledgeNav, knowledgeBrowseNav, dashboardNav]) {
    for (const item of group) {
      if (item.children?.some((c) => c.key === key)) {
        return item;
      }
    }
  }
  return null;
}

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
  // 优先取子菜单（如 /tasks/jobs 命中"JOB配置"），否则回退到仪表盘第一项
  const currentPage = useMemo(
    () => navFlatMap.get(selectedKey) ?? fallbackCurrentPage,
    [selectedKey],
  );

  // 如果当前选中的是子菜单，取其父菜单用于面包屑（跨三大类查找）
  const parentNavItem = useMemo(
    () => findParentNavItem(selectedKey),
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

        {/* 侧边栏菜单列表：按 基础配置 / 知识生成 / 知识查看 / 仪表盘 四大类分段渲染 */}
        {/* 第 1 段：基础配置 */}
        <div className="ci-sider-section">
          {!collapsed && <span className="ci-sider-label">基础配置</span>}
          <Menu
            mode="inline"
            selectedKeys={selectedKeys}
            openKeys={openKeys}
            onOpenChange={handleOpenChange}
            onClick={() => {
              if (isMobile) setCollapsed(true);
            }}
            items={basicMenuItems}
            className="ci-menu"
          />
        </div>
        {/* 第 2 段：知识生成 */}
        <div className="ci-sider-section">
          {!collapsed && <span className="ci-sider-label">知识生成</span>}
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
            items={knowledgeMenuItems}
            className="ci-menu"
          />
        </div>
        {/* 第 3 段：知识查看 */}
        <div className="ci-sider-section">
          {!collapsed && <span className="ci-sider-label">知识查看</span>}
          <Menu
            mode="inline"
            selectedKeys={selectedKeys}
            openKeys={openKeys}
            onOpenChange={handleOpenChange}
            onClick={() => {
              if (isMobile) setCollapsed(true);
            }}
            items={knowledgeBrowseMenuItems}
            className="ci-menu"
          />
        </div>
        {/* 第 4 段：仪表盘 / 看板 */}
        <div className="ci-sider-section ci-sider-section-last">
          {!collapsed && <span className="ci-sider-label">仪表盘 / 看板</span>}
          <Menu
            mode="inline"
            selectedKeys={selectedKeys}
            openKeys={openKeys}
            onOpenChange={handleOpenChange}
            onClick={() => {
              if (isMobile) setCollapsed(true);
            }}
            items={dashboardMenuItems}
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
          {/* 多页签导航条：每个打开的页面 = 一个 tab，工作台不计入。
             必须放在最顶部,在面包屑/页面标题之前。 */}
          <PageTabs />

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
