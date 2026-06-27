# 迭代日志 (Changelog)

本文档记录了**代码洞察平台 (CodeInsight Platform)** 的版本迭代、功能更新、系统优化与 Bug 修复记录。

---

## [v0.1.2] - 2026-06-27

### 💡 核心代码注释与文档化优化
- **全栈代码详细注释**：
  - **后端 Java 模块**：对所有业务领域（系统、仓库、提示词、任务、扫描、解析、切片、AI 归纳、草稿、知识库、推送、审计日志等）的实体类 (`Entity`)、Mapper 接口 (`Mapper`)、服务接口/实现类 (`Service`/`ServiceImpl`) 及控制器 (`Controller`) 进行了规范化的中文 Javadoc 级行级注释，明确了状态机流转、切片逻辑及降级兜底设计。
  - **前端 TS/React 模块**：在 API 统一请求拦截器 (`request.ts`)、路由定义 (`router/index.tsx`)、主要布局组件 (`BasicLayout.tsx`) 及工作台/草稿箱/任务中心核心页面组件中，补齐了状态更新、防抖、自动保存锁、页面交互及生命周期的逻辑注释。

### 🔒 数据库与缓存配置外置化
- **本地环境配置独立抽离**：
  - 将 PostgreSQL 数据库连接（URL、用户名、密码、初始化模式）及 Redis 缓存连接配置从主 `application-local.yml` 彻底剥离。
  - 在 `backend/src/main/resources` 下新增本地专用的 `application-local.properties` 配置文件，以属性键值对形式管理上述数据库及缓存连接信息，实现环境配置与环境特定私密信息的安全隔离。

---

## [v0.1.1] - 2026-06-26

### 🔄 数据库与基础设施升级
- **PostgreSQL 数据库全面接入**：后端服务全面迁移至 **PostgreSQL** 关系型数据库，移除了之前的 Mock 数据与内存数据库（H2）等临时配置，确保系统的持久化存储符合生产环境标准。
- **本地开发环境配置配置项优化**：
  - 更新了 [application-local.yml](file:///d:/WorkSpace/codeinsight-master/backend/src/main/resources/application-local.yml)，配置 PostgreSQL 驱动 (`org.postgresql.Driver`) 及连接 URL。
  - 在数据库 URL 中添加了 `stringtype=unspecified` 参数，以解决 PostgreSQL 严格类型检查的兼容性问题。
  - 启用了 `spring.sql.init.mode: always`，支持启动时自动从 [schema.sql](file:///d:/WorkSpace/codeinsight-master/backend/src/main/resources/db/schema.sql) 初始化和同步 16 张核心业务表的表结构。
- **单元测试环境一致性对齐**：
  - 更新了测试配置文件 [application-test.yml](file:///d:/WorkSpace/codeinsight-master/backend/src/test/resources/application-test.yml)，使单元测试也运行在 PostgreSQL 测试库 (`code_insight_test`) 环境中，消除了开发与测试环境之间的数据库差异。
- **数据库服务启动与监控**：
  - 启动并验证了本地 PostgreSQL 实例（监听端口 `5432`），输出运行状态至 [pg_run.log](file:///d:/WorkSpace/codeinsight-master/pg_run.log)，系统连接正常。

### 🔧 Git 仓库环境校验
- 校验了 Git 仓库初始化状态，确认代码库处于可追踪状态并完成远程配置检测。

---

## [v0.1.0] - 2026-06-21

### ✨ MVP 阶段核心功能交付

#### 🖥️ 前端 (React + Ant Design)
- **视觉与主题规范**：在 `App.tsx` 中通过 `ConfigProvider` 配置了 "Quiet Luxury Console" 主题 Token。应用最新的视觉方案，完成了桌面端、移动端适配。
- **核心模块页面**：
  - **工作台 (Dashboard)**：展示任务吞吐量、待复核项目、Token 成本趋势及最近推送记录。
  - **系统与仓库配置**：支持负责人管理、仓库分支、扫描范围及排除规则。
  - **提示词工作区**：提供提示词模板版本管理、复制、变量替换和试跑功能。
  - **任务中心**：展示初始化/增量分析任务的执行进度，支持状态机追踪与日志查看。
  - **草稿复核区**：提供三栏式 Markdown 编辑区、包含来源行号与待确认项，支持 Redis 自动保存与编辑锁。
  - **Token 审计与日志**：支持 Token 消费明细统计与操作日志追踪。

#### ⚙️ 后端 (Spring Boot + MyBatis Plus + JGit)
- **静态解析与切片 (Scanner & Parser & Chunk)**：
  - 集成 **JGit** 实现代码拉取、文件快照与目录过滤。
  - 支持 **Java 静态解析**，可提取 Java 类型、路由、方法、异常和数据库表等元数据。
  - 实现基于文件、类、方法及 Diff 的智能切片功能，支持 Token 预估与额度阻断。
- **AI 归纳与模型适配**：
  - 接入大语言模型，默认支持 Mock AI（通过 `LLM_MOCK=true` 启用）以供本地开发测试。
  - 提供上下文组装、Prompt 组装及结构化结果解析。
- **知识库输出规范**：
  - 知识输出目录规范化，确认的知识将写入目标仓库的 `/docs/code-insight/`，自动生成 `index.md`、`module-index.md` 及版本元数据配置文件。

---
test111