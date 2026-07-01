# 增量扫描：模块层级与文档重生成方案

> **状态**：Plan（待实现）  
> **关联**：`INCREMENTAL` 任务、`IncrementalContext`、MODULE_HIERARCHY / GENERATING_DOC 阶段  
> **记录日期**：2026-06-30

## 背景

增量任务（`ci_task.type = INCREMENTAL`）通过 `git diff <lastCommitId>..HEAD` 得到 `changedPaths` / `deletedPaths`，下游各阶段经 `IncrementalContext` 决定是否重算。

当前 MODULE_HIERARCHY 与 GENERATING_DOC 的判定偏「路径 / classPaths 直接命中」，无法覆盖「非入口类变更 → 经调用链影响入口所属模块」的场景。

### 现状

| 阶段 | 实现位置 | 增量判定 |
|------|----------|----------|
| 模块层级 AI | `ModuleHierarchyServiceImpl.buildAndPersist` | 仅当 `entry.getFilePath()` ∈ `changedPaths` 才重跑该入口的 modularize AI |
| 模块文档 AI | `AiSummaryServiceImpl.generateDraftDocument` → `moduleTouchedByChange` | 仅当某 `function.classPaths` **直接包含**变更 FQCN 才重生成该模块草稿 |

### 缺口示例

修改 `UserService.java`（非入口），`UserController.java`（入口）未出现在 diff 中：

- 模块层级：跳过 `UserController` 入口 → **不会**重跑层级 AI（符合「非入口不做层级」）。
- 模块文档：若 `function.classPaths` 未显式列出 `UserService`，则 **不会**重生成文档 → **不符合预期**。

期望：沿 AST / 调用链**反向**找到入口类，定位模块，**仅重生成模块说明文档**，不对非入口类做模块层级提取。

---

## 目标行为（两条规则）

对 `changedPaths` 中每个变更 `.java` 文件，在 PARSING_CODE 完成（`ci_method_call` 已增量刷新）后做一次**影响分析**：

| 变更类类型 | 模块层级（MODULE_HIERARCHY） | 模块文档（GENERATING_DOC） |
|------------|------------------------------|----------------------------|
| **入口类** | 对该入口 **重新** 进行模块层级 AI 提取 | 该入口关联模块纳入重生成集合 |
| **非入口类** | **不** 对该类做模块层级提取 | 反向检索到入口类 → 按「入口类 + 方法」定位模块 → **仅**重生成模块说明文档 |

删除文件：沿用现有逻辑（从 `classPaths` 剔除、清理 chunk/callchain）；若模块结构变空或需刷新说明，纳入文档重生成集合。

---

## 总体架构

新增 **`IncrementalImpactAnalyzer`**（建议包路径：`modules/callchain` 或 `modules/scanner`），在 MODULE_HIERARCHY 与 GENERATING_DOC 之前统一计算影响面。

```
changedPaths (.java)
    │
    ▼
deriveFqcnFromPath（已有，ModuleHierarchyServiceImpl）
    │
    ├─ 入口类？ ──是──► hierarchyRetargetEntries
    │
    └─ 否 ──► 收集变更类方法 seed
              │
              ▼
         MethodCallReverseGraph（反向 BFS，ci_method_call）
              │
              ▼
         命中入口类方法 → mapEntryToModule（ModuleHierarchy）
              │
              ▼
         docRetargetModuleIds
```

### 输出结构（建议）

```java
record IncrementalImpact(
    Set<EntryPoint> hierarchyRetargetEntries,  // 需重跑 modularize AI 的入口
    Set<String> docRetargetModuleIds,          // 需重生成文档的 moduleId / moduleKey
    List<ImpactTrace> traces                   // 可选：日志 / 任务详情展示用
)
```

---

## 详细算法

### 1. 变更文件 → FQCN

- 输入：`IncrementalContext.getChangedPaths()` 中后缀为 `.java` 的路径。
- 转换：复用 `ModuleHierarchyServiceImpl.deriveFqcnFromPath(projectDir, relativePath)`（与现有文档增量逻辑一致）。
- 删除路径：单独处理 classPaths 剔除，不进入反向 BFS。

### 2. 入口类判定

以 `entrypointReviewService.loadEnabledEntries(taskId)` 为准：

- `entry.className` 等于变更 FQCN，或
- `entry.filePath` 等于变更相对路径

→ 加入 `hierarchyRetargetEntries`（去重）。

### 3. 非入口类：反向调用链检索

**现状**：`MethodCallGraphServiceImpl.resolveReachableMethods` 仅支持 **正向** BFS（caller → callee）。

**需新增**：`MethodCallReverseGraphService`（或扩展 `MethodCallGraphService`）：

```
resolveCallingEntries(taskId, seedSignatures, entryClassNames, maxDepth)
```

算法：

1. **Seed 收集**：变更类在 `ci_method_call` 中作为 callee 出现的记录  
   - 优先：`className = changedFqcn` 且 `target_signature` / `caller_signature` 可用的行  
   - 备选：从 chunk（METHOD 级）取方法名，构造 `changedFqcn#methodName(...)` 近似签名  
2. **反向 BFS**：  
   - 当前节点 = 被调方法签名  
   - 查询：`target_signature` 匹配当前节点（需结合 `className` 过滤，见限制）  
   - 扩展：`caller_signature` 入队继续向上  
   - 终止：`caller` 所属类 ∈ `entryClassNames`，或深度 ≥ `maxDepth`（**默认 15**）  
3. **输出**：`Set<EntryMethodHit>`（入口 FQCN + 入口方法签名）

### 4. 入口 → 模块映射

在已加载的 `ModuleHierarchy` 中定位模块（优先级从高到低）：

1. `function.classPaths` 含入口 FQCN **且** `function.methodSignatures`（或等价字段）含命中入口方法  
2. 仅 `classPaths` 含入口 FQCN  
3. 仅反向 BFS 命中入口类：取 hierarchy 中该入口 FQCN 关联的**第一个** function，打 `WARN` 日志  

命中 module → 加入 `docRetargetModuleIds`。

### 5. 降级策略（待确认，建议启用）

反向 BFS **未**命中任何入口，但某 module 的 `function.classPaths` **直接包含**变更 FQCN：

→ 仍将该 module 加入 `docRetargetModuleIds`（与现有 `moduleTouchedByChange` 行为并集，避免漏刷）。

---

## 下游接入点

| 组件 | 文件 | 改动要点 |
|------|------|----------|
| 影响分析 | 新建 `IncrementalImpactAnalyzer` | 封装上述算法，对外 `analyze(taskId, projectDir, ctx, hierarchy, entries)` |
| 反向 BFS | 新建/扩展 `MethodCallReverseGraphService` | 基于 `MethodCallMapper` 反向查询 |
| 模块层级 | `ModuleHierarchyServiceImpl` | `toProcess` 改为：`!incremental \|\| hierarchyRetargetEntries.contains(entry)`，替代纯 `isPathChanged(entry.filePath)` |
| 模块文档 | `AiSummaryServiceImpl` | 增量分支用 `docRetargetModuleIds.contains(moduleId)`；与 `moduleTouchedByChange` **取并集** |
| 流水线日志 | `DecompileTaskServiceImpl` / `TaskExecutionLogger` | 输出：入口重算 N 个、反向命中 M 个模块、降级 K 个 |
| 任务详情（可选 P3） | 前端 task detail | 展示影响链：`UserService#save → UserController#list → 模块「用户管理」` |

**调用时机**：在 `DecompileTaskServiceImpl` 流水线中，PARSING_CODE（callchain 增量落库）之后、MODULE_HIERARCHY 之前计算一次 `IncrementalImpact`，经 `TaskPipelineContext` 或方法参数传递给 hierarchy / ai 阶段。

---

## 数据依赖

| 表 / 实体 | 用途 |
|-----------|------|
| `ci_method_call` | 反向 BFS；字段 `caller_signature`、`target_signature`、`className`、`filePath` |
| `ci_entrypoint`（经 `loadEnabledEntries`） | 入口类 / 文件路径快照 |
| `ci_module_hierarchy_node` + 内存 `ModuleHierarchy` | 入口 → module / function 映射 |
| `IncrementalContext` | changed / deleted paths |

---

## 已知限制

| 项 | 说明 | 缓解 |
|----|------|------|
| `target_signature` 精度 | MVP  callee 端常仅为方法名，反向链可能同名误匹配 | P2 完善 target 端完整签名；P1 用 `className` + `filePath` 过滤 |
| 反射 / 动态调用 | 静态 AST 无法建链 | 降级：`classPaths` 直接命中仍重生成文档 |
| 跨模块长链 | BFS 过深影响性能 | `maxDepth=15` + visited 去重 |
| 新增入口类 | diff 新增且未走 ENTRYPOINT_REVIEW | 本期不自动发现；全量或手动复核入口后重跑 |
| 全量降级 | 无 baseline / diff 失败 | 现有 `IncrementalContext.fullScan()` 不变 |

---

## 实施分期

### P1 — 核心（建议首先实现）

- [ ] `MethodCallReverseGraphService` 反向 BFS（深度上限 15）
- [ ] `IncrementalImpactAnalyzer` + `IncrementalImpact` 模型
- [ ] `ModuleHierarchyServiceImpl` 接入 `hierarchyRetargetEntries`
- [ ] `AiSummaryServiceImpl` 接入 `docRetargetModuleIds`（与 `moduleTouchedByChange` 并集）
- [ ] 流水线日志：影响面摘要
- [ ] 单元测试：mock `ci_method_call` 验证「非入口变更 → 文档模块命中」

### P2 — 精度

- [ ] AST 解析阶段写入完整 `target_signature`（className#method(args)）
- [ ] 方法级 seed（仅变更方法参与反向 BFS，而非整类）
- [ ] 配置项：`code-insight.incremental.reverse-bfs-max-depth`

### P3 — 可观测（可选）

- [ ] API / 任务详情展示 `ImpactTrace`
- [ ] 增量任务文档：README / CLAUDE.md 增量语义表更新

---

## 待确认项（实现前）

以下为用户评审时提出的选项，**默认值**供实现参考：

| # | 问题 | 建议默认 |
|---|------|----------|
| 1 | 是否按 P1 开始实现？ | 待用户确认 |
| 2 | 反向 BFS 深度上限 | **15 层** |
| 3 | 反查失败时是否降级为 `classPaths` 直接命中仍重生成文档？ | **是**（并集） |
| 4 | P2（target_signature 精度）是否与 P1 一并做？ | **否**，P1 先跑通再迭代 |

---

## 与现有增量语义的关系

本方案 **不改变** 以下既有行为：

- `scanner.pullAndScan`：git diff、snapshot 增量重写  
- `callchain.persistAstForTask`：变更 + 删除文件重解析  
- `chunk.chunkAndEstimate`：变更 + 删除文件重建 chunk  
- `hierarchy.buildAndPersist` 落库：仍为 `deleteByTaskId + 全量 insert`（仅 **AI 调用** 按影响面裁剪）  
- 无 baseline / 本地路径 / diff 失败 → 全量扫描降级  

**改变**的是 MODULE_HIERARCHY 与 GENERATING_DOC 的「谁需要跑 AI」判定逻辑，使非入口类变更能通过调用链影响到正确的入口与模块文档。

---

## 参考代码位置

```
backend/src/main/java/com/company/codeinsight/modules/
├── scanner/model/IncrementalContext.java
├── hierarchy/service/impl/ModuleHierarchyServiceImpl.java   # L149-157 toProcess 逻辑
├── ai/service/impl/AiSummaryServiceImpl.java                # moduleTouchedByChange L738-755
├── callchain/service/impl/MethodCallGraphServiceImpl.java   # 正向 BFS，需对称扩展
├── callchain/entity/MethodCall.java                         # caller_signature / target_signature
└── entrypoint/service/EntrypointReviewService.java          # loadEnabledEntries
```
