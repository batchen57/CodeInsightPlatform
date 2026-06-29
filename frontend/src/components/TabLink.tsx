import React from 'react';
import { Link, useNavigate, type LinkProps } from 'react-router-dom';
import { useTabsStore } from '../stores/tabs';
import { getPageTitle } from '../utils/pageTitle';

/**
 * 侧栏菜单专用的 Link：
 *  - 点击时把目标路径加入 tab 栏（可同时开多个相同 path 的 tab）
 *  - 默认走 React Router 的 <Link> 渲染
 *
 * 行为与原生 <Link> 兼容：可以传 children（菜单文字/icon）、className/style/onClick。
 */
interface TabLinkProps extends Omit<LinkProps, 'to' | 'onClick'> {
  to: string;
  title?: string;
  children?: React.ReactNode;
  onClick?: (event: React.MouseEvent<HTMLAnchorElement>) => void;
}

const TabLink: React.FC<TabLinkProps> = ({ to, title, children, onClick, ...rest }) => {
  const navigate = useNavigate();
  const addTab = useTabsStore((s) => s.addTab);

  const handleClick = (event: React.MouseEvent<HTMLAnchorElement>) => {
    // 允许调用方先处理（如折叠侧边栏等）
    onClick?.(event);
    if (event.defaultPrevented) return;
    // 中键/右键新标签打开：不做 tab 操作，让浏览器处理
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
      return;
    }
    // 外链或 hash-only：放过
    if (to.startsWith('http') || to.startsWith('mailto:')) return;

    const finalTitle = title ?? getPageTitle(to);
    addTab({ path: to, title: finalTitle });

    // 阻止 <Link> 的默认行为，手动 navigate（这样可以确保是 pushState 而非 hash 替换）
    event.preventDefault();
    navigate(to);
  };

  return (
    <Link to={to} onClick={handleClick} {...rest}>
      {children}
    </Link>
  );
};

export default TabLink;
