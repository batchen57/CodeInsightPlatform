import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Modal,
  Row,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
  message,
} from 'antd';
import { ArrowRightOutlined, SwapOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { listPrompts, setDefaultPrompt } from '../../../api/prompt';
import type { Prompt } from '../../../types';

const { Text, Title, Paragraph } = Typography;

const PROMPT_TYPES: { key: string; label: string; description: string }[] = [
  {
    key: 'MODULARIZE',
    label: '模块提取提示词',
    description: 'AI_ANALYZING / MODULE_HIERARCHY 阶段使用',
  },
  {
    key: 'DOCUMENT_GENERATION',
    label: '文档生成提示词',
    description: 'GENERATING_DOC 阶段使用',
  },
];

/**
 * 「默认提示词维护」：每个 promptType 一个卡片，仅显示当前默认项，
 * 提供"更换默认"下拉选择（列出该类型下所有启用的非默认提示词）。
 */
const DefaultPromptsPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [prompts, setPrompts] = useState<Prompt[]>([]);
  const [changeOpen, setChangeOpen] = useState<{ type: string; currentDefaultId: number | null } | null>(null);
  const [pendingPromptId, setPendingPromptId] = useState<number | undefined>();
  const [submitting, setSubmitting] = useState(false);

  const fetchAll = useCallback(async () => {
    setLoading(true);
    try {
      const all: Prompt[] = [];
      // 每个 promptType 拉一次；只取启用 + 全部（前端再过滤）
      for (const t of PROMPT_TYPES) {
        const res = await listPrompts({ current: 1, size: 200, status: 1, promptType: t.key });
        all.push(...res.records);
      }
      setPrompts(all);
    } catch {
      // 拦截器已提示
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAll();
  }, [fetchAll]);

  const getDefault = (type: string) => prompts.find((p) => p.promptType === type && p.isDefault === 1) || null;
  const getCandidates = (type: string, currentDefaultId: number | null) =>
    prompts.filter((p) => p.promptType === type && p.status === 1 && p.id !== currentDefaultId);

  const handleChange = (type: string) => {
    const current = getDefault(type);
    setChangeOpen({ type, currentDefaultId: current?.id ?? null });
    setPendingPromptId(undefined);
  };

  const handleConfirmChange = async () => {
    if (!changeOpen || !pendingPromptId) {
      message.warning('请选择要设为默认的提示词');
      return;
    }
    setSubmitting(true);
    try {
      await setDefaultPrompt(pendingPromptId);
      message.success('默认提示词已更新');
      setChangeOpen(null);
      await fetchAll();
    } catch {
      // ignore
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="ci-page ci-default-prompts-page">
      <Card
        title={
          <Space>
            <span>默认提示词维护</span>
            <Link to="/basic/prompts">
              <Button type="link" size="small">
                前往完整提示词库 <ArrowRightOutlined />
              </Button>
            </Link>
          </Space>
        }
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="每个 promptType 仅允许一条默认；后端通过唯一索引强制约束。"
        />
        <Spin spinning={loading}>
          <Row gutter={[16, 16]}>
            {PROMPT_TYPES.map((t) => {
              const current = getDefault(t.key);
              return (
                <Col key={t.key} xs={24} md={12}>
                  <Card
                    type="inner"
                    title={
                      <Space>
                        <Tag color="purple">{t.label}</Tag>
                        <Text type="secondary" style={{ fontSize: 12 }}>
                          {t.description}
                        </Text>
                      </Space>
                    }
                    extra={
                      <Button
                        type="primary"
                        icon={<SwapOutlined />}
                        onClick={() => handleChange(t.key)}
                      >
                        更换默认
                      </Button>
                    }
                  >
                    {current ? (
                      <Space direction="vertical" size={6} style={{ width: '100%' }}>
                        <Title level={5} style={{ margin: 0 }}>
                          <Space>
                            {current.name}
                            <Tag color="gold">默认</Tag>
                            <Tag>v{current.version}</Tag>
                          </Space>
                        </Title>
                        <Paragraph
                          type="secondary"
                          ellipsis={{ rows: 4, expandable: true, symbol: '展开' }}
                          style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}
                        >
                          {current.content}
                        </Paragraph>
                      </Space>
                    ) : (
                      <Empty
                        description={`该类型尚无默认提示词；新建提示词时勾选「默认」即可`}
                        style={{ padding: '12px 0' }}
                      />
                    )}
                  </Card>
                </Col>
              );
            })}
          </Row>
        </Spin>
      </Card>

      <Modal
        title={
          changeOpen
            ? `更换默认：${PROMPT_TYPES.find((t) => t.key === changeOpen.type)?.label}`
            : '更换默认'
        }
        open={!!changeOpen}
        onCancel={() => setChangeOpen(null)}
        onOk={handleConfirmChange}
        okText="设为默认"
        cancelText="取消"
        confirmLoading={submitting}
        okButtonProps={{ disabled: !pendingPromptId }}
      >
        {changeOpen && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              选择该类型下任意一个启用中的提示词作为新默认。
              其他默认会被自动取消。
            </Text>
            <Select
              style={{ width: '100%' }}
              placeholder="选择提示词"
              value={pendingPromptId}
              onChange={setPendingPromptId}
              options={getCandidates(changeOpen.type, changeOpen.currentDefaultId).map((p) => ({
                value: p.id,
                label: `${p.name} (v${p.version})${p.isDefault === 1 ? ' · 当前默认' : ''}`,
              }))}
              notFoundContent="该类型下暂无可切换的提示词，请先在提示词库中新建"
              showSearch
              optionFilterProp="label"
            />
          </Space>
        )}
      </Modal>
    </div>
  );
};

export default DefaultPromptsPage;
