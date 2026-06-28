# 迭代日志 (Changelog)

本文档记录了**代码洞察平台 (CodeInsight Platform)** 的版本迭代、功能更新、系统优化与 Bug 修复记录。

---

## [v0.1.4] - 2026-06-28

### 🔄 增量扫描（INCREMENTAL）全链路贯通

基于 Git Diff 的增量扫描从「标签」变为「真正生效」，流水线在 `ci_task.type = INCREMENTAL` 时按 `git diff <repo.lastCommit>..HEAD` 识别变更/删除文件，下游 5 个阶段只对变更文件做处理，未变文件的产物原样保留。

- **核心抽象**：
  - 新增 [IncrementalContext.java](backend/src/main/java/com/company/codeinsight/modules/scanner/model/IncrementalContext.java)：不可变上下文，封装 `changedPaths` / `deletedPaths`，提供 `isPathChanged / isPathDeleted / isPathUnchanged` 判定方法；`IncrementalContext.fullScan()` 走全量分支。
  - 新增 [ScanResult.java](backend/src/main/java/com/company/codeinsight/modules/scanner/model/ScanResult.java)：`pullAndScan` 的返回值 = `projectDir + IncrementalContext`。
- **扫描器 ([CodeScannerServiceImpl.java](backend/src/main/java/com/company/codeinsight/modules/scanner/service/impl/CodeScannerServiceImpl.java))**：
  - 接口 `pullAndScan(taskId, repositoryId, taskType)` 新增第三个参数；返回类型由 `File` 改为 `ScanResult`。
  - 拆开 Git 句柄的 `try-with-resources`，保留句柄到 `DiffFormatter.scan()` 算完再统一关闭。
  - 使用 `DiffFormatter(NullOutputStream.INSTANCE) + setDetectRenames(true)` 走 JGit 6.8 的重命名识别（RENAME 拆为「旧路径进 deleted + 新路径进 changed」）。
  - 全量：清空 `ci_file_snapshot` 中 `taskId` 全部记录后扫全树；增量：仅删 `changedPaths + deletedPaths` 的 snapshot，按 `subtreeHasMatch` 在目录级短路跳过未变子树。
  - 始终刷新 `repo.lastCommitId` / `lastDecompileAt`，下次增量即可生效。
- **下游 4 个服务接口加重载，向后兼容**（旧方法委托到新方法 + `IncrementalContext.fullScan()`）：
  - `MethodCallService.persistAstForTask(taskId, projectDir, ctx)`：[MethodCallServiceImpl.java](backend/src/main/java/com/company/codeinsight/modules/callchain/service/impl/MethodCallServiceImpl.java) 删变更 + 删除文件的旧调用链；仅对 `changedPaths` 中 .java 重解析。
  - `CodeChunkService.chunkAndEstimate(taskId, snapshots, ctx)`：[CodeChunkServiceImpl.java](backend/src/main/java/com/company/codeinsight/modules/chunk/service/impl/CodeChunkServiceImpl.java) 抽出 `chunkOneSnapshot(taskId, snapshot)` 给全量/增量共用。
  - `ModuleHierarchyService.buildAndPersist(taskId, projectDir, ctx)`：[ModuleHierarchyServiceImpl.java](backend/src/main/java/com/company/codeinsight/modules/hierarchy/service/impl/ModuleHierarchyServiceImpl.java) 跳过未变入口的 AI 调用；新增 `purgeDeletedClassPaths` + public `deriveFqcnFromPath`，按 Maven 路径推 FQ 类名从 `function.classPaths` 移除被删引用。
  - `AiSummaryService.generateDraftDocument(taskId, chunks, promptContent, ctx)`：[AiSummaryServiceImpl.java](backend/src/main/java/com/company/codeinsight/modules/ai/service/impl/AiSummaryServiceImpl.java) 新增 `moduleTouchedByChange`，仅对「function.classPaths 命中变更 FQ」的模块重跑 AI；其余模块的旧草稿保留。
- **降级路径**（不会让流水线挂在增量分支，警告 + 落全量）：
  - 无 `repo.lastCommitId` 基线（首次增量）。
  - 本地路径或 Mock 降级（无 Git 句柄）。
  - `resolve(ref + "^{tree}")` 失败（force-push / rebase）。
- **流水线编排**：[DecompileTaskServiceImpl.runPipeline()](backend/src/main/java/com/company/codeinsight/modules/task/service/impl/DecompileTaskServiceImpl.java) 收 `ScanResult`，从 `getIncrementalContext()` 取 ctx 并向四个下游透传；日志多打 `scanMode / changed files / deleted files`。
- **测试**：[CodeScannerServiceTest.java](backend/src/test/java/com/company/codeinsight/modules/scanner/CodeScannerServiceTest.java) 适配 `ScanResult` 返回类型，并断言 `null` 走 INITIAL 时 `incremental` 应为 false。

### 🔗 复核节点跳转直达

任务列表「打开复核」、任务详情「复核草稿」按钮不再裸跳 `/drafts`，而是带上下文：

- **跳转链路**：[tasks/detail.tsx](frontend/src/pages/tasks/detail.tsx) 新增 `buildDraftsHref(task)` 工具，生成 `/drafts?systemId=X&taskId=Y`。
- **接收方**：[drafts/index.tsx](frontend/src/pages/drafts/index.tsx) 解析 URL 上的 `systemId` / `taskId`，跳过默认选第一项/优先高亮 REVIEWING 任务的逻辑，直接锁定到指定任务的复核数据；查完工作区后清理 URL 参数（`replace: true`），避免后续系统切换时仍强行跳回原任务。

### 📚 文档同步

- **[README.md](README.md)**：状态机补 `MODULE_HIERARCHY` / `MODULE_HIERARCHY_REVIEW`；业务流程图拆出 INITIAL / INCREMENTAL 两条入口；新增「增量扫描」章节（5 行阶段对照表 + 4 处降级路径）；模块清单从「13 个」纠正为 16 个；测试类计数 14 → 27；新增 v0.1.3 登录认证、模块层级人工复核等核心能力描述。
- **[CLAUDE.md](CLAUDE.md)**：测试类计数 14 → 27；后端模块分层列出 16 个领域模块名；状态机同步；新增「增量扫描（INCREMENTAL 任务）」小节，把 `IncrementalContext` / `ScanResult` 的位置 + 5 阶段对照表写入；`SecurityConfig` 现状明确为 `anyRequest().permitAll()` + 前端 `useAuthStore + RequireAuth` 仅 UI 级守卫；本地路径修正为 `C:\project\codeInsight\CodeInsightPlatform`。

### 🛠️ 内部优化

- **目录级短路**：`scanDirectory(..., pathFilter)` 在进入目录前用 `subtreeHasMatch` 判断「该目录下是否有命中文件」，无则 `continue`，避免大仓库增量跑时绝大多数未变子树被无谓遍历。
- **降级优先级**：增量模式下不存在的 `pathFilter` 路径由「抛错」改为「跳过」，与现有「白名单即视作全量」的口径一致。
- **复用 FQ 推导规则**：`deriveFqcnFromPath` 提到 `ModuleHierarchyServiceImpl` 的 `public static`，让 AI 草稿阶段无需重写一份路径→FQ 转换。

### ⚠️ 已知遗留

- **增量模式不主动删除被删文件对应的旧草稿**：保留以备审计；后续可在 UI 上基于 `filePath in deletedPaths` 增加过滤提示，或在 `generateDraftDocument` 增量分支里加一条「清理孤儿草稿」逻辑。
- **`createIncrementalTask` 暂无前置校验**：当前若仓库没跑过全量，运行时降级为全量并刷新基线；下一步可以在创建任务时直接拒绝并提示「请先跑一次全量建立基线」。
- **沙盒环境 PG 不可达**：`mvn test` 因 DataSource 初始化失败无法本地起，可改用 `mvn -DskipTests compile` / `mvn test-compile` 验证。

---

## [v0.1.3] - 2026-06-27

### 🔐 登录认证模块上线

- **新增登录页 3 字段登录**：UM 账号 / UM 密码 / 平安令牌，平安令牌采用 6 位独立输入框，支持自动跳格、退格回退、粘贴自动拆分、满 6 位自动提交。
- **登录页 UI 全面升级**：定制品牌 SVG 标识、点阵背景、玻璃卡片、内联错误反馈（替代开发期残留的"默认账号 / MVP 内存会话"提示）。
- **后端 `AuthController / AuthService` 骨架**：[LoginRequest](backend/src/main/java/com/company/codeinsight/modules/auth/dto/LoginRequest.java) 加 `@Pattern` 校验 6 位数字令牌，当前为配置化账号占位实现，留待 UM/SSO 真实接入。
- **前端新增 `useAuthStore`（Zustand）+ `RequireAuth` 路由守卫**：未登录访问受保护路由会重定向到 `/login`，登录态随组件树传播。

### 🗃️ 系统与代码库管理升级

- **软删除机制**：`ci_system` / `ci_repository` 加 `deleted_at` 字段（兼容旧库 `ALTER TABLE IF NOT EXISTS`），实体加 `@TableLogic`，所有查询自动过滤已删除记录；新增 `DELETE /systems/{id}` 与 `DELETE /repositories/{id}`，**强校验活跃任务**（PENDING / 拉取 / 解析 / 切片 / AI / 推送）存在时拒绝删除并明确报错；删除系统会**级联软删**其下所有未删除代码库。
- **系统列表聚合指标**：`GET /systems` 一次性返回 3 个新字段——**代码库数 / 知识版本数 / 最近扫描时间**，单条 SQL 用 2 个 LEFT JOIN 子查询实现，避免 N+1。
- **「立即扫描」入口**：代码库行加「扫描」按钮，跳转 `/tasks?systemId=X&repositoryId=Y&openCreate=1`，任务页自动预填并打开创建向导，把"配置"和"执行"在 UI 上打通。

### 🐛 Bug 修复

- **OtpInput 满 6 位提前自动提交**：`useMemoDigits` 之前用 `padEnd(length, '')`，由于 `padEnd` 在填充串为空时**不会补齐**，导致 digits 数组实际长度小于 6，输到第 2 位时 `next.every(d => d !== '')` 就提前返回 true 触发自动提交。改为 `Array.from({ length }, (_, i) => value[i] ?? '')` 始终返回正确长度。
- **Form.Item 未自动注入 value/onChange 导致 OtpInput 运行时崩溃**：将 `OtpInputProps` 的 `value / onChange` 改为可选并加 `getValueProps` + `getValueFromEvent` 显式透传，避免初值 `undefined` 时 `value[0]` 报 TypeError。

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