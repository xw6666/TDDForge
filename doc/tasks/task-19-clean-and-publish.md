# Task 19: 实现 worktree 清理和分支发布

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 10、13、15、16 章。

## 本次任务

实现任务产物生命周期管理：clean 和 publish。

## 关键约定

clean 操作：

- 仅允许清理已完成、失败、取消或仲裁中的非运行任务。
- 删除任务对应 worktree。
- 删除本地 branch。
- 删除前校验 worktree path 在配置的 `worktree_dir` 下。
- 优先使用 `git worktree remove --force`。
- 执行 `git worktree prune`。
- 再删除本地 branch。

publish 操作：

- 仅允许 publish `COMPLETED` 任务。
- 执行：

```bash
git push --force --set-upstream <remote> <branch>
```

- 成功后保存 `published_at`。

## 禁止事项

- 不要 clean 运行中的任务。
- 不要 publish 未完成任务。
- 不要对未知路径递归删除。
- 不要删除非任务分支。

## 验收标准

- 测试不能 clean 运行中任务。
- 测试不能 publish 未完成任务。
- 测试路径越界保护。
- 测试成功 clean 后任务资源字段更新。
- 测试 publish 成功后写入 `published_at`。

