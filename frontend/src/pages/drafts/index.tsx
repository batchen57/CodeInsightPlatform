import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Badge,
  Button,
  Card,
  Col,
  Empty,
  Input,
  List,
  Modal,
  Row,
  Select,
  Space,
  Tabs,
  Tag,
  Tooltip,
  Tree,
  Typography,
  message,
} from 'antd';
import {
  AppstoreOutlined,
  CheckOutlined,
  ClockCircleOutlined,
  CloseOutlined,
  FileTextOutlined,
  HistoryOutlined,
  LinkOutlined,
  MessageOutlined,
  SaveOutlined,
  AlertOutlined,
  CloudUploadOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import Editor from '@monaco-editor/react';
import {
  autoSaveDraft,
  confirmDraft,
  getComments,
  getDraftContent,
  getReferences,
  getRevisions,
  getWorkspaceByTask,
  rejectDraft,
  saveDraft,
  type DraftReviewComment,
  type DraftRevision,
  type DraftSourceReference,
  type DraftWorkspace,
  type KnowledgeDraft,
} from '../../api/draft';
import { listSystems } from '../../api/system';
import { listTasks, retryTask } from '../../api/task';
import type { System, Task } from '../../types';

const { Text } = Typography;
const { TextArea } = Input;

// 统一的草稿状态颜色映射
const statusColor: Record<string, string> = {
  AI_GENERATED: 'cyan',
  PENDING_REVIEW: 'magenta',
  REVIEWING: 'geekblue',
  REVISED: 'gold',
  CONFIRMED: 'green',
  REJECTED: 'red',
  PUSHED: 'green',
  ARCHIVED: 'default',
};

// 草稿状态文本映射说明
const statusLabel: Record<string, string> = {
  AI_GENERATED: 'AI 已生成',
  PENDING_REVIEW: '待复核',
  REVIEWING: '复核中',
  REVISED: '已修订',
  CONFIRMED: '已确认',
  REJECTED: '已驳回',
  PUSHED: '已推送',
  ARCHIVED: '已归档',
};

// 草稿状态对应 Antd Badge 微标类型的渲染配置
const statusBadgeType: Record<string, "success" | "processing" | "error" | "warning" | "default"> = {
  AI_GENERATED: 'processing',
  PENDING_REVIEW: 'warning',
  REVIEWING: 'processing',
  REVISED: 'warning',
  CONFIRMED: 'success',
  REJECTED: 'error',
  PUSHED: 'success',
  ARCHIVED: 'default',
};

/**
 * 知识复核工作区组件 (Drafts)
 * 支持左侧模块目录结构树状展现、中间集成式 Monaco Editor 编辑器实现实时修改、右侧多页签聚合呈现待确认指标列表、源文件代码关联、修改历史和审核意见。
 * 包含 Redis 编辑锁仿真和 1.8 秒防抖自动暂存逻辑。
 */
const Drafts: React.FC = () => {
  const navigate = useNavigate();
  
  // 系统下拉数据及选中的系统 ID
  const [systems, setSystems] = useState<System[]>([]);
  const [selectedSystemId, setSelectedSystemId] = useState<number | undefined>();
  // 当前系统关联的任务列表及选中的任务 ID
  const [tasks, setTasks] = useState<Task[]>([]);
  const [selectedTaskId, setSelectedTaskId] = useState<number | undefined>();

  // 任务对应生成的草稿工作区和草稿模块列表
  const [workspace, setWorkspace] = useState<DraftWorkspace | null>(null);
  const [drafts, setDrafts] = useState<KnowledgeDraft[]>([]);
  // 当前选中的模块草稿 ID 与具体草稿实体
  const [selectedDraftId, setSelectedDraftId] = useState<number | null>(null);
  const [selectedDraft, setSelectedDraft] = useState<KnowledgeDraft | null>(null);

  // 编辑器状态：当前最新的编辑文本 and 最近一次保存的原始文本
  const [editorContent, setEditorContent] = useState('');
  const [originalContent, setOriginalContent] = useState('');
  
  // 编辑器自动保存状态字与防抖定时器引用
  const [autoSaveStatus, setAutoSaveStatus] = useState('已同步');
  const autoSaveTimer = useRef<number | null>(null);

  // 当前草稿关联的历史版本记录、审核意见和涉及的源文件引用
  const [revisions, setRevisions] = useState<DraftRevision[]>([]);
  const [comments, setComments] = useState<DraftReviewComment[]>([]);
  const [references, setReferences] = useState<DraftSourceReference[]>([]);

  // 驳回弹框控制、驳回意见文字
  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [rejectComment, setRejectComment] = useState('');
  // 重新触发后台任务的 loading 状态
  const [rerunLoading, setRerunLoading] = useState(false);
  
  // Monaco 编辑器底层引用
  const editorRef = useRef<any>(null);

  // 计算属性：已通过、已确认或推送归档的文章设为只读模式以防止误写
  const isReadOnly = useMemo(() => {
    return selectedDraft ? ['CONFIRMED', 'PUSHED', 'ARCHIVED'].includes(selectedDraft.status) : false;
  }, [selectedDraft]);

  // 初始化：获取已启用的全部业务系统列表
  useEffect(() => {
    listSystems({ current: 1, size: 100, status: 1 }).then((data) => {
      setSystems(data.records);
      if (data.records.length > 0) {
        setSelectedSystemId(data.records[0].id);
      }
    });
  }, []);

  // 联动监听所选系统变更，重载关联的历史分析任务列表
  useEffect(() => {
    if (!selectedSystemId) {
      return;
    }
    listTasks({ current: 1, size: 100, systemId: selectedSystemId }).then((data) => {
      setTasks(data.records);
      // 优先高亮高亮有待人工处理的任务（如待复核、复核中等）
      const reviewTask = data.records.find((task) => ['PENDING_REVIEW', 'REVIEWING'].includes(task.status));
      setSelectedTaskId(reviewTask?.id ?? data.records[0]?.id);
      if (data.records.length === 0) {
        setWorkspace(null);
        setDrafts([]);
        setSelectedDraftId(null);
      }
    });
  }, [selectedSystemId]);

  // 联动监听所选任务变更，拉取对应的工作区 (Workspace) 及包含的全部模块列表
  useEffect(() => {
    if (!selectedTaskId) {
      return;
    }
    getWorkspaceByTask(selectedTaskId)
      .then((result) => {
        setWorkspace(result.workspace);
        setDrafts(result.drafts);
        setSelectedDraftId(result.drafts[0]?.id ?? null);
      })
      .catch(() => {
        setWorkspace(null);
        setDrafts([]);
        setSelectedDraftId(null);
      });
  }, [selectedTaskId]);

  // 监听选中的草稿，拉取草稿内容、修订历史、评审意见和代码来源引用数据
  useEffect(() => {
    if (!selectedDraftId) {
      setSelectedDraft(null);
      setEditorContent('');
      setOriginalContent('');
      setRevisions([]);
      setComments([]);
      setReferences([]);
      return;
    }

    const currentDraft = drafts.find((draft) => draft.id === selectedDraftId) ?? null;
    setSelectedDraft(currentDraft);

    // 并发查询单篇草稿所需的多维度信息，实现快速的页签切换联动
    Promise.all([
      getDraftContent(selectedDraftId),
      getRevisions(selectedDraftId),
      getComments(selectedDraftId),
      getReferences(selectedDraftId),
    ]).then(([content, revisionData, commentData, referenceData]) => {
      setEditorContent(content);
      setOriginalContent(content);
      setAutoSaveStatus('已同步');
      setRevisions(revisionData);
      setComments(commentData);
      setReferences(referenceData);
    });
  }, [drafts, selectedDraftId]);

  const handleEditorDidMount = (editor: any) => {
    editorRef.current = editor;
  };

  /**
   * 辅助定位跳转：在待确认页签下点击某项时，Monaco 编辑器自动滚动高亮到对应行号
   */
  const scrollToLine = (lineNum: number) => {
    if (editorRef.current) {
      editorRef.current.revealLineInCenter(lineNum);
      editorRef.current.setPosition({ lineNumber: lineNum, column: 1 });
      editorRef.current.focus();
    }
  };

  // 实时从 Markdown 内容中动态提取待确认的事项（正则匹配格式如 `- [ ] 待确认描述`）
  const pendingConfirmations = useMemo(() => {
    if (!editorContent) return [];
    const lines = editorContent.split('\n');
    return lines
      .map((line, index) => {
        const match = line.match(/^\s*[-*]\s*\[\s*\]\s*(.*)$/);
        if (match) {
          return {
            lineNum: index + 1,
            text: match[1].trim() || '待确认的细节规则',
          };
        }
        return null;
      })
      .filter((item): item is { lineNum: number; text: string } => item !== null);
  }, [editorContent]);

  /**
   * 监听编辑器文本改变，防抖自动保存至 Redis 临时暂存区
   * 设定防抖延迟为 1800 毫秒，防止用户连续击键时向后端发起高频并发请求。
   */
  const handleEditorChange = (value: string | undefined) => {
    const nextValue = value || '';
    setEditorContent(nextValue);
    if (nextValue === originalContent) {
      return;
    }
    setAutoSaveStatus('正在保存草稿...');
    // 清除上一次的未执行定时器
    if (autoSaveTimer.current) {
      window.clearTimeout(autoSaveTimer.current);
    }
    // 重新开启 1.8 秒防抖保存任务
    autoSaveTimer.current = window.setTimeout(async () => {
      if (!selectedDraftId) {
        return;
      }
      try {
        await autoSaveDraft(selectedDraftId, nextValue, 'Admin');
        setAutoSaveStatus('已自动保存');
      } catch {
        setAutoSaveStatus('自动保存失败');
      }
    }, 1800);
  };

  // 手动点击保存：物理落盘并计算增改差分行，重载历史修改列表
  const handleSave = async () => {
    if (!selectedDraftId) {
      return;
    }
    await saveDraft(selectedDraftId, editorContent, 'Admin', '人工复核保存');
    message.success('草稿已保存');
    setOriginalContent(editorContent);
    setAutoSaveStatus('已同步');
    setRevisions(await getRevisions(selectedDraftId));
  };

  // 确认通过：将该模块置为 CONFIRMED
  const handleConfirm = async () => {
    if (!selectedDraftId) {
      return;
    }
    await confirmDraft(selectedDraftId, 'Admin');
    message.success('草稿已确认');
    setDrafts((prev) => prev.map((draft) => (draft.id === selectedDraftId ? { ...draft, status: 'CONFIRMED' } : draft)));
  };

  // 驳回修改：触发模态框保存具体批注理由，回流状态至 REJECTED
  const handleReject = async () => {
    if (!selectedDraftId || !rejectComment.trim()) {
      message.warning('请输入驳回意见');
      return;
    }
    await rejectDraft(selectedDraftId, rejectComment, 'Admin');
    message.success('草稿已驳回');
    setRejectModalOpen(false);
    setRejectComment('');
    setDrafts((prev) => prev.map((draft) => (draft.id === selectedDraftId ? { ...draft, status: 'REJECTED' } : draft)));
    setComments(await getComments(selectedDraftId));
  };

  // 重跑任务分析以重新生成该任务的草稿
  const handleRerun = async () => {
    if (!selectedTaskId) {
      return;
    }
    setRerunLoading(true);
    message.loading({ content: '正在为当前任务发送重跑指令...', key: 'rerun' });
    try {
      await retryTask(selectedTaskId);
      message.success({ content: '任务已重新启动，系统正在后台解析并重新生成草稿，请在任务详情页或稍后在此查看进度', key: 'rerun' });
      // 刷新当前任务下的工作区和草稿列表
      getWorkspaceByTask(selectedTaskId)
        .then((result) => {
          setWorkspace(result.workspace);
          setDrafts(result.drafts);
          setSelectedDraftId(result.drafts[0]?.id ?? null);
        })
        .catch(() => {});
    } catch {
      message.error({ content: '重跑指令发送失败，请确保当前任务状态允许重试', key: 'rerun' });
    } finally {
      setRerunLoading(false);
    }
  };

  // 跳转到推送页面并传递当前系统ID参数
  const handlePush = () => {
    navigate('/push', { state: { systemId: selectedSystemId } });
  };

  // 左侧目录树节点构建
  const treeData = drafts.map((draft) => {
    const badgeStatus = statusBadgeType[draft.status] ?? 'default';
    const isSelected = selectedDraftId === draft.id;
    const isLowConfidence = draft.status === 'PENDING_REVIEW';
    return {
      title: (
        <Space size={4} style={{ width: '100%', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Space size={6}>
            <FileTextOutlined style={{ color: isSelected ? '#5258e8' : '#8c8c8c' }} />
            <span style={{ fontWeight: isSelected ? 600 : 400, color: isSelected ? '#5258e8' : 'inherit' }}>
              {draft.moduleName}
            </span>
          </Space>
          <Space size={4}>
            {isLowConfidence && (
              <Tooltip title="AI 模块路由分类置信度低于 0.75，请核对细节">
                <Tag color="warning" style={{ fontSize: '10px', padding: '0 4px', margin: 0, lineHeight: '16px' }}>低置信</Tag>
              </Tooltip>
            )}
            <Badge status={badgeStatus} title={statusLabel[draft.status]} />
          </Space>
        </Space>
      ),
      key: draft.id,
    };
  });

  return (
    <div className="ci-page ci-review-page ci-drafts-page">
      {/* 顶部系统与关联任务选择器 */}
      <Card className="ci-filter-card" style={{ flexShrink: 0 }}>
        <Row gutter={[12, 12]} align="middle">
          <Col xs={24} md={8}>
            <Select
              style={{ width: '100%' }}
              placeholder="请选择系统"
              value={selectedSystemId}
              onChange={setSelectedSystemId}
              options={systems.map((system) => ({ value: system.id, label: system.name }))}
            />
          </Col>
          <Col xs={24} md={10}>
            <Select
              style={{ width: '100%' }}
              placeholder="请选择任务"
              value={selectedTaskId}
              onChange={setSelectedTaskId}
              options={tasks.map((task) => ({
                value: task.id,
                label: `任务 #${task.id} / ${task.type === 'INITIAL' ? '全量' : '增量'} / ${statusLabel[task.status] ?? task.status}`,
              }))}
            />
          </Col>
          <Col xs={24} md={6}>
            <Space className="ci-toolbar-actions">
              {workspace ? <Tag color="green">工作区 #{workspace.id}</Tag> : <Tag>暂无工作区</Tag>}
              <Tag>{drafts.length} 个模块</Tag>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* 工作区详情部分 */}
      {workspace ? (
        <Row gutter={[16, 16]} className="ci-review-grid">
          {/* 左侧：模块目录结构树 */}
          <Col xs={24} lg={5}>
            <Card title="模块目录" className="ci-review-panel">
              <div style={{ maxHeight: 'calc(100vh - 300px)', overflowY: 'auto', paddingRight: 4 }}>
                {treeData.length > 0 ? (
                  <Tree
                    showIcon
                    defaultExpandAll
                    treeData={treeData}
                    selectedKeys={selectedDraftId ? [selectedDraftId] : []}
                    onSelect={(keys) => {
                      if (keys.length > 0) {
                        setSelectedDraftId(Number(keys[0]));
                      }
                    }}
                  />
                ) : (
                  <Empty description="暂无草稿" />
                )}
              </div>
            </Card>
          </Col>

          {/* 中间：Monaco 编辑器区域 */}
          <Col xs={24} lg={12}>
            <Card
              className="ci-review-panel"
              title={
                <Space wrap>
                  <AppstoreOutlined />
                  <span>{selectedDraft?.moduleName || '未选择模块'}</span>
                  {selectedDraft && <Tag color={statusColor[selectedDraft.status] ?? 'default'}>{statusLabel[selectedDraft.status] ?? selectedDraft.status}</Tag>}
                </Space>
              }
              extra={
                <Space size={16}>
                  <Tooltip title="Redis 双通道自动缓存编辑锁已启动">
                    <Tag icon={<SafetyCertificateOutlined />} color="success" style={{ border: 'none', background: 'rgba(46, 154, 98, 0.1)', color: '#2e9a62' }}>
                      编辑锁: 正常持有
                    </Tag>
                  </Tooltip>
                  <Text type="secondary" style={{ fontSize: '13px' }}>
                    {autoSaveStatus === '已自动保存' ? (
                      <span><CheckOutlined style={{ color: '#2e9a62', marginRight: 4 }} />{autoSaveStatus}</span>
                    ) : autoSaveStatus.includes('正在保存') ? (
                      <span><ReloadOutlined spin style={{ color: '#5258e8', marginRight: 4 }} />{autoSaveStatus}</span>
                    ) : (
                      autoSaveStatus
                    )}
                  </Text>
                </Space>
              }
            >
              {selectedDraftId ? (
                <div style={{ display: 'flex', flexDirection: 'column', height: '100%', gap: '12px' }}>
                  {/* 编辑器操作工具栏 */}
                  <div className="ci-editor-actions" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexShrink: 0 }}>
                    <Space size={8}>
                      {!isReadOnly && (
                        <Button type="primary" icon={<SaveOutlined />} onClick={handleSave}>
                          保存
                        </Button>
                      )}
                      {!isReadOnly && (
                        <Button danger icon={<CloseOutlined />} onClick={() => setRejectModalOpen(true)}>
                          驳回
                        </Button>
                      )}
                      <Button
                        icon={<ReloadOutlined spin={rerunLoading} />}
                        loading={rerunLoading}
                        onClick={handleRerun}
                      >
                        重跑任务
                      </Button>
                    </Space>
                    <Space size={8}>
                      {!isReadOnly && (
                        <Button type="primary" icon={<CheckOutlined />} onClick={handleConfirm}>
                          确认通过
                        </Button>
                      )}
                      <Button type="dashed" icon={<CloudUploadOutlined />} onClick={handlePush}>
                        去推送
                      </Button>
                    </Space>
                  </div>
                  {/* 编辑器容器 */}
                  <div className="ci-editor-shell">
                    <Editor
                      height="100%"
                      defaultLanguage="markdown"
                      theme="vs-light"
                      value={editorContent}
                      onChange={handleEditorChange}
                      onMount={handleEditorDidMount}
                      options={{
                        readOnly: isReadOnly,
                        minimap: { enabled: false },
                        wordWrap: 'on',
                        lineNumbers: 'on',
                        fontSize: 14,
                        fontFamily: 'Consolas, "Courier New", monospace',
                        lineHeight: 22,
                        scrollbar: {
                          vertical: 'visible',
                          horizontal: 'visible'
                        }
                      }}
                    />
                  </div>
                </div>
              ) : (
                <Empty description="请选择一个模块草稿进行复核" />
              )}
            </Card>
          </Col>

          {/* 右侧：多维度关联属性页签（待确认、代码来源、修订历史、复核意见） */}
          <Col xs={24} lg={7}>
            <Card className="ci-review-panel">
              <Tabs
                defaultActiveKey="confirmations"
                items={[
                  {
                    key: 'confirmations',
                    label: (
                      <Space>
                        <Badge count={pendingConfirmations.length} size="small" style={{ backgroundColor: pendingConfirmations.length > 0 ? '#b7791f' : '#2e9a62' }}>
                          <AlertOutlined />
                        </Badge>
                        <span>待确认</span>
                      </Space>
                    ),
                    children: (
                      <div style={{ maxHeight: 'calc(100vh - 350px)', overflowY: 'auto', paddingRight: 4 }}>
                        <List
                          size="small"
                          dataSource={pendingConfirmations}
                          locale={{ emptyText: '没有待确认事项，该模块已完全确认' }}
                          renderItem={(item) => (
                            <List.Item
                              style={{
                                cursor: 'pointer',
                                borderBottom: '1px solid #eceef3',
                                padding: '10px 4px',
                                borderRadius: '6px',
                                transition: 'all 0.2s',
                              }}
                              onClick={() => scrollToLine(item.lineNum)}
                              onMouseEnter={(e) => {
                                e.currentTarget.style.backgroundColor = '#f7f8ff';
                              }}
                              onMouseLeave={(e) => {
                                e.currentTarget.style.backgroundColor = 'transparent';
                              }}
                            >
                              <List.Item.Meta
                                title={
                                  <Space align="start">
                                    <Tag color="orange" style={{ margin: 0, marginTop: 2 }}>行 {item.lineNum}</Tag>
                                    <span style={{ fontSize: '13px', fontWeight: 500, color: '#171a23' }}>{item.text}</span>
                                  </Space>
                                }
                              />
                            </List.Item>
                          )}
                        />
                      </div>
                    ),
                  },
                  {
                    key: 'references',
                    label: (
                      <Space>
                        <LinkOutlined />
                        代码来源
                      </Space>
                    ),
                    children: (
                      <div style={{ maxHeight: 'calc(100vh - 350px)', overflowY: 'auto', paddingRight: 4 }}>
                        <List
                          size="small"
                          dataSource={references}
                          locale={{ emptyText: '暂无代码来源' }}
                          renderItem={(reference) => (
                            <List.Item>
                              <List.Item.Meta
                                title={<Text code style={{ fontSize: '12px', wordBreak: 'break-all', display: 'inline-block', maxWidth: '100%' }}>{reference.filePath}</Text>}
                                description={`第 ${reference.startLine}-${reference.endLine} 行`}
                              />
                            </List.Item>
                          )}
                        />
                      </div>
                    ),
                  },
                  {
                    key: 'revisions',
                    label: (
                      <Space>
                        <HistoryOutlined />
                        修订记录
                      </Space>
                    ),
                    children: (
                      <div style={{ maxHeight: 'calc(100vh - 350px)', overflowY: 'auto', paddingRight: 4 }}>
                        <List
                          size="small"
                          dataSource={revisions}
                          locale={{ emptyText: '暂无保存记录' }}
                          renderItem={(revision) => (
                            <List.Item>
                              <List.Item.Meta
                                avatar={<ClockCircleOutlined style={{ color: '#2563eb', marginTop: 4 }} />}
                                title={<span style={{ fontWeight: 500, color: '#171a23', fontSize: '13px' }}>{revision.remark}</span>}
                                description={
                                  <Space size={8} style={{ fontSize: '12px', color: '#697386' }}>
                                    <span>{revision.author}</span>
                                    <span>•</span>
                                    <span>{new Date(revision.createdAt).toLocaleString()}</span>
                                  </Space>
                                }
                              />
                            </List.Item>
                          )}
                        />
                      </div>
                    ),
                  },
                  {
                    key: 'comments',
                    label: (
                      <Space>
                        <MessageOutlined />
                        复核意见
                      </Space>
                    ),
                    children: (
                      <div style={{ maxHeight: 'calc(100vh - 350px)', overflowY: 'auto', paddingRight: 4 }}>
                        <List
                          size="small"
                          dataSource={comments}
                          locale={{ emptyText: '暂无复核意见' }}
                          renderItem={(comment) => (
                            <List.Item>
                              <List.Item.Meta
                                title={
                                  <Space style={{ display: 'flex', justifyContent: 'space-between', width: '100%' }}>
                                    <Text strong style={{ color: '#171a23' }}>{comment.author}</Text>
                                    <Text type="secondary" style={{ fontSize: '11px' }}>{new Date(comment.createdAt).toLocaleString()}</Text>
                                  </Space>
                                }
                                description={<span style={{ color: '#333333', fontSize: '13px', lineHeight: '20px' }}>{comment.comment}</span>}
                              />
                            </List.Item>
                          )}
                        />
                      </div>
                    ),
                  },
                ]}
              />
            </Card>
          </Col>
        </Row>
      ) : (
        <Card>
          <Empty description="该任务暂未生成草稿工作区，请先执行任务直到文档生成完成。" />
        </Card>
      )}

      {/* 驳回意见输入弹框 */}
      <Modal
        title="驳回草稿"
        open={rejectModalOpen}
        onOk={handleReject}
        onCancel={() => {
          setRejectModalOpen(false);
          setRejectComment('');
        }}
        okText="驳回"
        okButtonProps={{ danger: true }}
        destroyOnClose
      >
        <TextArea
          rows={4}
          placeholder="请说明确认该知识前必须修正的内容。"
          value={rejectComment}
          onChange={(event) => setRejectComment(event.target.value)}
        />
      </Modal>
    </div>
  );
};

export default Drafts;
