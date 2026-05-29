# Task 01: 初始化 Java Spring Boot 工程

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 1、3、10、12、15、16 章。

## 本次任务

创建 Java 21 + Spring Boot 3 工程骨架，作为后续所有多 agent 调度能力的宿主进程。

## 关键约定

- 优先使用 Maven，除非仓库已经有 Gradle 约定。
- 依赖至少包含 Spring Web、Validation、MySQL JDBC Driver、Spring JDBC 或 Spring Data JPA、Flyway 或 Liquibase、Jackson、Actuator、JUnit 5、Mockito/AssertJ。
- 建立包结构：
  - `config`
  - `domain`
  - `persistence`
  - `opencode`
  - `agent`
  - `git`
  - `orchestrator`
  - `web`
  - `service`
  - `util`
- 创建可运行的 Spring Boot 主类。
- 提供最小启动验证，例如 Actuator health 或启动测试。

## 禁止事项

- 不要实现具体 agent 流程。
- 不要连接真实 opencode。
- 不要实现 MySQL schema。
- 不要做复杂 Dashboard。

## 验收标准

- `mvn test` 或等价命令通过。
- 应用可以启动。
- README 或配置模板中说明需要 Java 21。
- 包结构与 PRD 一致。

