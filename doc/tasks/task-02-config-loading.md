# Task 02: 实现配置加载

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 8、10、11、12、16 章。

## 本次任务

实现 YAML 配置加载，让 daemon 能从配置文件读取运行参数和模型配置。

## 关键约定

- 默认配置路径建议支持 `/etc/opengiraffe-java/config.yaml`，同时允许 Spring Boot 标准方式覆盖。
- 配置必须覆盖：
  - HTTP 服务地址和端口。
  - repo path、base branch、worktree dir、worktree hooks。
  - opencode config path、timeout seconds、max continues。
  - Planner、TestWriter、TestReviewer、Coder、Reviewer 的模型配置。
  - max parallel tasks。
  - max test retries。
  - max code retries。
  - MySQL 连接。
  - publish remote。
  - logging level。
- 模型配置统一抽象为 `ModelSpec`：
  - `model`
  - `variant`
  - `agent`
- `variant` 和 `agent` 可以为空字符串。
- 启动时校验：
  - repo path 存在且是 git 仓库。
  - worktree dir 不存在时可创建。
  - opencode config path 可解析为绝对路径。

## 禁止事项

- 不要实现数据库表。
- 不要实现 agent 运行逻辑。
- 不要调用真实 opencode。

## 验收标准

- 提供配置类和配置模板。
- 缺失关键配置时有明确错误。
- 非法路径有明确错误。
- 单元测试覆盖 YAML 绑定、默认值和 `ModelSpec` 解析。

