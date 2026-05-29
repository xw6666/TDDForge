# Task 20: 实现 daemon 启动恢复

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 6、9、14、15、16 章。

## 本次任务

实现 daemon 重启恢复，处理上次运行中断留下的任务状态。

## 关键约定

active 状态包括：

- `PLANNING`
- `TEST_WRITING`
- `TEST_REVIEWING`
- `CODING`
- `REVIEWING`

默认恢复策略：

- active task on startup -> `FAILED`
- error 写明 `daemon restarted during active execution`
- 保留 worktree 供人工检查。
- pending task 保持 `PENDING`。
- completed task 保持 `COMPLETED`。

启动时还必须：

- 重建依赖图。
- 恢复 pending dispatch 语义。
- 核对 branch/worktree 是否仍存在。
- 生成资源快照供 API 展示。
- 把恢复动作写入 `task_events`。

## 禁止事项

- 不要自动删除重启前的 worktree。
- 不要把 active task 静默改回 pending。
- 不要只重建内存状态而不记录事件。

## 验收标准

- 测试 active task 恢复为 failed。
- 测试 pending task 保持 pending。
- 测试 completed task 保持 completed。
- 测试依赖图重建。
- 测试恢复动作写入 task_events。

