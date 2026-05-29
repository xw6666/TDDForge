# Task 06: 实现 opencode NDJSON 解析

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 8、12、16 章。

## 本次任务

实现 opencode `--format json` 的 NDJSON 输出解析器和自动 Continue 机制。

## 关键约定

- raw output 必须完整保留。
- 解析器要支持：
  - 提取 session id。
  - 提取 text event 文本。
  - 提取最后 stop step 文本。
  - 生成可读步骤。
  - 统计 tool 调用摘要。
  - 判断输出是否完整。
- 完整性判定：
  - 最后一个 step 必须有 `step_finish`。
  - `step_finish.reason` 必须等于 `stop`。
- 容错规则：
  - 空行跳过。
  - 单行非法 JSON 跳过并记录 debug/warn 日志。
  - 未知字段忽略。
  - 不能因为一行非法 JSON 导致整个 AgentRun 解析失败。
- 自动 Continue：
  - 当 exit code 非 0 或输出不完整，并且已经拿到 session id 时，自动再次调用同一模型、同一 worktree、同一 variant/agent。
  - 命令传入 `--session <sessionId> "Continue"`。
  - 最多执行 `max_continues` 次。

## 禁止事项

- 不要用严格解析导致兼容性差。
- 不要在解析失败时丢弃 raw output。
- 不要无限 Continue。

## 验收标准

- 测试覆盖正常 stop。
- 测试覆盖无 stop。
- 测试覆盖无 session。
- 测试覆盖非法 JSON 行被跳过。
- 测试覆盖未知字段被忽略。
- 测试覆盖自动 Continue 拼接输出。

