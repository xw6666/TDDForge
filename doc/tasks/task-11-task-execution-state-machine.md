# Task 11: 实现 TaskExecutionService 主状态机

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 6、7、8、9、12、16 章。

## 本次任务

实现单个开发任务的主状态机，这是系统核心执行流程。

## 关键约定

正常状态流：

```text
PENDING
  -> PLANNING
  -> TEST_WRITING
  -> TEST_REVIEWING
  -> CODING
  -> REVIEWING
  -> COMPLETED
```

异常状态流：

```text
TEST_WRITING -> TEST_WRITE_FAILED -> TEST_WRITING
TEST_REVIEWING -> TEST_REVIEW_FAILED -> TEST_WRITING
REVIEWING -> REVIEW_FAILED -> CODING
REVIEWING -> TEST_REVIEW_FAILED -> TEST_WRITING
任意阶段 -> FAILED
任意阶段 -> CANCELLED
超过重试 -> NEEDS_ARBITRATION
```

执行规则：

- Planner 可把任务拆成子任务。
- 父任务被拆分后不创建 worktree。
- 未拆分任务才创建 worktree 并进入测试阶段。
- 每个阶段开始前和结束后都要保存 task 状态、时间戳、agent run、session id、阶段输出和错误信息。
- 任何阶段执行前都要检查任务是否已取消。
- 异常必须写入 task error 和 task_events。

## 禁止事项

- 不要把所有失败都写成 `FAILED`。
- 不要跳过 TestWriter/TestReviewer。
- 不要在父任务被拆分后直接创建 worktree。
- 不要只写日志不落库。

## 验收标准

- fake agent 测试成功链路。
- 测试 Planner split 后父任务等待。
- 测试 TestWriter 自检失败进入 `TEST_WRITE_FAILED`。
- 测试 TestReviewer 拒绝进入 `TEST_REVIEW_FAILED`。
- 测试 Reviewer 拒绝进入 `REVIEW_FAILED`。
- 测试取消任务会停止推进。

