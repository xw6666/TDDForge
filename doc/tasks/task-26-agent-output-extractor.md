# Task 26: 实现 AgentOutputExtractor 结构化输出提取

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 6.5、7、8、11、12、16 章。

## 本次任务

实现集中式 agent 输出提取组件，避免每个 agent 自己写零散字符串解析逻辑。

## 关键约定

实现 `AgentOutputExtractor` 或等价服务。输入为 `AgentRun` 和 agent 类型，输出为结构化结果对象。

必须支持：

- Planner：
  - 从最终文本中提取 JSON 对象。
  - 解析为 `PlannerResult`。
  - 包含 complexity、split、plan、sub_tasks、depends_on。
- TestWriter：
  - 提取测试文件列表。
  - 提取测试命令。
  - 提取结果分类 `PASS / EXPECTED_RED / INVALID`。
  - 提取提交信息或 commit hash。
  - 提取简短测试说明。
- TestReviewer：
  - 提取首行 verdict `APPROVE / REQUEST_CHANGES`。
  - 提取正文反馈。
- Coder：
  - 提取实现摘要。
  - 提取修改文件列表。
  - 提取测试命令。
  - 提取测试结果。
  - 提取提交信息或 commit hash。
- Reviewer：
  - 提取首行 verdict。
  - 提取正文反馈。
  - 分类为 `implementation_issue / test_issue / unclear`。

容错规则：

- 如果模型输出没有完全遵循格式，返回带 warning 的部分结果。
- raw output 必须保留。
- Planner JSON 无法解析是关键错误。
- Reviewer verdict 缺失是关键错误。
- TestWriter 分类缺失是关键错误。

## 禁止事项

- 不要让解析逻辑散落在 agent 类里。
- 不要因为缺少非关键字段丢弃整个输出。
- 不要吞掉 raw output。

## 验收标准

- 测试规范输出。
- 测试缺少字段。
- 测试额外 markdown。
- 测试非法 JSON。
- 测试多个 verdict 关键词。
- 测试 TestWriter 三种分类。
- 测试 Coder 文件列表提取。
- 测试 reviewer test_issue 分类。

