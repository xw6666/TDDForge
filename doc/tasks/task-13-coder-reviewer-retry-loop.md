# Task 13: 实现 Coder/Reviewer 重试循环

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 6.3、6.4、6.5、7.4、7.5、8、11.7、11.8、11.9、11.10、16 章。

## 本次任务

实现编码和最终审查闭环。

## 关键约定

进入 Coder 的前置条件：

- Planner 已完成。
- TestWriter 已产生测试输出。
- TestReviewer 已审批通过。
- worktree 已创建。

Coder 规则：

- 在已审批测试约束下实现生产代码。
- 不能删除、跳过、弱化 TestWriter 产生的测试。
- 完成后必须有实现相关 git commit。
- 输出中记录测试命令和结果。

Reviewer 规则：

- 可以配置多个 reviewer。
- 所有 reviewer 都必须 `APPROVE`，任务才 `COMPLETED`。
- 任一 reviewer `REQUEST_CHANGES`，默认短路并回 Coder。

Coder retry session 规则：

- Coder retry 必须优先复用最近一次 coder session。
- 调 opencode 时传入：

```bash
--session <coderSessionId>
```

- retry prompt 中包含最新 reviewer feedback。
- 这样 opencode 保留前一轮已读文件、工具调用和实现上下文。
- 如果没有 coder session id，才允许新建 session，并写入 task_event。

重试规则：

- Reviewer 拒绝消耗 `codeRetryCount`。
- 超过 `max_code_retries` 后进入 `NEEDS_ARBITRATION`。

## 禁止事项

- 不要让 Coder 在 TestReviewer 通过前运行。
- 不要在 Coder retry 时默认新建 session。
- 不要把 reviewer 测试问题路由逻辑写在本 task，那个属于 Task 14。

## 验收标准

- 测试单 reviewer 通过。
- 测试多 reviewer 全通过。
- 测试首个 reviewer 拒绝短路。
- 测试拒绝后回 Coder 且复用 coder session。
- 测试缺失 session 时记录事件并新建 session。
- 测试超过 max code retries 后进入仲裁。

