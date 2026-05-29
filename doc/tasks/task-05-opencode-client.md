# Task 05: 实现 OpenCodeClient

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 6.5、8、12、16 章。

## 本次任务

实现系统最底层的 opencode CLI 调用客户端。

## 关键约定

- Java daemon 不直接调用任何 LLM SDK。
- 所有模型交互都通过本机命令行：

```bash
opencode run --model <model> --dir <worktree> --format json <prompt>
```

- 使用 Java `ProcessBuilder`。
- 工作目录设置为任务 worktree。
- 环境变量注入：

```text
OPENCODE_CONFIG=<resolved_opencode_config_path>
```

- 支持可选参数：
  - `--session <sessionId>`
  - `--variant <variant>`
  - `--agent <agent>`
- 必须实现：
  - timeout。
  - stdout 完整收集。
  - stderr 记录。
  - exit code 记录。
  - duration 记录。
  - task id 到 Process 的映射。
  - `killTask(taskId)`。
  - `killAll()`。
  - `AgentRun` 构造。

## 禁止事项

- 不要直接调用 provider API。
- 不要在本任务解析 Planner JSON 或 Reviewer verdict。
- 不要丢弃 raw output。

## 验收标准

- fake opencode 脚本可测试正常输出。
- fake opencode 脚本可测试非零退出。
- timeout 后进程被终止。
- `killTask` 能终止指定 task 进程。
- `OPENCODE_CONFIG` 被正确注入。

