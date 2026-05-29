# Task 04: 实现领域模型

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 6、7、9、12、16 章。

## 本次任务

定义领域模型，供 agent、orchestrator、repository 和 web 层共同使用。

## 关键约定

必须实现或等价实现：

- `Task`
- `AgentRun`
- `TaskStatus`
- `TaskPriority`
- `TaskSource`
- `ModelSpec`
- `ReviewerResult`
- `OpenCodeRequest`
- `OpenCodeEvent`
- `PlannerResult`
- `ReviewVerdict`
- `TestWriterResult`
- `CoderResult`

`TaskStatus` 必须包含：

```text
PENDING
PLANNING
TEST_WRITING
TEST_WRITE_FAILED
TEST_REVIEWING
TEST_REVIEW_FAILED
CODING
REVIEWING
REVIEW_FAILED
NEEDS_ARBITRATION
COMPLETED
FAILED
CANCELLED
```

`Task` 必须支持：

- `testRetryCount`
- `codeRetryCount`
- `sessionIds`
- `reviewerResults`
- `dependsOn`
- 各阶段输出字段。

## 禁止事项

- 不要省略 TDD 状态。
- 不要把测试阶段失败和编码阶段失败混为一个 retry counter。
- 不要让 JSON 字段只能单向序列化。

## 验收标准

- 枚举非法值有明确错误。
- 领域对象能稳定 JSON 序列化和反序列化。
- 单元测试覆盖 Task、AgentRun、ModelSpec、状态枚举和 JSON 字段。

