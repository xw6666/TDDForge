# Task 07: 实现 PromptTemplateRegistry

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 6.5、11、12、16 章。

## 本次任务

实现集中式 prompt 模板注册与渲染组件，禁止 prompt 散落在 agent 业务代码中。

## 关键约定

必须集中管理 PRD 第 11 章所有模板：

- 通用 System Prompt 片段。
- Planner analyze/split。
- Planner no-split。
- TestWriter。
- TestWriter retry。
- TestReviewer。
- Coder implement。
- Coder retry。
- Coder test-failure retry。
- Reviewer review。
- Reviewer patch。
- BranchSlug。
- Continue。

模板变量使用 `{{variable_name}}`。

渲染规则：

- 缺失变量必须抛出清晰异常。
- 不能静默输出未替换变量。
- 模板内容必须可做快照测试。
- TestWriter prompt 必须包含 `PASS / EXPECTED_RED / INVALID` 分类要求。

## 禁止事项

- 不要把大段 prompt 写在 agent 类中。
- 不要修改 PRD 中 prompt 的核心语义。
- 不要漏掉 TestWriter/TestReviewer prompt。

## 验收标准

- 每个模板都有快照测试。
- 缺失关键变量时测试失败。
- agent 类只能通过 registry 获取 prompt。

