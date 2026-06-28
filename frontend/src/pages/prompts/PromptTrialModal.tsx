import { Alert, Button, Card, Col, Collapse, Divider, Empty, Input, Modal, Row, Select, Space, Spin, Statistic, Typography, message } from 'antd';
import { CopyOutlined, InfoCircleOutlined, PlayCircleOutlined } from '@ant-design/icons';
import type React from 'react';
import type { PromptTestResult } from '../../api/prompt';
import type { AiModel, Prompt } from '../../types';
import { getModelOptionDisabled } from './utils';

const { Text } = Typography;

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
  onRun: () => void;
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
}) => (
  <Modal
    title={`${promptTypeLabel}试跑：${selectedPrompt?.name || ''} v${selectedPrompt?.version || ''}`}
    open={open}
    onCancel={onCancel}
    width={800}
    footer={null}
    destroyOnClose
  >
    {selectedPrompt ? (
      <Space direction="vertical" size={16} className="ci-prompt-trial-body">
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
              children: <Input.TextArea autoSize={{ minRows: 4, maxRows: 12 }} className="ci-code-input" value={selectedPrompt.content} readOnly />,
            },
          ]}
        />
        <Alert
          type="info"
          showIcon
          icon={<InfoCircleOutlined />}
          message="支持的变量"
          description={
            <Space direction="vertical" size={2}>
              <Text code>{'${class_name}'}</Text>
              <Text code>{'${method_name}'}</Text>
              <Text code>{'${source_code}'}</Text>
            </Space>
          }
        />
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
        <div>
          <Text strong className="ci-prompt-field-label">
            Java 示例代码
          </Text>
          <Input.TextArea
            autoSize={{ minRows: 9, maxRows: 18 }}
            className="ci-code-input"
            value={sampleCode}
            onChange={(event) => onSampleCodeChange(event.target.value)}
          />
        </div>
        <Button type="primary" block icon={<PlayCircleOutlined />} loading={running} onClick={onRun}>
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

export default PromptTrialModal;
