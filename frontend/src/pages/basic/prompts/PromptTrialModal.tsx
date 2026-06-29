import { Alert, Button, Card, Col, Collapse, Divider, Empty, Input, Modal, Row, Select, Space, Spin, Statistic, Tag, Tooltip, Typography, message } from 'antd';
import { CheckCircleOutlined, CopyOutlined, ExclamationCircleOutlined, PlayCircleOutlined, SwapOutlined } from '@ant-design/icons';
import React, { useEffect, useMemo, useState } from 'react';
import type { PromptTestResult } from '../../../api/prompt';
import type { AiModel, Prompt } from '../../../types';
import {
  extractPlaceholders,
  formatVariableLabel,
  getModelOptionDisabled,
  substitutePlaceholders,
} from './utils';

const { Text, Paragraph } = Typography;

interface PromptTrialModalProps {
  open: boolean;
  promptTypeLabel: string;
  selectedPrompt: Prompt | null;
  models: AiModel[];
  selectedModelId?: number;
  sampleCode: string;
  running: boolean;
  result: PromptTestResult | null;
  onCancel: () => void;
  /**
   * 触发试跑。
   * - sampleCode: 用户在 UI 填的 Java 代码
   * - variables: 用户为占位符填的值（key 不含 `${}`）
   * - resolvedContent: 已把 variables 替换进 prompt.content 的最终字符串
   */
  onRun: (params: { sampleCode: string; variables: Record<string, string>; resolvedContent: string }) => void;
  onModelChange: (value?: number) => void;
  onSampleCodeChange: (value: string) => void;
}

const PromptTrialModal: React.FC<PromptTrialModalProps> = ({
  open,
  promptTypeLabel,
  selectedPrompt,
  models,
  selectedModelId,
  sampleCode,
  running,
  result,
  onCancel,
  onRun,
  onModelChange,
  onSampleCodeChange,
}) => {
  /** 用户在 UI 中填的占位符变量 */
  const [variables, setVariables] = useState<Record<string, string>>({});

  // 切换 prompt 时清空变量缓存
  useEffect(() => {
    setVariables({});
  }, [selectedPrompt?.id, selectedPrompt?.version]);

  /** 当前 prompt 的占位符列表（按出现顺序） */
  const placeholders = useMemo(
    () => extractPlaceholders(selectedPrompt?.content),
    [selectedPrompt?.content],
  );

  /** 把用户填的变量 + sampleCode 合成最终发送给 AI 的字符串 */
  const resolvedContent = useMemo(() => {
    if (!selectedPrompt) return '';
    const merged: Record<string, string> = { ...variables };
    // 默认把 sampleCode 当作 source_code(与后端 auto-fill 一致)
    if (sampleCode) merged.source_code = sampleCode;
    return substitutePlaceholders(selectedPrompt.content, merged);
  }, [selectedPrompt, variables, sampleCode]);

  const hasEmptyRequired = placeholders.some(
    (name) => !(variables[name] && variables[name].trim()) && !(name === 'source_code' && sampleCode),
  );

  const updateVariable = (name: string, value: string) => {
    setVariables((prev) => ({ ...prev, [name]: value }));
  };

  const handleRun = () => {
    if (!selectedPrompt) return;
    if (hasEmptyRequired) {
      message.warning('请先填写所有占位符变量（带 * 为必填）');
      return;
    }
    onRun({
      sampleCode,
      variables,
      resolvedContent,
    });
  };

  return (
    <Modal
      title={`${promptTypeLabel}试跑：${selectedPrompt?.name || ''} v${selectedPrompt?.version || ''}`}
      open={open}
      onCancel={onCancel}
      width={880}
      footer={null}
      destroyOnClose
    >
      {selectedPrompt ? (
        <Space direction="vertical" size={16} className="ci-prompt-trial-body">
          {/* 提示词模板正文（只读） */}
          <Collapse
            ghost
            defaultActiveKey={['template']}
            items={[
              {
                key: 'template',
                label: (
                  <div className="ci-prompt-collapse-label">
                    <Text strong>提示词模板正文</Text>
                    <Button
                      size="small"
                      type="text"
                      icon={<CopyOutlined />}
                      onClick={(event) => {
                        event.stopPropagation();
                        navigator.clipboard.writeText(selectedPrompt.content);
                        message.success('模板正文已复制');
                      }}
                    >
                      复制
                    </Button>
                  </div>
                ),
                children: (
                  <Input.TextArea
                    autoSize={{ minRows: 4, maxRows: 10 }}
                    className="ci-code-input"
                    value={selectedPrompt.content}
                    readOnly
                  />
                ),
              },
            ]}
          />

          {/* 占位符变量填入区 */}
          <Card
            size="small"
            title={
              <Space>
                <SwapOutlined />
                <span>占位符变量</span>
                {placeholders.length > 0 ? (
                  <Tag color="blue">{placeholders.length} 个</Tag>
                ) : (
                  <Tag>无占位符</Tag>
                )}
              </Space>
            }
            extra={
              placeholders.length > 0 && (
                <Tooltip title="用正则从模板中识别 ${var_name} 形式的占位符。点击「试跑」前请填写所有变量；source_code 默认取下方示例代码。">
                  <Text type="secondary" style={{ fontSize: 12 }}>使用说明</Text>
                </Tooltip>
              )
            }
          >
            {placeholders.length === 0 ? (
              <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                当前模板中没有占位符变量,直接点击下方「试跑」即可。
              </Paragraph>
            ) : (
              <Row gutter={[12, 8]}>
                {placeholders.map((name) => {
                  const isSourceCode = name === 'source_code';
                  const value = variables[name] ?? (isSourceCode ? sampleCode : '');
                  return (
                    <Col xs={24} md={12} key={name}>
                      <div className="ci-prompt-var-row">
                        <Text strong className="ci-prompt-var-label">
                          <code>{`\${${name}}`}</code>
                          <Text type="secondary" style={{ fontSize: 12, marginLeft: 4 }}>
                            {formatVariableLabel(name)}
                          </Text>
                        </Text>
                        <Input
                          placeholder={
                            isSourceCode ? '默认取下方「示例代码」,可在此覆盖' : '请输入变量值'
                          }
                          value={value}
                          onChange={(e) => updateVariable(name, e.target.value)}
                          status={!value && !isSourceCode ? 'warning' : undefined}
                          allowClear
                        />
                        {isSourceCode && (
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            未填时,使用下方「Java 示例代码」内容。
                          </Text>
                        )}
                      </div>
                    </Col>
                  );
                })}
              </Row>
            )}
          </Card>

          {/* 模型选择 */}
          <div className="ci-prompt-model-row">
            <Text strong className="ci-prompt-model-label">
              选择 AI 模型
            </Text>
            <Select
              className="ci-prompt-model-select"
              placeholder="请选择用于试跑的 AI 模型配置"
              options={models.map((model) => ({
                label: `${model.name} (${model.identifier})` + (model.isDefault === 'true' ? ' [系统默认]' : ''),
                value: model.id,
                disabled: getModelOptionDisabled(model),
              }))}
              value={selectedModelId}
              onChange={onModelChange}
              allowClear
            />
          </div>

          {/* 示例代码 */}
          <div>
            <Text strong className="ci-prompt-field-label">
              Java 示例代码
            </Text>
            <Input.TextArea
              autoSize={{ minRows: 6, maxRows: 16 }}
              className="ci-code-input"
              value={sampleCode}
              onChange={(event) => onSampleCodeChange(event.target.value)}
              placeholder="在此输入 Java 示例代码,作为 source_code 占位符的默认值"
            />
          </div>

          {/* 替换后的最终 prompt 预览 */}
          {placeholders.length > 0 && (
            <Collapse
              ghost
              items={[
                {
                  key: 'preview',
                  label: (
                    <Space>
                      <CheckCircleOutlined style={{ color: '#52c41a' }} />
                      <Text strong>替换后的最终 prompt（发送给 AI 的内容）</Text>
                    </Space>
                  ),
                  children: (
                    <Input.TextArea
                      autoSize={{ minRows: 4, maxRows: 14 }}
                      className="ci-code-input"
                      value={resolvedContent}
                      readOnly
                    />
                  ),
                },
              ]}
            />
          )}

          {hasEmptyRequired && (
            <Alert
              type="warning"
              showIcon
              icon={<ExclamationCircleOutlined />}
              message="存在未填写的占位符"
              description="请填写上方所有带 * 的变量后再次点击「试跑」。"
            />
          )}

          <Button
            type="primary"
            block
            icon={<PlayCircleOutlined />}
            loading={running}
            onClick={handleRun}
            disabled={hasEmptyRequired}
          >
            {running ? '正在流式输出...' : '开始试跑'}
          </Button>

          {running && !result?.result && (
            <div className="ci-empty-panel">
              <Spin tip="AI 正在分析..." />
            </div>
          )}

          {result && (
            <Space direction="vertical" size={12} className="ci-prompt-trial-result">
              <Divider className="ci-prompt-divider" />
              <Row gutter={12}>
                <Col span={8}>
                  <Card size="small">
                    <Statistic title="输入 Token" value={result.inputTokens} />
                  </Card>
                </Col>
                <Col span={8}>
                  <Card size="small">
                    <Statistic title="输出 Token" value={result.outputTokens} />
                  </Card>
                </Col>
                <Col span={8}>
                  <Card size="small">
                    <Statistic title="耗时" value={result.durationMs} suffix="ms" />
                  </Card>
                </Col>
              </Row>
              {result.errorReason ? (
                <Alert type="error" showIcon message="分析失败" description={result.errorReason} />
              ) : (
                <div className="ci-editor-shell ci-prompt-result-shell">
                  <div className="ci-prompt-result-header">
                    <Text strong className="ci-prompt-result-title">
                      AI 归纳结果输出
                    </Text>
                    <Button
                      size="small"
                      icon={<CopyOutlined />}
                      onClick={() => {
                        navigator.clipboard.writeText(result.result);
                        message.success('试跑结果已复制到剪贴板');
                      }}
                    >
                      复制结果
                    </Button>
                  </div>
                  <pre className={`ci-result-preview ci-prompt-result-preview${running ? ' ci-prompt-result-preview-streaming' : ''}`}>{result.result}</pre>
                </div>
              )}
            </Space>
          )}
        </Space>
      ) : (
        <Empty description="请选择一个提示词模板，并使用示例 Java 代码进行试跑。" />
      )}
    </Modal>
  );
};

export default PromptTrialModal;
