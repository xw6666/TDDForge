# Task 22: 编写单元测试

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 6、8、11、12、15、16 章。

## 本次任务

补齐核心单元测试，确保后续自动修改不会破坏状态机和解析规则。

## 关键约定

必须覆盖：

- 配置加载。
- `ModelSpec` 解析。
- prompt 渲染和缺失变量。
- opencode NDJSON 解析。
- 自动 Continue 判定。
- `AgentOutputExtractor`。
- Planner JSON 解析。
- TestWriter `PASS / EXPECTED_RED / INVALID` 分类解析。
- Reviewer verdict 解析。
- Reviewer 路由分类。
- 状态机转移。
- 依赖图。
- repository JSON 序列化。

测试边界：

- 单元测试不得调用真实 opencode。
- 单元测试不得调用真实 LLM。
- 模型 I/O 必须 mock。
- 需要真实 MySQL 的测试应归为集成测试。

## 禁止事项

- 不要写依赖本机 opencode 配置的单元测试。
- 不要让单元测试依赖网络。
- 不要只测试 happy path。

## 验收标准

- 无 opencode 环境也能运行单元测试。
- 无 MySQL 服务也能运行纯单元测试。
- 测试命名清楚表达行为。
- 失败时能定位具体规则。

