import React, { useEffect, useState } from 'react';
import { Alert, Card, Descriptions, Space, Tag, Typography } from 'antd';
import { CheckCircleTwoTone, KeyOutlined, UserOutlined } from '@ant-design/icons';
import { useAuthStore } from '../../../stores/auth';

const { Text, Title } = Typography;

/**
 * 「权限管理」占位页
 *
 * MVP 阶段后端只有一个硬编码 ADMIN 账号；本页仅展示当前会话账号信息
 * + 提示后续接入 UM/SSO 即可在此切换到完整 RBAC 管理。
 */
const PermissionsPage: React.FC = () => {
  const session = useAuthStore((s) => s.session);
  const [now, setNow] = useState(() => new Date().toLocaleString('zh-CN'));

  useEffect(() => {
    // 1 秒一刷，让"当前会话登录时间"显示成"今天 HH:mm:ss"
    const t = window.setInterval(() => setNow(new Date().toLocaleString('zh-CN')), 1000);
    return () => window.clearInterval(t);
  }, []);

  return (
    <div className="ci-page ci-permissions-page">
      <Card
        title={
          <Space>
            <KeyOutlined />
            <span>权限管理</span>
          </Space>
        }
      >
        <Title level={4} style={{ marginTop: 0 }}>
          <Space>
            <UserOutlined />
            <span>当前会话</span>
            {session?.role === 'ADMIN' && (
              <Tag icon={<CheckCircleTwoTone twoToneColor="#52c41a" />} color="green">
                平台管理员
              </Tag>
            )}
            {session?.role === 'USER' && <Tag color="blue">普通用户</Tag>}
          </Space>
        </Title>

        <Descriptions column={1} bordered size="middle">
          <Descriptions.Item label="账号">{session?.username ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="显示名">{session?.displayName ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="角色">{session?.role ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="Token 有效期">
            {session ? `${Math.round((session.expiresInSeconds ?? 0) / 3600)} 小时` : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="登录时间（本地）">{session?.loginAt ?? '—'}</Descriptions.Item>
          <Descriptions.Item label="页面访问时间">{now}</Descriptions.Item>
        </Descriptions>

        <Alert
          type="info"
          showIcon
          style={{ marginTop: 16 }}
          message="本页面为 MVP 阶段占位"
          description={
            <Space direction="vertical">
              <Text>
                当前后端 AuthService 硬编码单一 ADMIN 账号（admin / admin123），
                无用户表、无角色矩阵、无细粒度权限。基础配置模块的路由门禁通过 UI 层 &lt;RequireRole role=&quot;ADMIN&quot;&gt; 控制。
              </Text>
              <Text>
                后续接入真实 UM/SSO 后，本页将切换为：账号增删改查、角色绑定、接口权限矩阵、最近登录审计。
              </Text>
            </Space>
          }
        />
      </Card>
    </div>
  );
};

export default PermissionsPage;
