# Task 15: 实现 Orchestrator 并发调度

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 6、10、12、13、16 章。

## 本次任务

实现 daemon 级任务调度器，控制并发、排队和任务派发。

## 关键约定

- 使用固定大小 `ExecutorService`。
- 并发数由 `max_parallel_tasks` 控制。
- 维护 `taskId -> Future` 的 running map。
- 防止同一 task 重复 dispatch。
- 并发槽满时任务不失败，进入 pending dispatch 队列。
- 运行任务结束后自动刷新 pending 队列。

dispatch 前必须检查：

- task 存在。
- task 状态允许运行。
- task 未取消。
- task 未在 running map 中。
- 子任务依赖已满足。

## 禁止事项

- 不要无限创建线程。
- 不要同一 task 重复运行。
- 不要在并发满时直接失败。
- 不要忽略依赖阻塞。

## 验收标准

- 测试并发上限。
- 测试重复 dispatch。
- 测试 pending 队列等待。
- 测试任务完成后自动补位。
- 测试取消任务不再调度。

