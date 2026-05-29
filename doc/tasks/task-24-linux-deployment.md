# Task 24: 编写 Linux 部署脚本和 systemd 文件

## 系统背景

我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 Java `ProcessBuilder` 调用本机 `opencode` CLI，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 多 agent 流程推进。所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 `NEEDS_ARBITRATION` 等待人工。

## 必读上下文

- `docs/java-opengiraffe-prd.md`
- 重点阅读 PRD 第 8、10、14、16、18 章。

## 本次任务

提供 Linux 部署说明、配置模板和 systemd service。

## 关键约定

文档必须说明前置依赖：

- Java 21。
- git。
- opencode CLI。
- MySQL 8。
- 目标 repo 的访问权限。
- SSH key 或 token。
- worktree 目录写权限。

需要提供：

- 目录结构建议。
- `config.yaml` 模板。
- `opencode.json` 放置说明。
- MySQL 初始化或迁移执行说明。
- systemd service 文件。

systemd 要求：

- 使用非 root 用户，例如 `opengiraffe`。
- 设置 `WorkingDirectory`。
- 设置 `ExecStart`。
- 设置 `Restart`。
- 设置 `User`。
- 说明配置文件路径或环境变量。

## 禁止事项

- 不要要求 root 运行 daemon。
- 不要把密钥写进示例文件。
- 不要假设 opencode provider 已经由系统自动配置。

## 验收标准

- 按文档在 Linux 上能启动服务。
- 服务日志显示配置加载成功。
- 服务日志显示 HTTP 端口。
- systemd 文件可直接作为模板使用。

