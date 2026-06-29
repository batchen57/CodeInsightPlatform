import request from './request';
import type {
  KnowledgeBrowseFileType,
  KnowledgeBrowseItem,
  KnowledgeBrowseQuery,
} from '../types';

/**
 * 列出系统下的知识文档 / 索引文件 / 清单文件
 * 后端：GET /api/knowledge/browse
 */
export const listKnowledgeBrowse = (
  query: KnowledgeBrowseQuery,
): Promise<KnowledgeBrowseItem[]> => {
  return request.get('/knowledge/browse', { params: query });
};

/**
 * 读取单条目的原始文本内容
 * 后端：GET /api/knowledge/browse/content
 *
 * - DRAFT：通过 draftId 读取 ci_knowledge_draft.content_uri 指向的物理 Markdown
 * - INDEX/MANIFEST：通过 taskId + filePath（相对 docs/code-insight/）读 temp_repos 文件
 */
export const getKnowledgeBrowseContent = (params: {
  type: KnowledgeBrowseFileType;
  id?: number;
  taskId?: number;
  filePath?: string;
}): Promise<string> => {
  return request.get('/knowledge/browse/content', {
    params,
    responseType: 'text',
    // 不让 axios 把字符串当作 JSON 解析
    transformResponse: [(data) => data],
  });
};