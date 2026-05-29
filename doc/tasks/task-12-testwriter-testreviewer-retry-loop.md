# Task 12: 实现 TestWriter/TestReviewer 重试循环

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 6.3、6.5、7.2、7.3、8、11.4、11.5、11.6、16 章。

## 本次任务

实现测试阶段完整闭环，而不是只调用一次 TestWriter。

## 关键约定

TestWriter 写完后必须满足：

- 有测试相关 git commit。
- 最终响应包含测试命令。
- 最终响应包含结果分类：
  - `PASS`
  - `EXPECTED_RED`
  - `INVALID`

分类语义：

- `PASS`：当前实现已经满足新测试。
- `EXPECTED_RED`：测试能编译并运行，但因为目标功能尚未实现而失败，这是 TDD 可接受结果。
- `INVALID`：测试自身不可用，例如编译失败、fixture 错、命令错、测试框架没跑起来、失败原因与任务无关。

重试规则：

- TestWriter opencode run 失败 -> 回 TestWriter。
- 输出不完整 -> 回 TestWriter。
- 没有测试 commit -> 回 TestWriter。
- 没有结果分类 -> 回 TestWriter。
- 分类为 `INVALID` -> 回 TestWriter。
- TestReviewer 输出 `REQUEST_CHANGES` -> 回 TestWriter。
- 上述失败共用 `testRetryCount` 和 `max_test_retries`。
- 超过次数进入 `NEEDS_ARBITRATION`，不再自动继续。

session 规则：

- TestWriter retry 可以复用上一轮 session，也可以按配置新建 session。
- 默认新建 session 是可接受的，因为无效测试可能需要重新审视测试策略。
- 无论是否复用 session，上一轮反馈必须通过 retry prompt 传入。

## 禁止事项

- 不要让 `INVALID` 进入 TestReviewer。
- 不要把 `EXPECTED_RED` 当成失败。
- 不要把测试阶段失败消耗 code retry count。

## 验收标准

- 测试 TestWriter invalid 后重试。
- 测试 TestWriter 缺少 commit 后重试。
- 测试 `EXPECTED_RED` 可以进入 TestReviewer。
- 测试 TestReviewer 拒绝后回 TestWriter。
- 测试超过 max test retries 后进入 `NEEDS_ARBITRATION`。

