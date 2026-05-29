# Task 16: 实现依赖图 DependencyTracker

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 6.2、6.5、9、15、16 章。

## 本次任务

实现 Planner 拆分子任务后的依赖管理。

## 关键约定

- Planner 输出的 `depends_on` 是子任务数组下标。
- 保存到 Task 时必须转换成真实 task id。
- DependencyTracker 必须能判断子任务是否被依赖阻塞。
- 被阻塞的子任务不能创建 worktree。
- 被阻塞的子任务不能启动 agent。
- 依赖任务 `COMPLETED` 后释放下游任务。
- 依赖任务失败、取消或进入仲裁时，下游任务和父任务要展示阻塞原因。
- daemon 重启后，依赖图必须从 MySQL 中的 `parent_id` 和 `depends_on_json` 重建。

## 禁止事项

- 不要让依赖图只存在内存里。
- 不要把 failed/cancelled 当成依赖满足。
- 不要忽略父任务状态聚合。

## 验收标准

- 测试并行子任务。
- 测试串行依赖。
- 测试依赖完成后释放下游。
- 测试依赖失败后阻塞下游。
- 测试 daemon 重启后重建依赖图。
- 测试父任务完成状态聚合。

