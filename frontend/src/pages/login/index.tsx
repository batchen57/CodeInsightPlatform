import React, { useCallback, useMemo, useState } from 'react';
import {
  Button,
  Checkbox,
  Form,
  Input,
  Typography,
  message,
} from 'antd';
import {
  LockOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { login } from '../../api/auth';
import { useAuthStore } from '../../stores/auth';
import type { LoginRequest } from '../../types';
import BrandMark from './BrandMark';
import OtpInput from './OtpInput';
import './index.css';

const { Text, Title } = Typography;

type LoginLocationState = {
  from?: {
    pathname?: string;
  };
};

type LoginFormFields = {
  username: string;
  password: string;
  token: string;
};

const Login: React.FC = () => {
  const [form] = Form.useForm<LoginFormFields>();
  const [submitting, setSubmitting] = useState(false);
  const [remember, setRemember] = useState(true);
  const navigate = useNavigate();
  const location = useLocation();
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  const setSession = useAuthStore((state) => state.setSession);

  const redirectTo = useMemo(() => {
    const state = location.state as LoginLocationState | null;
    return state?.from?.pathname && state.from.pathname !== '/login' ? state.from.pathname : '/';
  }, [location.state]);

  const submit = useCallback(
    async (values: LoginFormFields) => {
      setSubmitting(true);
      try {
        const payload: LoginRequest = values;
        const session = await login(payload);
        setSession(session);
        message.success(`欢迎回来，${session.displayName || session.username}`);
        navigate(redirectTo, { replace: true });
      } catch (error) {
        console.error('登录失败', error);
      } finally {
        setSubmitting(false);
      }
    },
    [redirectTo, navigate, setSession],
  );

  if (isAuthenticated) {
    return <Navigate to={redirectTo} replace />;
  }

  return (
    <main className="login-screen">
      {/* ===== 左侧品牌区 ===== */}
      <aside className="login-brand-panel">
        {/* 背景装饰 */}
        <div className="login-brand-bg" aria-hidden="true" />
        <div className="login-brand-orb" aria-hidden="true" />

        <div className="login-brand-inner">
          <BrandMark size={56} className="login-brand-logo" />
          <div className="login-brand-text">
            <h1 className="login-brand-name">
              代码洞察平台
            </h1>
            <p className="login-brand-tagline">
              CodeInsight Platform
            </p>
            <p className="login-brand-desc">
              将代码库转化为可维护、可追溯、可复核的知识资产
            </p>
          </div>

          {/* 品牌区底部装饰线 */}
          <div className="login-brand-accent" aria-hidden="true" />
        </div>

        <footer className="login-brand-footer">
          <Text type="secondary">© 2026 CodeInsight · 平安科技</Text>
        </footer>
      </aside>

      {/* ===== 右侧表单区 ===== */}
      <section className="login-form-panel">
        <div className="login-form-card">
          <header className="login-form-head">
            <Title level={3} className="login-form-title">
              登录
            </Title>
            <Text type="secondary" className="login-form-subtitle">
              使用 UM 账号与平安令牌继续
            </Text>
          </header>

          <Form<LoginFormFields>
            form={form}
            layout="vertical"
            requiredMark={false}
            onFinish={submit}
            className="login-form"
            autoComplete="on"
          >
            <Form.Item
              label="UM 账号"
              name="username"
              rules={[
                { required: true, message: '请输入 UM 账号' },
                { whitespace: true, message: 'UM 账号不能为空白' },
              ]}
            >
              <Input
                prefix={<UserOutlined />}
                placeholder="请输入工号或邮箱"
                autoComplete="username"
                size="large"
                allowClear
              />
            </Form.Item>

            <Form.Item
              label="UM 密码"
              name="password"
              rules={[{ required: true, message: '请输入 UM 密码' }]}
            >
              <Input.Password
                prefix={<LockOutlined />}
                placeholder="请输入登录密码"
                autoComplete="current-password"
                size="large"
              />
            </Form.Item>

            <Form.Item
              name="token"
              label={
                <span className="login-otp-label">
                  平安令牌
                  <Text type="secondary" className="login-otp-hint">
                    令牌上 6 位动态口令
                  </Text>
                </span>
              }
              className="login-otp-item"
              rules={[
                { required: true, message: '请输入 6 位平安令牌' },
                { pattern: /^\d{6}$/, message: '平安令牌须为 6 位数字' },
              ]}
              validateTrigger={['onSubmit']}
              getValueProps={(value) => ({ value: value ?? '' })}
              getValueFromEvent={(value: string) => value}
            >
              <OtpInput
                onComplete={() => form.submit()}
                disabled={submitting}
              />
            </Form.Item>

            <div className="login-meta">
              <Checkbox
                checked={remember}
                onChange={(e) => setRemember(e.target.checked)}
              >
                <span className="login-meta-text">保持登录 7 天</span>
              </Checkbox>
              <a className="login-meta-link" href="https://um.pingan.com" target="_blank" rel="noreferrer">
                忘记密码？
              </a>
            </div>

            <Button
              type="primary"
              htmlType="submit"
              loading={submitting}
              block
              size="large"
              className="login-submit"
            >
              登录
              <span className="login-submit-kbd">↵</span>
            </Button>
          </Form>
        </div>
      </section>
    </main>
  );
};

export default Login;
