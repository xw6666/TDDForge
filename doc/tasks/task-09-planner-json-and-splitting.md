# Task 09: 实现 Planner JSON 解析和拆分逻辑

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 6.2、6.5、7.1、11.2、11.3、16 章。

## 本次任务

实现 Planner 输出 JSON 的解析和父子任务拆分逻辑。

## 关键约定

Planner 输出 JSON 支持：

- `complexity`
- `split`
- `reason`
- `plan`
- `sub_tasks`
- `priority`
- `depends_on`

`complexity` 只能是：

```text
very_complex
complex
medium
simple
```

行为规则：

- `split=false`：把 `plan` 保存到当前 task 的 `plan_output`。
- `split=true`：当前 task 作为父任务等待，不创建 worktree。
- `split=true`：系统创建多个子任务。
- 子任务描述必须包含父任务整体目标、子任务边界、依赖关系和验收要求。
- Planner 输出中的 `depends_on` 是 0 基下标，必须转换为真实子任务 id。
- 解析失败时 Planner 自动重试一次。
- 第二次仍失败则任务失败并保存错误。

## 禁止事项

- 不要递归无限拆分 Planner 子任务。
- 不要忽略非法 depends_on。
- 不要让父任务直接进入 TestWriter。

## 验收标准

- 测试覆盖单任务 plan。
- 测试覆盖 split 创建子任务。
- 测试覆盖非法 JSON。
- 测试覆盖空 sub_tasks。
- 测试覆盖非法 depends_on。

