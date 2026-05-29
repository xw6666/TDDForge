# Task 18: 实现任务取消和 opencode 进程清理

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 6、8、13、16 章。

## 本次任务

实现可靠取消和 opencode 子进程清理。

## 关键约定

取消任务时必须：

- 将 task 状态标记为 `CANCELLED`。
- 调用 `OpenCodeClient.killTask(taskId)`。
- 终止当前关联的 opencode 进程。
- 保留已有 worktree。
- 保留已有 agent run。

状态机要求：

- 每个阶段执行前检查取消状态。
- 取消后不得继续推进后续阶段。
- daemon stop 时调用 `killAll()`。
- daemon stop 时安全关闭线程池。

## 禁止事项

- 不要取消时删除 worktree。
- 不要取消时删除 agent run。
- 不要只改状态不杀进程。

## 验收标准

- fake long-running opencode 能被取消。
- 取消后状态不继续推进。
- 取消任务仍可查询历史输出。
- daemon stop 会清理所有 active opencode 进程。

