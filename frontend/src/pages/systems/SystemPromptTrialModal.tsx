import React, { useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Input,
  Modal,
  Select,
  Skeleton,
  Space,
  Statistic,
  Tag,
  Typography,
  message,
} from 'antd';
import { PlayCircleOutlined } from '@ant-design/icons';
import { listModels } from '../../api/model';
import { testRunPrompt, type PromptTestResult } from '../../api/prompt';
import type { AiModel, Prompt } from '../../types';

const { Text, Paragraph } = Typography;

interface Props {
  open: boolean;
  /** 要试跑的 prompt(可绑定在系统上的) */
  prompt: Prompt | null;
  /** 提示词类型的中文标签 */
  promptTypeLabel?: string;
  /** 关闭 */
  onClose: () => void;
}

const DEFAULT_SAMPLE = `public class OrderService {
  private final OrderMapper orderMapper;
  public void createOrder(Order order) {
    orderMapper.insert(order);
  }
}`;

/**
 * 「系统向导 → 试跑」轻量弹窗
 *
 * 区别于 PromptTrialModal（受控 + 完整占位符提取 + 全量状态），
 * 这里走最简路径：让用户填一段 Java 代码 → 直接调 testRunPrompt → 展示结果。
 * 主要用于新建系统向导里快速验证所选提示词效果。
 */
const SystemPromptTrialModal: React.FC<Props> = ({ open, prompt, promptTypeLabel, onClose }) => {
  const [sampleCode, setSampleCode] = useState(DEFAULT_SAMPLE);
  const [models, setModels] = useState<AiModel[]>([]);
  const [modelId, setModelId] = useState<number | undefined>(undefined);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<PromptTestResult | null>(null);

  // 打开时拉取模型 + 重置结果
  useEffect(() => {
    if (!open) return;
    setResult(null);
    setRunning(false);
    listModels().then((list) => {
      setModels(list);
      const def = list.find((m) => m.isDefault === 'true' || (m as any).isDefault === true);
      if (def) setModelId(def.id);
    }).catch(() => {
      // 拦截器已提示
    });
  }, [open]);

  const handleRun = async () => {
    if (!prompt) {
      message.error('未选择提示词');
      return;
    }
    if (!sampleCode.trim()) {
      message.warning('请输入一段示例 Java 代码');
      return;
    }
    setRunning(true);
    setResult(null);
    try {
      const r = await testRunPrompt(prompt.id, sampleCode, modelId, prompt.content);
      setResult(r);
      if (r.errorReason) message.error('试跑失败');
      else message.success('试跑完成');
    } catch {
      // 拦截器已提示
    } finally {
      setRunning(false);
    }
  };

  return (
    <Modal
      title={
        <Space>
          <PlayCircleOutlined />
          试跑 · {promptTypeLabel ?? '提示词'} · {prompt?.name ?? ''}
        </Space>
      }
      open={open}
      onCancel={onClose}
      width={820}
      destroyOnClose
      footer={[
        <Button key="cancel" onClick={onClose}>
          关闭
        </Button>,
        <Button
          key="run"
          type="primary"
          icon={<PlayCircleOutlined />}
          loading={running}
          onClick={handleRun}
        >
          开始试跑
        </Button>,
      ]}
    >
      {!prompt ? (
        <Alert type="warning" message="未选择提示词" />
      ) : (
        <>
          <Space size={12} wrap style={{ marginBottom: 12 }}>
            <Text type="secondary">模型:</Text>
            <Select
              placeholder="选择模型（默认=系统默认）"
              value={modelId}
              onChange={(v) => setModelId(v)}
              allowClear
              style={{ width: 280 }}
              options={models.map((m) => ({ value: m.id, label: m.name }))}
              loading={models.length === 0}
            />
            <Tag color="blue">v{prompt.version}</Tag>
            {prompt.isDefault === 1 && <Tag color="gold">默认</Tag>}
          </Space>

          <Paragraph type="secondary" style={{ marginTop: 0, fontSize: 12 }}>
            提示词正文已绑定（可在编辑器中调整），下面填一段示例 Java 代码即可发起试跑。
          </Paragraph>

          <Input.TextArea
            value={sampleCode}
            onChange={(e) => setSampleCode(e.target.value)}
            rows={10}
            style={{ fontFamily: 'monospace', fontSize: 12 }}
            spellCheck={false}
          />

          {running && (
            <div style={{ marginTop: 16 }}>
              <Skeleton active paragraph={{ rows: 4 }} />
            </div>
          )}

          {result && !running && (
            <div style={{ marginTop: 16 }}>
              <Space size={24} wrap style={{ marginBottom: 12 }}>
                <Statistic
                  title="Input Tokens"
                  value={result.inputTokens}
                  valueStyle={{ fontSize: 14 }}
                />
                <Statistic
                  title="Output Tokens"
                  value={result.outputTokens}
                  valueStyle={{ fontSize: 14 }}
                />
                <Statistic
                  title="耗时 (ms)"
                  value={result.durationMs}
                  valueStyle={{ fontSize: 14 }}
                />
              </Space>
              {result.errorReason ? (
                <Alert type="error" message="试跑失败" description={result.errorReason} />
              ) : (
                <div
                  style={{
                    background: '#fafafa',
                    border: '1px solid #e8e8e8',
                    borderRadius: 6,
                    padding: 12,
                    maxHeight: 320,
                    overflow: 'auto',
                  }}
                >
                  <pre style={{ whiteSpace: 'pre-wrap', margin: 0, fontSize: 12 }}>
                    {result.result}
                  </pre>
                </div>
              )}
            </div>
          )}
        </>
      )}
    </Modal>
  );
};

export default SystemPromptTrialModal;
