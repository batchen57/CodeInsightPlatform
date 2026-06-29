import React from 'react';
import { Button, Result } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../stores/auth';

export type UserRole = 'ADMIN' | 'USER';

interface Props {
  role: UserRole;
  children: React.ReactNode;
}

/**
 * 角色门：未登录跳 /login；登录但角色不符显示 403 占位。
 *
 * 当前 MVP 阶段后端 role 恒为 ADMIN（AuthServiceImpl 硬编码），
 * 本组件只起到"UI 层隔离 + 后续接入真实 UM/SSO 时能直接接管"的作用。
 */
const RequireRole: React.FC<Props> = ({ role, children }) => {
  const session = useAuthStore((s) => s.session);
  const navigate = useNavigate();

  if (!session) {
    // 不通过 useEffect+navigate，避免首次渲染闪烁；直接渲染 redirect 占位
    setTimeout(() => navigate('/login', { replace: true }), 0);
    return null;
  }

  if (session.role !== role) {
    return (
      <Result
        status="403"
        title="403"
        subTitle={`当前账号角色为「${session.role}」，无权访问「${role}」专属页面`}
        extra={
          <Button type="primary" onClick={() => navigate('/')}>
            返回工作台
          </Button>
        }
      />
    );
  }

  return <>{children}</>;
};

export default RequireRole;
