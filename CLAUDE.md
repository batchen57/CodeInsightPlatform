# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在本仓库中工作时提供指导。

## 项目概览

代码洞察平台（CodeInsight Platform）将现有代码库转化为可维护、可追溯、可复核的代码知识资产。核心流程：代码拉取 → Java 静态解析 → 代码切片 → AI 归纳 → 草稿复核 → 知识版本管理 → Git/ZIP 输出 → Token 审计 → 操作日志。AI 生成内容必须先经过复核才能进入正式知识库。

- **前端**：React 19 + TypeScript + Vite + Ant Design + Zustand + ECharts + Monaco Editor + React Router
- **后端**：Java 17 + Spring Boot 3.3 + MyBatis Plus + PostgreSQL + Redis + JGit
- **存储分工**：PostgreSQL（状态与元数据）、本地文件系统/对象存储（正文内容）、Redis（草稿临时编辑与锁）、Git（已确认的正式知识）

## 常用命令

### 前端（`cd frontend`）

```bash
npm install          # 安装依赖
npm run dev          # 启动开发服务器 http://localhost:5173
npm run lint         # 运行 ESLint 检查
npm run build        # 类型检查 (tsc -b) + 生产构建 (vite build)
npm run preview      # 预览生产构建
```

### 后端（`cd backend`）

```bash
mvn spring-boot:run     # 启动后端 http://localhost:8080/api
mvn clean test          # 运行全部测试（23 个测试，JUnit 5）
mvn clean package       # 打包 JAR
```

### 环境要求

- JDK 17、Maven 3.8+、Node.js 20+、PostgreSQL 14+、Redis 6+
- 后端默认启用 Mock AI（`LLM_MOCK=true`），本地开发无需真实 API Key
- Swagger UI：`http://localhost:8080/api/swagger-ui.html`
- Vite 开发模式下将 `/api` 代理到 `localhost:8080`

## 架构

### 后端模块结构（`backend/src/main/java/com/company/codeinsight/`）

`modules/` 下的每个模块遵循统一的分层模式：`entity/`、`mapper/`、`service/`、`service/impl/`、`controller/`。共享基础设施位于 `common/`（config、exception、response、model）。

| 模块 | 职责 |
|---|---|
| `system` | 系统注册、负责人管理、启停控制 |
| `repository` | Git 仓库地址、分支、凭证、扫描范围、排除规则 |
| `prompt` | 提示词模板、版本管理、复制、启停、试跑 |
| `task` | 任务状态机、进度、重试、终止 |
| `scanner` | JGit 拉取、文件快照、目录过滤 |
| `parser` | 静态解析：Java 类型、路由、方法、异常、数据表 |
| `chunk` | 文件/类/方法/Diff 切片、Token 预估 |
| `ai` | 上下文组装、Prompt 组装、模型调用、结果解析 |
| `draft` | 草稿工作区、Markdown 草稿、Redis 自动保存、修订记录、评审意见 |
| `knowledge` | 版本元数据、`knowledge-version.json`、`module-map.yaml` |
| `push` | Git 推送、PR/MR 创建、ZIP 导出、推送前校验 |
| `token` | Token 记录、成本统计、额度控制、趋势分析 |
| `log` | 操作日志、任务日志、异常追踪 |
| `model` | AI 模型配置（供应商、API Key、接口地址、排序） |

### 统一 API 响应格式

所有接口返回 `{ code: 0, message: "success", data: {} }`。前端 `request.ts` 中的 axios 拦截器会自动解包——页面代码直接拿到 `data` 的内容。错误时 code 为非零值，错误信息在 `message` 中。

### 前端结构（`frontend/src/`）

| 目录 | 用途 |
|---|---|
| `api/` | 按业务域拆分的 axios 请求函数（每个后端模块对应一个文件），`request.ts` 为配置好的 axios 实例 |
| `types/index.ts` | 所有与后端实体对应的 TypeScript 接口定义。后端 snake_case 通过 MyBatis Plus 映射为 camelCase |
| `pages/` | 路由级页面组件：dashboard、systems、tasks（列表 + 详情）、prompts、drafts、push、token-audit、logs、model |
| `router/index.tsx` | Hash 路由，包裹在 `BasicLayout` 中，所有路由均为 `/` 的子路由 |
| `layouts/BasicLayout.tsx` | Ant Design Sider/Header/Content 外壳，深色侧边导航栏 + 路由元信息 |
| `App.tsx` | 根组件：Ant Design `ConfigProvider` 自定义主题 Token（Quiet Luxury Console 风格）+ `RouterProvider` |

### 任务状态机

```
DRAFT → PENDING → PULLING_CODE → PARSING_CODE → SPLITTING_TASK
      → AI_ANALYZING → GENERATING_DOC → PENDING_REVIEW → REVIEWING
      → CONFIRMED → PUSHING → PUSHED
```

终止状态：`FAILED`、`CANCELLED`、`ARCHIVED`。状态流转必须可追溯，不允许非法跳转。

### 数据库

PostgreSQL，启动时自动从 `backend/src/main/resources/db/schema.sql` 初始化表结构（`spring.sql.init.mode: always`）。核心表：`ci_system`、`ci_repository`、`ci_prompt`、`ci_task`、`ci_file_snapshot`、`ci_chunk`、`ci_ai_call_record`、`ci_draft_workspace`、`ci_knowledge_draft`、`ci_draft_revision`、`ci_draft_review_comment`、`ci_draft_source_reference`、`ci_knowledge_version`、`ci_token_usage_audit`、`ci_operation_log`、`ci_model`。

### 知识输出目录结构

已确认的知识写入目标仓库的 `/docs/code-insight/` 目录，包含 `index.md`、`module-index.md`、`architecture-overview.md`，以及 `/modules/`、`/changes/`、`/meta/` 子目录，其中存放 `knowledge-version.json`、`module-map.yaml` 和 `prompt-used.json`。

## 关键约定

- **SecurityConfig 当前允许所有请求**——仅适用于 MVP 阶段，生产环境必须配置正式认证授权。
- **AI 内容不得直接推送到 Git**——必须经过 草稿 → 复核 → 确认 的完整流程。
- **草稿正文不存入数据库**——数据库只存 URI/Hash，正文存文件系统/对象存储。
- **前端使用 hash 路由**（`createHashRouter`），非 browser router，所有路径均为 hash 模式。
- **后端数据源 URL 必须带 `stringtype=unspecified`**——PostgreSQL 兼容性要求。
- **前端 ESLint 配置**：`eslint.config.js` 位于 frontend 根目录，`npm run lint` 执行 `eslint .`。
- **Ant Design 主题**统一在 `App.tsx` 的 `ConfigProvider` 中配置——新增页面应使用主题 Token 变量，不要硬编码颜色值。
- **`backend/temp_repos/` 目录**存放的是 scanner/parser 测试用的模拟仓库（task_1、task_3）——属于测试数据，非主应用代码。


## 配置

后端配置：`application.yml`（profile 选择器）→ `application-local.yml`（实际配置，含环境变量占位符）。前端环境变量：`.env` 文件中配置 `VITE_API_BASE_URL`。根目录 `.env.example` 记录了所有可用变量，包括 `LLM_MOCK`、`LLM_API_KEY`、`DB_*`、`REDIS_*`、`STORAGE_LOCAL_PATH`。

## 编码行为准则

旨在减少大语言模型（LLM）常见编程错误的行為准则。

**权衡取舍：** 这些准则更倾向于"谨慎"而非"速度"。对于微不足道的简单任务，请自行斟酌判定。

---

### 1. 编码前先思考 (Think Before Coding)

**不要盲目假设。不要隐瞒疑惑。明确权衡利弊。**

在开始编写代码前：
- 明确陈述你的假设。如果存在不确定性，请主动提问。
- 如果存在多种理解或实现方式，请将它们呈现给用户——切勿默默自行选择。
- 如果存在更简单的替代方案，请明确提出。在合理情况下，应推动方案简化。
- 如果有任何不清晰的地方，请立即停止。指出令人困惑的具体问题并向用户提问。

---

### 2. 简约至上 (Simplicity First)

**用最精简的代码解决问题。不做任何投机性/猜测性的设计。**

- 绝不添加用户未要求的任何额外功能。
- 绝不为单次使用的代码做过度抽象。
- 绝不引入未经请求的"灵活性"或"可配置性"。
- 绝不对不可能发生的场景编写冗余的错误处理逻辑。
- 如果写了 200 行代码，但实际上 50 行就能解决，请重写它。

经常问自己："一位资深工程师会觉得这个设计过于复杂了吗？"如果是，请立即简化。

---

### 3. 外科手术式修改 (Surgical Changes)

**只触碰必须修改的部分。只清理自己造成的改动。**

编辑现有代码时：
- 不要顺手去"改进"相邻的代码、注释或格式。
- 不要重构没有损坏或能正常工作的逻辑。
- 必须匹配既有的代码风格，即使你自己习惯的方式与此不同。
- 如果你注意到了不相关的废弃代码，请向用户提及——切勿直接删除它。

当你的改动使其他代码失效或成为孤立代码（孤儿代码）时：
- 仅清除由于你的改动而变得不再使用的 import 导入、变量或函数。
- 除非用户明确要求，否则不要清除先前就已存在的废弃代码。

**终极检验**：确保你修改的每一行代码，都能直接追溯到用户的具体请求上。

---

### 4. 目标驱动执行 (Goal-Driven Execution)

**明确成功标准。持续循环验证，直至完全通过。**

将开发任务转化为可验证的目标：
- "添加校验逻辑" → "编写针对无效输入的测试用例，然后使其通过"
- "修复该 Bug" → "编写一个能复现该 Bug 的测试用例，然后使其通过"
- "重构模块 X" → "确保重构前和重构后测试均能全数通过"

对于多步骤的复杂任务，应列出一个简要的执行计划：
```
1. [执行步骤] → 验证依据: [检查项]
2. [执行步骤] → 验证依据: [检查项]
3. [执行步骤] → 验证依据: [检查项]
```

清晰、强力的成功标准能让你自主闭环迭代。含糊不清的标准（例如"能跑通即可"）则会需要频繁的沟通确认。

---

**检验本准则是否生效的标志**：代码差异（Diff）中无意义的改动减少、因设计过度复杂导致的重写次数减少，以及在编码实现前（而非犯错后）进行的澄清提问增加。