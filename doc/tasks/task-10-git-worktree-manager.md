# Task 10: 实现 Git WorktreeManager

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 6、10、12、16 章。

## 本次任务

实现 git worktree 隔离层，让每个开发任务在独立 worktree 和分支中运行。

## 关键约定

必须支持：

- fetch base branch。
- 生成 task branch name。
- 创建 worktree：

```bash
git worktree add -b <branch> <worktreePath> origin/<baseBranch>
```

- 复制 repo 根目录中的 `AGENTS.md` 到 worktree。
- 复制 repo 根目录中的 `hooks/` 到 worktree。
- 按配置执行 worktree hooks。
- 查询 `git status --short --branch`。
- 查询 changed files。
- 删除 worktree。
- 删除本地 branch。
- publish branch 到 remote。

安全规则：

- 删除操作必须校验目标路径在配置的 `worktree_dir` 下。
- 不得删除任意未知路径。
- 所有 git 命令必须有 timeout。
- 命令失败时保存 stdout/stderr。

## 禁止事项

- 不要让多个任务共用同一个 worktree。
- 不要直接在主 repo 工作区执行 agent。
- 不要对未校验路径递归删除。

## 验收标准

- 临时 git repo 测试创建 worktree。
- 测试查询 status。
- 测试删除 worktree 和 branch。
- 测试 hooks 执行。
- 测试路径越界保护。

