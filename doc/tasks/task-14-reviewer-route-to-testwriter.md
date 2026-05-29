# Task 14: 实现 Reviewer 路由到 TestWriter 的判定

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 6.4、6.5、7.5、11.10、16 章。

## 本次任务

实现最终 Reviewer 拒绝后的路由判断：有些拒绝应回 Coder，有些应回 TestWriter。

## 关键约定

Reviewer 拒绝分类：

- `implementation_issue`：回 Coder。
- `test_issue`：回 TestWriter。
- `unclear`：进入仲裁或按配置处理。

应归类为 `test_issue` 的情况：

- 测试无效。
- 测试过弱。
- 测试与需求不相关。
- 测试被 Coder 删除。
- 测试被 Coder 跳过。
- 测试断言被弱化。
- fixture 错误。
- 测试自身逻辑错误。

v1 可使用关键词启发式，例如：

- `test is invalid`
- `weak test`
- `insufficient test coverage`
- `test was weakened`
- `skipped test`
- `assertion is wrong`
- `fixture is wrong`

路由结果必须写入 `task_events`。

## 禁止事项

- 不要所有 reviewer 拒绝都回 Coder。
- 不要让测试问题消耗 code retry count。
- 不要丢弃原始 reviewer feedback。

## 验收标准

- 单元测试覆盖 test_issue。
- 单元测试覆盖 implementation_issue。
- 单元测试覆盖 unclear。
- 单元测试覆盖大小写变体。
- task_event 中记录分类和路由结果。

