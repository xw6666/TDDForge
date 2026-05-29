# Task 25: 编写最小 Dashboard

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 13、15、16 章。

## 本次任务

实现最小可用 Web Dashboard，用于调试和日常操作。

## 关键约定

Dashboard 必须展示：

- 任务列表。
- 任务状态。
- 父子任务。
- retry count。
- worktree。
- branch。
- 最近错误。
- 各阶段输出。
- agent runs。
- prompt。
- 原始 output。
- session id。
- duration。
- exit code。

Dashboard 必须提供操作：

- dispatch。
- cancel。
- revise。
- clean。
- publish。

交互规则：

- 不可用操作应禁用或显示原因。
- 状态刷新可用轮询实现。
- 不要求 WebSocket。

## 禁止事项

- 不要做营销页。
- 不要为了 UI 改动核心状态机。
- 不要隐藏 agent raw output。

## 验收标准

- 本地启动后可浏览任务列表。
- 可查看任务详情和 agent runs。
- 可通过页面 dispatch/cancel/revise/clean/publish。
- 可查看 fake agent 输出。

