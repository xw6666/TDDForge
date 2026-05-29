# Task 03: 建立 MySQL Schema 和迁移机制

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 6、9、15、16 章。

## 本次任务

实现 MySQL schema 和迁移机制，建立任务、agent 运行记录、任务事件和应用状态持久化。

## 关键约定

- 使用 Flyway 或 Liquibase。
- MySQL 8，所有表使用 `InnoDB` 和 `utf8mb4`。
- 至少创建四张表：
  - `tasks`
  - `agent_runs`
  - `task_events`
  - `app_state`
- `tasks` 必须保存：
  - id、title、description、status、priority、source、task_mode。
  - parent_id、depends_on_json、force_no_split。
  - repo_path、branch_name、worktree_path。
  - complexity、plan_output、test_output、test_review_output、code_output、review_output。
  - review_pass、reviewer_results_json、session_ids_json。
  - retry_count、test_retry_count、code_retry_count、max_test_retries、max_code_retries。
  - user_feedback、error、created_at、updated_at、started_at、completed_at、published_at。
- `agent_runs` 必须保存：
  - task_id、agent_type、model、variant、agent。
  - prompt、output、exit_code、duration_ms、session_id、continue_count、created_at。
- `prompt` 和 `output` 必须能保存完整长文本。
- JSON 字段用于保存 session ids、reviewer results、depends_on。

## 禁止事项

- 不要删减 PRD 第 9 章的核心字段。
- 不要把 prompt/output 截断后入库。
- 不要在本任务实现完整 orchestrator。

## 验收标准

- 迁移脚本能在空 MySQL 8 库执行。
- Repository 能保存、查询、更新、删除 Task 和 AgentRun。
- JSON 字段能正确序列化和反序列化。
- 测试使用 Testcontainers MySQL 或明确的测试替身。

