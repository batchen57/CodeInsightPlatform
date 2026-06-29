import React from 'react';
import { Outlet } from 'react-router-dom';

/**
 * 基础配置模块的轻量子布局：仅挂载 <Outlet />。
 * 真正的侧栏在根 BasicLayout（/basic 路由也是它的 children）。
 */
const BasicLayout: React.FC = () => <Outlet />;

export default BasicLayout;
