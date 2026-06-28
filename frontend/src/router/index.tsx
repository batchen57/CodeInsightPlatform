import { createHashRouter } from 'react-router-dom';
import BasicLayout from '../layouts/BasicLayout';
import Dashboard from '../pages/dashboard';
import Systems from '../pages/systems';
import Prompts from '../pages/prompts';
import Tasks from '../pages/tasks';
import TaskDetail from '../pages/tasks/detail';
import HierarchyReview from '../pages/tasks/hierarchy-review';
import Drafts from '../pages/drafts';
import Push from '../pages/push';
import TokenAudit from '../pages/token-audit';
import Logs from '../pages/logs';
import ModelConfig from '../pages/model/config';

/**
 * 全局 React 路由配置映射表
 * 采用 hash 路由模式（createHashRouter），便于离线部署或纯静态包服务器托管。
 * 嵌套于 BasicLayout 基础排版结构下，所有子路由都将渲染在页面的 Content 区域（通过 Outlet 组件实现）。
 */
export const router = createHashRouter([
  {
    path: '/',
    element: <BasicLayout />, // 公共菜单及排版布局
    children: [
      {
        path: '', // 默认首页：工作台 Dashboard 看板
        element: <Dashboard />,
      },
      {
        path: 'systems', // 系统接入与代码库管理
        element: <Systems />,
      },
      {
        path: 'models', // AI 语言模型配置
        element: <ModelConfig />,
      },
      {
        path: 'prompts', // AI 归纳提示词模板版本管理
        element: <Prompts />,
      },
      {
        path: 'tasks', // 扫描分析任务列表
        element: <Tasks />,
      },
      {
        path: 'tasks/hierarchy-review', // 模块层级调试专用页
        element: <HierarchyReview />,
      },
      {
        path: 'tasks/:id', // 任务执行详情与流程监控
        element: <TaskDetail />,
      },
      {
        path: 'drafts', // 知识草稿复核与编辑器协同工作区
        element: <Drafts />,
      },
      {
        path: 'push', // 知识推送确认与推送 Git 库版本管理
        element: <Push />,
      },
      {
        path: 'audit', // Token 消耗与计费审计报表页面
        element: <TokenAudit />,
      },
      {
        path: 'logs', // 操作历史审计日志页面
        element: <Logs />,
      },
    ],
  },
]);

