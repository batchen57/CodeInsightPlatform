# 集群部署就绪说明

本文档说明 CodeInsight 平台从单机 MVP 演进到多节点集群时的架构变更、配置项与部署检查清单。

## 开关

```yaml
code-insight:
  cluster:
    enabled: true   # 多节点必须 true；本地开发默认 false
```

环境变量：`CLUSTER_ENABLED=true`

## 依赖

| 组件 | 单机 | 集群 |
|------|------|------|
| PostgreSQL | 必需 | 必需（共享同一实例） |
| Redis | 草稿 autosave / 推送队列 / 定时锁 | **必需**（分布式并发、Leader、配置广播、编辑锁） |
| 共享文件卷 | 可选 | **必需**（`storage` + `workspace-root` 同卷挂载） |

## 存储路径

```yaml
code-insight:
  storage:
    local-path: /mnt/shared/code-insight/storage      # 草稿正文、pipeline.log
    workspace-root: /mnt/shared/code-insight/temp_repos # Git 克隆与扫描工作区
```

所有节点必须挂载**同一路径**（NFS / EFS / 云盘）。代码通过 `TaskWorkspacePaths` 统一解析 `{workspace-root}/task_{id}`。

## 集群模式行为变更

### 1. 任务队列（P0）

- **Leader 调度**：仅持有 `ci:leader:task-dispatcher` 的节点运行 `TaskQueueDispatcher`。
- **DB 认领**：`SELECT … FOR UPDATE SKIP LOCKED` + `claimed_by` / `lease_until` 预留 PENDING 行。
- **Redis 并发**：全局 `ci:permits:task:global` + 每系统 `ci:permits:task:sys:{id}`，holder=`task:{id}`。
- **重试**：`retryTask` 会清空认领字段，重新入队。

### 2. AI 并发（P1）

- JVM `Semaphore` → Redis Set `ci:permits:ai:global`。
- 配置项 `ai.concurrency` 变更后通过 Redis Pub/Sub `ci:config:refresh` 广播各节点重建。

### 3. 定时调度（P1）

- `ScheduleExecutor` 主轮询与队列消费均受 `ci:leader:schedule-executor` Leader 锁保护。
- 单条 schedule 触发仍使用既有 Redis 锁（`ScheduleTaskService.tryFire`）。

### 4. 草稿编辑（P2）

- 移除 JVM 内存 autosave 兜底；autosave **必须**走 Redis。
- 编辑锁 API：
  - `POST /drafts/{id}/edit-lock/acquire`
  - `POST /drafts/{id}/edit-lock/renew`
  - `POST /drafts/{id}/edit-lock/release`
- 前端应周期性 renew（间隔 < `draft-edit-lock-ttl-seconds`，默认 120s）。

### 5. 断点恢复亲和（P2）

- `resumeAfterEntrypointReview` / `resumeAfterHierarchyReview` 会检查本节点是否存在任务工作目录。
- 无共享卷时，需在原执行节点继续，或确保 `workspace-root` 已挂载。

## 已集群就绪（无需改造）

- **推送队列**：Redis List + 版本锁（`PushTaskScheduler`）。
- **定时单条触发**：`ScheduleTaskService` 内 Redis 分布式锁 + QUEUE 策略。

## 仍存在的单机假设（已知限制）

| 项 | 说明 |
|----|------|
| `taskCache` / `pipelineContextCache` | JVM 内存，仅优化同节点轮询；断点恢复依赖 DB + 共享卷重建 |
| 对象存储 S3 | 未实现；仍依赖共享文件系统 |
| 前端编辑锁 UI | 已接入 acquire / renew / release，状态栏实时展示锁状态 |
| 认证 | `SecurityConfig` 仍 `permitAll`，生产需独立改造 |

## 部署检查清单

1. [ ] PostgreSQL、Redis 对所有节点可达
2. [ ] `CLUSTER_ENABLED=true`
3. [ ] `STORAGE_LOCAL_PATH` 与 `STORAGE_WORKSPACE_ROOT` 挂载为共享卷
4. [ ] 各节点 `code-insight.cluster.enabled=true` 且 Redis 配置一致
5. [ ] 重启后 schema 自动添加 `claimed_by` / `claimed_at` / `lease_until`
6. [ ] 验证：两节点同时启动，PENDING 任务不重复执行
7. [ ] 验证：修改 `task.concurrency` / `ai.concurrency` 后各节点日志出现 ConfigRefreshListener 刷新

## 本地开发

保持 `code-insight.cluster.enabled=false`（默认）即可沿用 JVM Semaphore + 全节点调度，与改造前行为兼容。
