# Task 21: 实现日志与可观测性

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 8、9、14、15、16 章。

## 本次任务

实现结构化日志和基础可观测性，方便排查长时间 agent 任务。

## 关键约定

日志中关键字段：

- task id
- agent type
- model
- session id
- worktree path
- branch
- duration
- exit code
- retry count

必须接入 Spring Boot Actuator：

- health
- info
- metrics

health 至少检查：

- 数据库连接。
- 基础配置有效。

health 不要求检查真实 opencode provider。

敏感信息不得输出：

- MySQL password。
- token。
- 完整环境变量。
- SSH key。

## 禁止事项

- 不要把 prompt/output 全部打进普通日志。
- 不要输出敏感配置。
- 不要把 health 做成依赖真实 LLM provider 的检查。

## 验收标准

- 单元或集成测试覆盖敏感字段脱敏。
- 手动启动时能看到结构化关键字段。
- Actuator health 可访问。
- 数据库不可用时 health 体现异常。

