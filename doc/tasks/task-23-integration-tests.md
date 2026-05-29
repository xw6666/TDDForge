# Task 23: 编写集成测试

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 6、8、9、10、15、16 章。

## 本次任务

实现端到端集成测试，但仍不调用真实模型。

## 关键约定

集成测试应使用：

- Testcontainers MySQL。
- fake opencode 可执行文件。
- 临时 git repo。
- 尽量真实的 Spring Boot 上下文。
- 真实 repository 和状态机。

fake opencode 必须能模拟：

- NDJSON 输出。
- session id。
- 非零退出。
- timeout。
- Continue。
- Planner split。
- TestWriter `INVALID`。
- TestWriter `EXPECTED_RED`。
- Reviewer `REQUEST_CHANGES`。

必须覆盖：

- 单任务成功完成。
- TestWriter 第一次输出 `INVALID` 后被打回重做。
- TestReviewer 拒绝后回 TestWriter。
- Coder 测试失败后回 Coder。
- Reviewer 拒绝后回 Coder。
- Reviewer 指出测试问题后回 TestWriter。
- Planner split 创建子任务。
- 依赖子任务按顺序调度。
- 取消任务终止 fake opencode。

## 禁止事项

- 不要调用真实 LLM。
- 不要依赖真实 opencode provider。
- 不要依赖开发者本机固定路径。

## 验收标准

- 集成测试可在 CI 中稳定运行。
- fake opencode 脚本随测试资源提交。
- 测试完成后临时 repo/worktree 被清理。

