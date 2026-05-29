# Task 17: 实现 REST API

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 13、14、15、16 章。

## 本次任务

实现 REST API，让外部 UI 或 CLI 可以控制自动研发 daemon。

## 关键约定

必须提供：

```text
POST   /api/tasks
GET    /api/tasks
GET    /api/tasks/{id}
POST   /api/tasks/{id}/dispatch
POST   /api/tasks/{id}/cancel
POST   /api/tasks/{id}/revise
POST   /api/tasks/{id}/clean
POST   /api/tasks/{id}/publish
GET    /api/tasks/{id}/runs
GET    /api/tasks/{id}/status
GET    /api/system/status
```

创建任务 API 接收：

- `title`
- `description`
- `priority`
- `forceNoSplit`

任务详情返回应包含：

- 当前状态。
- 父子任务。
- 依赖信息。
- worktree。
- branch。
- test/code retry count。
- agent session ids。
- 最近错误。
- 最近 agent run 摘要。

## 禁止事项

- dispatch API 不要同步跑完整任务。
- cancel API 必须触发 opencode 进程终止。
- 不要只返回裸数据库字段。

## 验收标准

- Web 层测试覆盖参数校验。
- 测试任务不存在。
- 测试状态不允许操作。
- 测试正常返回结构。
- 测试 dispatch 调用 orchestrator。

