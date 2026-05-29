# Task 08: 实现 BaseAgent 和各 Agent 类

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 6.5、7、8、11、12、16 章。

## 本次任务

实现 agent 抽象层，让不同职责的 agent 都通过统一方式调用 opencode。

## 关键约定

必须实现或等价实现：

- `BaseAgent`
- `PlannerAgent`
- `TestWriterAgent`
- `TestReviewerAgent`
- `CoderAgent`
- `ReviewerAgent`

`BaseAgent` 负责：

- 接收 `ModelSpec`。
- 接收 `OpenCodeClient`。
- 接收 prompt、worktree、task id、session id。
- 调用 opencode。
- 返回 `AgentRun`。

各派生 agent 只负责：

- 准备对应 prompt。
- 调用 `OpenCodeClient`。
- 把 `AgentRun` 交给集中式 `AgentOutputExtractor` 得到结构化结果。

解析边界：

- Planner JSON 解析不写在 `PlannerAgent` 里。
- TestWriter 分类解析不写在 `TestWriterAgent` 里。
- Reviewer verdict 解析不写在 `ReviewerAgent` 里。
- 这些逻辑由 Task 26 的 `AgentOutputExtractor` 统一管理。

## 禁止事项

- 不要在 agent 类中做调度。
- 不要在 agent 类中做数据库事务。
- 不要在 agent 类中散落 prompt 字符串。

## 验收标准

- fake `OpenCodeClient` 可测试每个 agent。
- 测试覆盖 prompt 变量传入。
- 测试覆盖 session id 传递。
- 测试覆盖 agent 输出交给 extractor。

