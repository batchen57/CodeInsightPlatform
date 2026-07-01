# 知识查看：列表 / 树形双模式方案

> **状态**：已实现（2026-06-30）  
> **页面**：`frontend/src/pages/knowledge/index.tsx`  
> **API**：`GET /api/knowledge/browse`（分页）、`GET /api/knowledge/browse/tree`

## 已实现行为

| 维度 | 列表模式 | 树形模式（默认） |
|------|----------|------------------|
| 系统 | 可选（空=跨全部） | 必选 |
| 仓库 | 可选 | 必选 |
| 搜索 | 文件名 / 系统 / 仓库 + 高级筛选 | 树内关键字过滤 |
| 展示 | 平铺 Table + 服务端分页 | 模块 → 子模块 → 功能（叶子） |
| 文档 | 行点击 / 查看 | 叶子「查看」或点击节点 |
| 类型 | DRAFT / INDEX / MANIFEST | 仅知识文档（DRAFT） |

## 后端

- `KnowledgeBrowseQuery`：`systemId` / `repositoryId` 可选；`current` / `size` 分页
- `GET /knowledge/browse` 返回 `PageResult<KnowledgeBrowseItem>`
- `GET /knowledge/browse/tree?systemId=&repositoryId=&taskId=` → `KnowledgeBrowseTreeResult`
- 基准任务：未传 `taskId` 时自动取该仓库最新 `PENDING_REVIEW|REVIEWING|CONFIRMED|PUSHED|PUSHING` 任务
- 文档粒度：读取 `code-insight.doc-generation.granularity`（function / module）

## 前端

- 默认树形；`localStorage` 键 `ci-knowledge-view-mode` 记忆偏好
- 预览 Drawer 与列表模式共用
