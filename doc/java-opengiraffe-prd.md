# Java OpenGiraffe PRD

版本：v1.0

状态：草案，可进入设计评审

目标部署环境：Linux

技术栈：Java 21、Spring Boot 3、MySQL 8、opencode CLI、git worktree

## 1. 产品定义

本项目要实现一个 Java 版本的 OpenGiraffe：一个持久化、可并发执行的多 agent 自动研发系统。系统默认部署在 Linux 上，底层不直接调用大模型 API，而是统一调用本机已安装并配置好的 `opencode` CLI。系统围绕任务队列工作，为每个开发任务创建独立 git worktree，并按 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` 的 TDD 质量门流程自动推进。

系统必须支持：

- 用户提交开发任务。
- Planner 判断任务复杂度，并决策是否拆分为多个可并发或有依赖关系的子任务。
- TestWriter 先写测试，形成测试提交。
- TestReviewer 审批测试质量，拒绝不合格测试。
- Coder 在测试约束下实现代码，使测试通过。
- Reviewer 审查最终测试与实现，全部通过后任务完成。
- 所有 agent 运行记录、prompt、opencode 原始输出、session id、任务状态、worktree 信息持久化到 MySQL。
- 多任务并发执行，每个任务隔离在自己的 git worktree 和分支中。

## 2. 背景与迁移原则

当前 Python 版 OpenGiraffe 已经具备以下核心能力：

- opencode CLI 封装。
- Planner、Coder、Reviewer agent。
- SQLite 持久化。
- git worktree 隔离。
- Planner 拆分子任务。
- Reviewer 全部通过才完成，失败后回到 Coder 重试。
- Dashboard 和 CLI 管理任务。

Java 版本必须优先迁移这些稳定行为。除非 TDD 流程需要扩展，否则不改变核心语义。

迁移原则：

- opencode 调用协议保持一致。
- prompt 风格默认从现有 OpenGiraffe 迁移。
- Planner/Coder/Reviewer prompt 能直接迁移的直接迁移。
- TestWriter/TestReviewer 是新增 agent，按现有 prompt 写法设计。
- 状态机必须显式表达 TDD 阶段。
- 持久化从 SQLite 改为 MySQL。
- 默认部署在 Linux，不要求 Windows 兼容。

## 3. 目标

### 3.1 功能目标

系统必须完成一个完整自动开发闭环：

```text
用户提交任务
  -> Planner 分析复杂度与拆分
  -> 子任务调度
  -> 创建 worktree
  -> TestWriter 写测试并提交
  -> TestReviewer 审批测试
  -> Coder 编码并提交
  -> Reviewer 审查测试与实现
  -> 完成 / 重试 / 进入人工仲裁
```

### 3.2 质量目标

- 新增代码必须经过测试优先约束。
- 测试本身必须经过 agent 审批，避免弱测试、无效测试、只测 mock、只测实现细节。
- 最终 Reviewer 必须同时审查测试提交和实现提交。
- 任务失败必须可追踪，包含完整 agent 输出。
- 系统重启后必须可恢复未完成任务状态。

### 3.3 运维目标

- 支持 systemd 部署。
- 支持配置文件管理模型、repo、worktree、并发数、重试次数。
- 支持 kill 正在运行的 opencode 进程。
- 支持清理 worktree 和本地分支。

## 4. 非目标

v1 不做以下内容：

- 不直接集成任何 LLM provider SDK。
- 不实现浏览器 IDE。
- 不做复杂权限系统。
- 不实现跨机器分布式调度。
- 不自动 merge 到主干。
- 不要求支持 Windows 部署。

## 5. 用户角色

### 5.1 系统管理员

负责部署 Java daemon、配置 MySQL、配置 opencode、配置目标 repo 和模型。

### 5.2 开发负责人

负责提交任务、查看进度、审批仲裁、发布完成分支。

### 5.3 自动 agent

通过 opencode CLI 在 worktree 中执行规划、写测试、审测试、写实现、审代码。

## 6. 核心流程

### 6.1 单任务流程

```text
PENDING
  -> PLANNING
  -> TEST_WRITING
  -> TEST_REVIEWING
  -> CODING
  -> REVIEWING
  -> COMPLETED
```

异常分支：

```text
TEST_WRITING -> TEST_WRITE_FAILED -> TEST_WRITING
TEST_REVIEWING -> TEST_REVIEW_FAILED -> TEST_WRITING
REVIEWING -> REVIEW_FAILED -> CODING
REVIEWING -> TEST_REVIEW_FAILED -> TEST_WRITING
任意阶段 -> FAILED
任意阶段 -> CANCELLED
超过重试 -> NEEDS_ARBITRATION
```

### 6.2 Planner 拆分流程

Planner 输出 `split=true` 时，父任务不创建 worktree，不直接开发。系统创建多个子任务，子任务继承父任务描述中的整体目标，并根据 `depends_on` 建立依赖图。

子任务满足：

- `depends_on=[]` 的子任务可立即调度。
- 有依赖的子任务必须等待依赖任务 `COMPLETED` 后调度。
- 父任务在全部子任务完成后变为 `COMPLETED`。
- 任一子任务失败或进入仲裁时，父任务必须同步展示阻塞状态。

### 6.3 TDD 流程

TestWriter 必须先写测试并提交。Coder 后续必须基于该测试提交继续工作。

建议提交结构：

```text
commit 1: test: cover expected behavior for <task>
commit 2: fix: implement <task>
```

TestWriter 写完测试后必须先做自检。自检不是要求所有新增测试都绿，因为 TDD 下新增测试可能因为生产代码尚未实现而红；自检的目标是确认测试代码本身有效、可编译、可被测试框架发现，并且失败原因符合任务预期。TestWriter 最终必须把测试运行结果明确归类为：

```text
PASS         当前实现已经满足测试，测试通过。
EXPECTED_RED 测试成功运行，但因为目标行为未实现而失败，这是 TDD 预期红灯。
INVALID      测试编译失败、测试框架无法运行、测试数据/fixture 错误、命令错误、或失败原因与任务目标无关。
```

如果 TestWriter 的 opencode 运行失败、输出不完整、没有提交测试改动、或自检结果为 `INVALID`，系统必须把任务打回 TestWriter 重做。该重试和 TestReviewer 拒绝共用 `max_test_retries` 与 `test_retry_count`。超过次数后任务进入 `NEEDS_ARBITRATION`，等待人工处理。

TestWriter 阶段允许：

- 新增测试文件。
- 修改已有测试。
- 新增必要测试夹具。
- 新增最小测试辅助代码。

TestWriter 阶段禁止：

- 修改生产逻辑以让测试通过。
- 删除或弱化已有测试。
- 大范围重构。
- 提交环境产物。

Coder 阶段允许：

- 修改生产代码。
- 修改必要测试夹具。
- 在测试发现不合理时通过受控流程回到 TestWriter。

Coder 阶段禁止：

- 删除 TestWriter 新增测试。
- 弱化断言。
- 通过硬编码让测试通过。

### 6.4 Reviewer 判定规则

所有最终 Reviewer 必须通过，任务才算 `COMPLETED`。任一 Reviewer 输出 `REQUEST_CHANGES`，系统进入重试。

Reviewer 拒绝原因分为：

- `implementation_issue`：回到 Coder。
- `test_issue`：回到 TestWriter。
- `unclear`：进入 `NEEDS_ARBITRATION` 或按配置回到 Coder。

v1 可以先用文本启发式识别：

- 反馈中明确指出 test/test coverage/assertion/weak test 等问题时，归为 `test_issue`。
- 其他默认 `implementation_issue`。

### 6.5 Agent 间信息传递

系统中 agent 之间不能依赖进程内对象、临时文件或隐式聊天上下文传递关键业务信息。`Task` 对象及其 MySQL 持久化字段是唯一的跨 agent 信息中继站；`AgentRun` 保存审计历史和原始输出，但下一阶段 prompt 需要使用的关键上下文必须先落到 `Task` 的明确字段中。

核心传递规则：

```text
Planner -> task.plan_output -> TestWriter / Coder / Reviewer prompt
TestWriter -> task.test_output -> TestReviewer / Coder / Reviewer prompt
TestReviewer -> task.test_review_output -> Coder / Reviewer prompt
Coder -> task.code_output -> Reviewer prompt
Reviewer -> task.review_output -> Coder retry / TestWriter retry / human arbitration
```

prompt 模板中的 `{{plan_output}}`、`{{test_output}}`、`{{test_review_output}}`、`{{coder_response}}`、`{{prior_rejections}}` 等变量必须从 Task 持久化字段或由 Task 关联的最新 AgentRun 中确定性生成。除 opencode session 复用外，不允许要求某个 agent 直接读取另一个 agent 的内存状态。

opencode session 是上下文增强机制，不是持久化事实来源。系统恢复、Dashboard 展示、人工仲裁和后续 prompt 渲染必须以 MySQL 中的 Task 与 AgentRun 为准。

## 7. Agent 定义

### 7.1 Planner

职责：

- 理解任务。
- 评估复杂度。
- 决策是否拆分。
- 输出单任务实施计划或子任务 JSON。

输入：

- 任务标题。
- 任务描述。
- repo 路径。
- 是否禁止拆分。

输出：

```json
{
  "complexity": "medium",
  "split": false,
  "reason": "...",
  "plan": "Overall objective: ...\n1. ..."
}
```

或：

```json
{
  "complexity": "complex",
  "split": true,
  "reason": "...",
  "sub_tasks": [
    {
      "title": "...",
      "description": "...",
      "priority": "medium",
      "depends_on": []
    }
  ]
}
```

### 7.2 TestWriter

职责：

- 根据 Planner 计划和任务描述先写测试。
- 只提交测试相关改动。
- 运行相关测试或测试验证命令，记录命令、输出和结果分类。
- 区分 `PASS`、`EXPECTED_RED`、`INVALID`。
- 当测试代码自身无效、不可编译、命令错误或失败原因与任务无关时，必须继续修复测试，不能把无效测试交给 TestReviewer。
- 说明新增测试如何约束目标行为。

输出：

- opencode 原始输出。
- 测试说明。
- 测试命令。
- 测试结果分类。
- 测试 commit。

### 7.3 TestReviewer

职责：

- 审查 TestWriter 产生的测试提交。
- 判断测试是否真实、必要、稳定、能约束需求。
- 输出 `APPROVE` 或 `REQUEST_CHANGES`。

输出首行必须是：

```text
APPROVE
```

或：

```text
REQUEST_CHANGES
```

### 7.4 Coder

职责：

- 在已审批测试基础上实现代码。
- 保持测试不被弱化。
- 运行相关测试。
- 提交实现代码。

输出：

- opencode 原始输出。
- 实现说明。
- 测试命令和结果。
- 实现 commit。

### 7.5 Reviewer

职责：

- 审查测试提交和实现提交。
- 判断实现是否正确、测试是否仍有效、是否存在无关改动。
- 输出 `APPROVE` 或 `REQUEST_CHANGES`。

## 8. opencode 调用协议

Java 版本必须通过 `ProcessBuilder` 调用 opencode。

基础命令：

```bash
opencode run \
  --model <model> \
  --dir <worktree_path> \
  --format json \
  <prompt>
```

可选参数：

```bash
--session <session_id>
--variant <variant>
--agent <agent>
```

环境变量：

```bash
OPENCODE_CONFIG=<resolved_opencode_config_path>
```

Session 使用语义：

- 首次运行某个 agent 阶段时，不传 `--session`，让 opencode 创建新 session。
- 同一 Coder 阶段的 retry 必须优先复用最近一次 coder session，通过 `--session <coderSessionId>` 发送 retry prompt。这样 opencode 能保留它已读过的文件、工具调用记录和前一轮实现上下文。
- 同一 TestWriter 阶段的 retry 可以复用 session，也可以新建 session。默认建议新建 session 或由配置控制，因为测试被拒绝时经常需要重新审视测试策略，而不是沿着错误测试继续局部修补。
- Reviewer 和 TestReviewer 默认每次审查新建 session，保证审查相对独立；如后续支持 reviewer continuation，必须把该行为显式配置化。
- 自动 `Continue` 必须复用当前 run 提取到的 session id，因为它是同一次 opencode 输出未完成时的续跑。
- session id 必须保存到 `task.session_ids_json` 和对应 `agent_runs.session_id`，但系统事实来源仍然是 MySQL 中的 Task 与 AgentRun。

要求：

- stdout 必须完整保存到 `agent_runs.output`。
- stderr 必须进入日志；非零退出时也要记录到 agent run 的 error 字段或 output 附加段。
- 必须支持 timeout。
- 必须支持按 task id kill 进程。
- 必须解析 NDJSON。
- NDJSON 解析必须容错：空行跳过，单行非法 JSON 跳过并记录 debug/warn 日志，不能因为一行不可解析导致整个 AgentRun 解析失败。
- 必须提取 `sessionID`。
- 必须判断最后一个 `step_finish.reason == "stop"`。
- 如果输出不完整或 exit code 非 0 且存在 session id，必须自动发送 `Continue`，最多 `maxContinues` 次。

## 9. MySQL 数据模型

数据库：MySQL 8，字符集 `utf8mb4`，引擎 `InnoDB`。

### 9.1 tasks

```sql
CREATE TABLE tasks (
  id VARCHAR(32) PRIMARY KEY,
  title VARCHAR(512) NOT NULL,
  description LONGTEXT NOT NULL,
  status VARCHAR(64) NOT NULL,
  priority VARCHAR(32) NOT NULL DEFAULT 'medium',
  source VARCHAR(64) NOT NULL DEFAULT 'manual',
  task_mode VARCHAR(32) NOT NULL DEFAULT 'develop',
  parent_id VARCHAR(32) NULL,
  depends_on_json JSON NOT NULL,
  force_no_split BOOLEAN NOT NULL DEFAULT FALSE,

  repo_path VARCHAR(1024) NOT NULL,
  branch_name VARCHAR(512) NOT NULL DEFAULT '',
  worktree_path VARCHAR(1024) NOT NULL DEFAULT '',

  complexity VARCHAR(64) NOT NULL DEFAULT '',
  plan_output LONGTEXT,
  test_output LONGTEXT,
  test_review_output LONGTEXT,
  code_output LONGTEXT,
  review_output LONGTEXT,
  review_pass BOOLEAN NOT NULL DEFAULT FALSE,
  reviewer_results_json JSON NOT NULL,
  session_ids_json JSON NOT NULL,

  retry_count INT NOT NULL DEFAULT 0,
  test_retry_count INT NOT NULL DEFAULT 0,
  code_retry_count INT NOT NULL DEFAULT 0,
  max_test_retries INT NOT NULL DEFAULT 2,
  max_code_retries INT NOT NULL DEFAULT 4,

  user_feedback LONGTEXT,
  error LONGTEXT,

  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  started_at DATETIME(3) NULL,
  completed_at DATETIME(3) NULL,
  published_at DATETIME(3) NULL,

  INDEX idx_tasks_status (status),
  INDEX idx_tasks_parent_id (parent_id),
  INDEX idx_tasks_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 9.2 agent_runs

```sql
CREATE TABLE agent_runs (
  id VARCHAR(32) PRIMARY KEY,
  task_id VARCHAR(32) NOT NULL,
  agent_type VARCHAR(64) NOT NULL,
  model VARCHAR(256) NOT NULL,
  variant VARCHAR(128) NOT NULL DEFAULT '',
  agent VARCHAR(128) NOT NULL DEFAULT '',
  prompt LONGTEXT NOT NULL,
  output LONGTEXT NOT NULL,
  exit_code INT NOT NULL,
  duration_ms BIGINT NOT NULL,
  session_id VARCHAR(256) NOT NULL DEFAULT '',
  continue_count INT NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL,

  INDEX idx_agent_runs_task_id (task_id),
  INDEX idx_agent_runs_agent_type (agent_type),
  CONSTRAINT fk_agent_runs_task
    FOREIGN KEY (task_id) REFERENCES tasks(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 9.3 task_events

```sql
CREATE TABLE task_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id VARCHAR(32) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  message TEXT NOT NULL,
  data_json JSON NULL,
  created_at DATETIME(3) NOT NULL,

  INDEX idx_task_events_task_id (task_id),
  CONSTRAINT fk_task_events_task
    FOREIGN KEY (task_id) REFERENCES tasks(id)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 9.4 app_state

```sql
CREATE TABLE app_state (
  state_key VARCHAR(128) PRIMARY KEY,
  data_json JSON NOT NULL,
  updated_at DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

## 10. 配置文件

默认配置文件路径：

```text
/etc/opengiraffe-java/config.yaml
```

配置模板：

```yaml
server:
  host: 0.0.0.0
  port: 8778

repo:
  path: /data/repos/target-project
  base_branch: master
  worktree_dir: /data/opengiraffe/worktrees
  worktree_hooks: []

opencode:
  config_path: /etc/opengiraffe-java/opencode.json
  timeout_seconds: 3600
  max_continues: 8
  planner:
    model: opencode/qwen3.6-plus-free
    variant: ""
    agent: ""
  test_writer:
    model: opencode/qwen3.6-plus-free
    variant: ""
    agent: ""
  test_reviewer:
    model: opencode/qwen3.6-plus-free
    variant: ""
    agent: ""
  coder_default:
    model: opencode/qwen3.6-plus-free
    variant: ""
    agent: ""
  coder_by_complexity:
    very_complex:
      model: opencode/qwen3.6-plus-free
    complex:
      model: opencode/qwen3.6-plus-free
    medium:
      model: opencode/qwen3.6-plus-free
    simple:
      model: opencode/qwen3.6-plus-free
  reviewers:
    - model: opencode/qwen3.6-plus-free
      variant: ""
      agent: ""

orchestrator:
  max_parallel_tasks: 3
  max_test_retries: 2
  max_code_retries: 4
  poll_interval_seconds: 30

mysql:
  url: jdbc:mysql://127.0.0.1:3306/opengiraffe_java?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
  username: opengiraffe
  password: change-me

publish:
  remote: origin

logging:
  level: INFO
```

## 11. Prompt 规范

本章是系统必须内置的 prompt 模板。模板变量使用 `{{variable_name}}` 表示。Java 实现中必须集中管理这些模板，不允许散落在业务代码中。

### 11.1 通用 System Prompt 片段

所有 agent prompt 必须继承以下约束。该片段是 Java 版本新增，用于统一 Linux/opencode/worktree 语义。

```text
You are running inside an isolated git worktree managed by OpenGiraffe Java.

Global rules:
1. Work only inside the current repository/worktree.
2. Read AGENTS.md first if it exists, and follow it strictly.
3. Use relative file paths in explanations and commands whenever possible.
4. Do not modify unrelated files.
5. Do not commit environment setup, dependency cache, build outputs, or temporary files.
6. Do not ask the user questions. Make reasonable engineering decisions from repository context.
7. If you make code or test changes, ensure the relevant changes are committed.
8. Report the exact commands you ran and whether they passed.
```

### 11.2 Planner: Analyze And Split Prompt

来源：从当前 OpenGiraffe `planner_analyze_and_split` 迁移，仅将变量改为模板占位符。

```text
You are a planning agent. Analyze the following task and produce a structured plan.

Task title: {{title}}

Task description: {{description}}

Repository: {{repo_path}}

Step 1 — Assess complexity. Choose ONE label:
  very_complex : Requires deep understanding of multiple subsystems, likely touches >10 files,
                 high risk of breaking existing behaviour, needs the most capable model.
  complex      : Touches several modules or requires careful design, moderate risk.
  medium       : Clear scope, a few files, straightforward logic.
  simple       : Trivial fix or one-liner, low risk, small model is sufficient.

Step 2 — Decide whether to split.
  Split into sub-tasks ONLY if:
  - The task clearly contains multiple separable concerns in different modules/files AND
  - Parallelising those concerns would save meaningful time.
  Prefer NOT splitting. Minor edits across several files should usually remain one task. Only split when each sub-task is substantial and meaningfully self-contained.

Step 3 — If you split, define dependency ordering precisely.
  - Sub-tasks that can run in parallel must use an empty depends_on list.
  - If sub-task B must wait for sub-task A, add A's 0-based index to B's depends_on.
  - Only add dependencies when strictly required.
  - Prefer maximum safe parallelism.

Step 4 — If you split, write strong sub-task descriptions.
  - Each sub-task description must fully and measurably describe the expected outcome.
  - Each description must explain the sub-task's role in the overall task.
  - Each description must be clear enough for an implementation agent to act without guessing.

Step 5 — Produce the output.
  Output ONLY valid JSON (no markdown fences).

If you keep it as a single task:
{"complexity": "medium", "split": false, "reason": "...", "plan": "Overall objective: ...\n1. ...\n2. ..."}

If you split it:
{"complexity": "complex", "split": true, "reason": "...", "sub_tasks": [
  {"title": "Define new interface in module A", "description": "...", "priority": "high", "depends_on": []},
  {"title": "Migrate callers to new interface", "description": "...", "priority": "medium", "depends_on": [0]}
]}
```

### 11.3 Planner: Analyze And Split No-Split Prompt

来源：从当前 OpenGiraffe 禁止拆分分支迁移。

```text
You are a planning agent. Analyze the following task and produce a structured plan.

Task title: {{title}}

Task description: {{description}}

Repository: {{repo_path}}

Step 1 — Assess complexity. Choose ONE label:
  very_complex : Requires deep understanding of multiple subsystems, likely touches >10 files,
                 high risk of breaking existing behaviour, needs the most capable model.
  complex      : Touches several modules or requires careful design, moderate risk.
  medium       : Clear scope, a few files, straightforward logic.
  simple       : Trivial fix or one-liner, low risk, small model is sufficient.

Step 2 — Splitting is forbidden for this task.
  - You MUST keep this as a single task.
  - You MUST output "split": false.
  - You MUST NOT output sub_tasks.

Step 3 — Produce the output.
  Output ONLY valid JSON with this exact shape:
  {"complexity": "medium", "split": false, "reason": "Splitting was explicitly disabled for this task.", "plan": "Overall objective: ...\n1. ...\n2. ..."}
```

### 11.4 TestWriter Prompt

来源：新增 agent，按当前 OpenGiraffe Coder prompt 写法设计。

```text
You are a test-writing agent in a TDD coding pipeline. Your job is to write meaningful tests BEFORE implementation. You must first read AGENTS.md within the project if it exists and strictly follow it.

## Task: {{title}}

## Description
{{description}}

## Repository
{{repo_path}}

## Planner Output
{{plan_output}}

## Delivery Requirements
The changes you make in this phase must form a correct test-only git commit that can be reviewed independently before implementation begins.

## Requirements
1. Write or update tests that express the expected behavior of this task.
2. Do NOT implement the production code fix in this phase.
3. Do NOT weaken, delete, or bypass existing tests.
4. Prefer focused tests close to the affected behavior instead of broad brittle integration tests, unless integration coverage is necessary.
5. The tests must be capable of catching an incorrect or missing implementation. Avoid tests that only assert mocks, implementation details, or superficial existence.
6. If the current code already satisfies the behavior, add regression coverage where valuable and clearly explain why the test already passes.
7. Run the most relevant test command available in the repository. Do not run the full suite unless the repository convention requires it or the scope is unclear.
8. Classify the test command result as exactly one of:
   - PASS: the current implementation already satisfies the new/updated tests.
   - EXPECTED_RED: the tests compile and run, but fail because the requested behavior is not implemented yet.
   - INVALID: tests do not compile, cannot run, use broken fixtures, use the wrong command, or fail for reasons unrelated to the requested behavior.
9. If the result is INVALID, keep fixing the tests before finishing. Do not hand off invalid tests.
10. Commit only the test-related changes. The commit should not include environment setup, dependency cache, build outputs, or unrelated formatting.
11. Use ONLY relative file paths in your final response.
12. Final response must include:
   - test files changed
   - behavior covered
   - exact test command(s) run
   - result classification: PASS / EXPECTED_RED / INVALID
   - relevant command output summary
   - commit hash or confirmation that the changes were committed
```

### 11.5 TestWriter Retry Prompt

来源：新增 agent，按当前 OpenGiraffe `coder_retry_feedback` 写法设计。

```text
## Test Phase Feedback (attempt {{attempt}})
{{test_phase_feedback}}

Please inspect the feedback carefully and update the tests accordingly.

This feedback may come from:
1. TestWriter self-validation failure, such as invalid test code, compilation failure, broken fixtures, no test commit, or incomplete opencode output.
2. TestReviewer REQUEST_CHANGES feedback about weak, irrelevant, brittle, or invalid tests.

Rules:
1. Do not implement production code.
2. Do not weaken assertions just to satisfy the reviewer.
3. Keep the test changes focused on the task requirements.
4. Run the relevant tests or validation command again.
5. Classify the result as PASS / EXPECTED_RED / INVALID.
6. If the result is INVALID, keep fixing the tests before finishing.
7. Amend or create a new test commit according to repository conventions.
8. Final response must explain what changed and include the exact test command result and classification.
```

### 11.6 TestReviewer Prompt

来源：新增 agent，按当前 OpenGiraffe Reviewer prompt 写法设计。

```text
You are a test review agent.

## Task that the tests were written for
Title: {{title}}
Description: {{description}}

## Planner Output
{{plan_output}}

## Test Writer Response
{{test_writer_response}}

## Instructions
The test-writing agent has already committed its test changes to this repository's git history. You should only focus on whether the test commit is a high-quality TDD constraint for the task. The production implementation is not expected to exist yet.

Use the available tools to inspect the work:
  - Run `git log --oneline -10` to see recent commits.
  - Run `git show HEAD` or the relevant test commit to inspect the test changes.
  - Read modified test files and nearby production code to understand what behavior is being specified.
  - Run relevant tests if useful.

Review criteria:
1. The tests must directly cover the requested behavior.
2. The tests must be meaningful enough to fail for a missing or incorrect implementation, unless the current implementation already satisfies the behavior and the agent clearly explained that.
3. The tests must not be overly coupled to private implementation details when public behavior can be tested.
4. The tests must not simply assert mocks, snapshots, or superficial existence without validating behavior.
5. The tests must not modify production logic.
6. The tests must not weaken, delete, or bypass existing coverage.
7. The tests should be scoped and stable. Reject brittle, slow, or unrelated tests unless the task requires that scope.
8. Minor naming or style suggestions alone should not block approval.

## Output Format
Start your review with either APPROVE or REQUEST_CHANGES on the first line. nothing else in this line.
Then provide specific feedback. Review comments should concisely point out the issues and provide relevant examples or context to explain the current problem.
```

### 11.7 Coder Implement Prompt

来源：从当前 OpenGiraffe `coder_implement` 迁移，并加入 TDD 测试上下文。

```text
You are a coding agent. Implement the following task completely. You must first read the AGENTS.md within the project to understand the relevant specifications and strictly enforce them.

## Task: {{title}}

## Description
{{description}}

## Delivery Requirements
The set of modifications for this task needs to form a correct git commit, so that it can directly be used as a qualified Pull Request. The commit should not include irrelevant changes such as environment setup.

## Approved Test Context
The TestWriter agent has already written tests for this task and the TestReviewer agent has approved them. You must preserve those tests and make the implementation pass them.

Test writer output:
{{test_output}}

Test review output:
{{test_review_output}}

{{dependency_context}}

## Target File
{{file_path}}:{{line_number}}

## Implementation Plan(Suggestion, not necessarily followed)
{{plan_output}}

## Requirements
1. Make the minimal necessary changes to resolve this task. If changing leads to a more optimal organization of related functions and files, and the modifications are not difficult, please apply the relevant refactoring accordingly.
2. Follow existing code style and conventions.
3. You must actually execute compilation and testing to verify the correctness of the code. Run the tests added or changed by TestWriter, plus any directly related tests.
4. Do not introduce new TODOs.
5. Use ONLY relative file paths (never absolute paths).
6. Do not delete, weaken, skip, or bypass the approved tests.
7. If you believe an approved test is invalid, explain the reason clearly and make the smallest necessary correction; otherwise preserve it.
8. Ensure the commit content is correct and all relevant code modifications eventually entered the commit. The final commit(s) submitted by you will be reviewed by the reviewer.
9. Final response must include:
   - implementation summary
   - files changed
   - exact test command(s) run
   - pass/fail result
   - commit hash or confirmation that the changes were committed
```

### 11.8 Coder Retry Feedback Prompt

来源：从当前 OpenGiraffe `coder_retry_feedback` 迁移。

```text
## Review Feedback (attempt {{attempt}})
{{review_feedback}}

Please confirm whether the issues/optimization suggestions mentioned in the review are present/feasible, and if there are no issues, modify the code according to the suggestions.
Do not ask me any questions. If you think the review comments are reasonable, make the modifications you believe are appropriate directly.
You can decide on any intermediate issues on your own and finally explain them all together.
You still need to follow the instructions in AGENTS.md, but the environment part should already be ready as you just used it.
Make sure the tests pass after the modifications and that the code is organized to be basically the clearest.
The overall code style and conventions must still be followed.
All changes must still be made as commit(s) so that the reviewer can see them.
```

### 11.9 Coder Test Failure Retry Prompt

来源：新增系统 prompt，用于 Coder 运行测试失败后的自动续写。

```text
## Test Failure Feedback (attempt {{attempt}})
The implementation did not pass the required test command.

Command:
{{test_command}}

Output:
{{test_output}}

Please diagnose the failure, update the implementation, and run the relevant tests again.

Rules:
1. Do not delete, weaken, skip, or bypass the approved tests.
2. Prefer fixing production code. Only adjust tests if they are demonstrably incorrect, and explain why.
3. Keep changes focused on the task.
4. Commit the corrected implementation changes.
5. Final response must include the new test command result.
```

### 11.10 Reviewer Review Prompt

来源：从当前 OpenGiraffe `reviewer_review` 迁移，并加入 TDD 审查要求。

```text
You are a code review agent.

## Task that was implemented
Title: {{title}}
Description: {{description}}

## Approved Test Context
The task was developed through a TDD pipeline. A TestWriter agent wrote tests first, and a TestReviewer agent approved them before implementation.

Test writer output:
{{test_output}}

Test review output:
{{test_review_output}}

## Coder's Response (from latest round)
The coding agent provided the following explanation alongside its changes. Consider these arguments on their merits — the coder may have valid reasons for certain design choices, or may be mistaken. Evaluate the actual code, not just the coder's claims.

{{coder_response}}

## Previous Review Rejections (for reference only)
The following issues were raised by reviewers in earlier round(s). The coder has since made further changes, so these complaints may already be resolved — or may have been incorrect in the first place. Use them as hints to guide your inspection, but reach your own independent conclusion.

{{prior_rejections}}

## Instructions
The coding agent has already committed its changes to this repository's git history. You should only focus on the content of the commits; the content in the working area that has not been committed is NOT part of the submission and does not require review.
Use the available tools to inspect the work:
  - Run `git log --oneline -10` to see recent commits.
  - Run `git show HEAD` and previous task commits as needed to view the content of the commits. DON'T use `git diff` because it shows the diff between the working area.
  - Read any modified files to check correctness and style.
  - Run relevant tests if needed.

When reviewing, you must strictly follow AGENTS.md and the related skills. In addition, you can perform any desired review operations to observe suspicious code and details in order to identify issues as much as possible.

TDD-specific review requirements:
1. Review both the test commit and the implementation commit.
2. Verify the implementation satisfies the approved tests without weakening them.
3. Verify the tests still meaningfully cover the requested behavior.
4. Reject hardcoded implementations that only satisfy the tests but not the real requirement.
5. Reject deleted, skipped, weakened, or bypassed tests unless the coder provided a strong and correct justification.
6. If the primary issue is bad or invalid tests, say that clearly so the orchestrator can route the task back to TestWriter.

Commit messages are not required; minor fixes that do not affect correctness or quality can be proposed, but if only such minor issues are present, the final conclusion should be APPROVE.

## Output Format
Start your review with either APPROVE or REQUEST_CHANGES on the first line. nothing else in this line.
Then provide specific feedback. Review comments should concisely point out the issues and provide relevant examples or context to explain the current problem.
```

### 11.11 Reviewer Patch Prompt

来源：从当前 OpenGiraffe `reviewer_review_patch` 迁移。v1 可选，用于 review-only 模式。

```text
You are a code review agent.

## Review Request
Title: {{title}}

## Additional Review Instructions (from user)
{{revision_context}}

## Previous Review Rejections (for reference only)
{{prior_rejections}}

## Material to Review
{{review_input}}

## Instructions
The user has provided the above material for you to review. It may be:
  - A patch / diff pasted inline
  - A GitHub PR or commit URL (use `curl` or the available tools to fetch it)
  - A description of changes with file references
Note that the content of this PR may not be present in the current codebase, so the newly added content cannot be found locally. Please combine the codebase and the actual content of the patch/PR for review.

Depending on the material type:
  - For inline patches/diffs: analyze the diff directly.
  - For GitHub URLs: fetch the diff with `curl -sL <url>.diff` (append .diff to PR URLs) and review it.
  - For file references: read the files in the repository to understand the changes.
  - You can also use `git log`, `git show`, `git diff` etc. as needed.

## Output Format
Start your review with either APPROVE or REQUEST_CHANGES on the first line. nothing else in this line.
Then provide specific feedback. Review comments should concisely point out the issues and provide relevant examples or context to explain the current problem.
```

### 11.12 Branch Slug Prompt

来源：从当前 OpenGiraffe branch slug 生成逻辑迁移。

```text
Convert the following task title into a concise git branch name slug (lowercase, hyphens only, max 5 words, no special chars, no prefix):
{{title}}

Reply with ONLY the slug, nothing else.
```

### 11.13 Continue Prompt

来源：从当前 OpenGiraffe 自动续跑语义迁移。

```text
Continue
```

## 12. Java 模块设计

### 12.1 包结构

```text
com.opengiraffe
  config
  domain
  persistence
  opencode
  agent
  git
  orchestrator
  web
  service
  util
```

### 12.2 核心类

```java
public interface Agent {
    AgentRun run(AgentContext context);
}

public final class PlannerAgent implements Agent {}
public final class TestWriterAgent implements Agent {}
public final class TestReviewerAgent implements Agent {}
public final class CoderAgent implements Agent {}
public final class ReviewerAgent implements Agent {}

public final class OpenCodeClient {
    AgentRun run(OpenCodeRequest request);
    void killTask(String taskId);
    void killAll();
}

public final class AgentOutputExtractor {
    PlannerResult extractPlannerResult(AgentRun run);
    TestWriterResult extractTestWriterResult(AgentRun run);
    ReviewVerdict extractReviewVerdict(AgentRun run);
    CoderResult extractCoderResult(AgentRun run);
}

public final class TaskExecutionService {
    void executeTask(String taskId);
    void reviseTask(String taskId, String feedback);
}

public final class WorktreeManager {
    Path createWorktree(String branchName, List<String> hooks);
    void removeWorktree(String branchName, Path worktreePath);
    GitStatus status(Path worktreePath);
    String publish(String branchName, String remote);
}

public final class Orchestrator {
    boolean dispatchTask(String taskId);
    void start();
    void stop();
}
```

## 13. Web API

v1 必须提供以下 REST API：

```text
POST   /api/tasks
GET    /api/tasks
GET    /api/tasks/{id}
POST   /api/tasks/{id}/dispatch
POST   /api/tasks/{id}/cancel
POST   /api/tasks/{id}/revise
POST   /api/tasks/{id}/clean
POST   /api/tasks/{id}/publish
GET    /api/tasks/{id}/runs
GET    /api/tasks/{id}/status
GET    /api/system/status
```

任务创建请求：

```json
{
  "title": "Fix cache invalidation bug",
  "description": "When user permissions change, cached access decisions should be invalidated.",
  "priority": "medium",
  "forceNoSplit": false
}
```

## 14. 状态恢复

系统启动时必须执行恢复：

- 将 `PLANNING`、`TEST_WRITING`、`TEST_REVIEWING`、`CODING`、`REVIEWING` 中无 active process 的任务标记为 `FAILED` 或 `PENDING`，具体策略由配置决定。
- 重建依赖图。
- 恢复 pending dispatch 队列。
- 检查 task 记录中的 branch/worktree 是否仍存在。
- 清理不存在 task 对应的孤儿运行状态，不自动删除 worktree。

默认策略：

```text
active task on startup -> FAILED with error "daemon restarted during active execution"
pending task -> keep PENDING
completed task -> keep COMPLETED
```

## 15. 验收标准

### 15.1 opencode 调用验收

- 能成功运行一次 Planner prompt。
- 能保存完整 NDJSON 输出。
- 能提取 session id。
- 不完整输出能自动 Continue。
- timeout 后进程被终止，任务进入失败。

### 15.2 TDD 流程验收

- 创建任务后会先进入 TestWriter。
- TestWriter opencode 运行失败、输出不完整、没有提交测试、或自检结果为 `INVALID` 时会回到 TestWriter 重试。
- TestWriter 新增测试可以是 `EXPECTED_RED`，即测试能运行但因生产代码未实现而失败；这种情况可以进入 TestReviewer。
- TestReviewer 拒绝后会回到 TestWriter。
- TestReviewer 通过后才会进入 Coder。
- Coder 不通过最终 Reviewer 时会重试。
- Reviewer 指出测试问题时能回到 TestWriter。
- 测试阶段或编码阶段超过各自重试次数后进入 `NEEDS_ARBITRATION`，等待人工处理。

### 15.3 并发验收

- `max_parallel_tasks=3` 时最多 3 个任务同时运行。
- 每个任务有独立 worktree。
- 子任务依赖未满足时不会被调度。

### 15.4 持久化验收

- daemon 重启后任务列表、agent runs、session ids、worktree 信息仍可查询。
- `agent_runs.prompt` 和 `agent_runs.output` 保存完整。
- JSON 字段可正确反序列化。

## 16. 实现 Task 清单

以下每个 task 都必须重复明确系统目标。统一系统目标文本为：

我们要做的是一个 Java 21 + Spring Boot 3 + MySQL 8 的后端 daemon 系统。这个系统不是业务应用，而是一个自动研发任务编排器：它默认部署在 Linux 服务器上，接收用户提交的代码修改任务，为每个任务创建独立 git worktree 和任务分支，通过 Java 的 ProcessBuilder 调用本机 opencode CLI，让不同职责的 agent 依次工作。完整流水线是 Planner 先分析任务、判断复杂度并决定是否拆成多个子任务；TestWriter 先写测试并运行测试自检；TestReviewer 审查测试质量；Coder 在已审批测试约束下实现代码并运行测试；Reviewer 最终审查测试和实现。系统必须用 MySQL 持久化任务、agent 运行记录、prompt、opencode 原始输出、session id、状态流转、worktree 路径和分支名，并支持并发调度、失败重试、超过次数后进入 NEEDS_ARBITRATION 等待人工处理。

这里的“OpenGiraffe Java 版本”不能理解成普通业务系统。它具体指一个自动研发任务编排 daemon：用户提交代码修改任务后，Java 后端把任务保存到 MySQL，按状态机调度多个 agent，通过 `ProcessBuilder` 调本机 `opencode run --format json`，让 opencode 在目标代码仓库的独立 git worktree 中读代码、写测试、写实现、做审查并提交 git commit。每个 task 的描述都必须自包含；即使执行 agent 没读过本文档其他章节，也要能从该 task 中知道目标系统、模块边界、要实现的行为和验收方式。

### Task 01: 初始化 Java Spring Boot 工程

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要从空项目或现有空目录中搭建可启动的 Java 后端工程。这个后端不是业务 CRUD 应用，而是后续所有多 agent 调度能力的宿主进程。请创建 Java 21 + Spring Boot 3 工程，优先使用 Maven，除非仓库已有 Gradle 约定。依赖至少包含 Spring Web、Validation、MySQL JDBC Driver、Spring JDBC 或 Spring Data JPA、Flyway 或 Liquibase、Jackson、Actuator、JUnit 5、Mockito/AssertJ。

必须建立包结构 `config/domain/persistence/opencode/agent/git/orchestrator/web/service/util`，并创建一个可运行的主类。启动后即使还没有业务接口，也应能通过 health endpoint 或简单启动测试证明 daemon 能正常启动。不要实现具体 agent 流程，不要连接真实 opencode，不要写无关 UI。验收标准：`mvn test` 或等价命令通过，应用可以启动，README 或配置模板中说明需要 Java 21。

### Task 02: 实现配置加载

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现系统配置层，让 Java daemon 能从 YAML 中读取运行参数。配置必须覆盖：HTTP 服务地址、目标代码仓库路径、base branch、worktree 存放目录、worktree hook、opencode config 路径、opencode timeout、max continues、Planner/TestWriter/TestReviewer/Coder/Reviewer 的模型配置、并发数、测试阶段重试次数、编码阶段重试次数、MySQL 连接、发布 remote 和日志级别。

模型配置必须统一抽象为 `ModelSpec`，字段包括 `model`、`variant`、`agent`，其中 `variant` 和 `agent` 可为空。启动时必须校验关键路径：repo path 必须存在且是 git 仓库，worktree dir 不存在时可创建，opencode config path 必须能解析为绝对路径。不要在本任务中实现数据库表或 agent 运行逻辑。验收标准：提供配置类、默认配置模板、配置缺失/非法时的明确错误，并有单元测试覆盖 YAML 绑定和默认值。

### Task 03: 建立 MySQL Schema 和迁移机制

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要把系统持久化从设计落到 MySQL。请使用 Flyway 或 Liquibase 创建迁移脚本，至少包含 `tasks`、`agent_runs`、`task_events`、`app_state` 四张表。所有表必须使用 InnoDB、`utf8mb4`，时间字段使用毫秒精度，长文本字段能保存完整 prompt 和 opencode 原始输出。

`tasks` 要保存任务状态、标题、描述、父任务、依赖、worktree 路径、branch、复杂度、各阶段输出、retry count、session ids JSON、reviewer results JSON。`agent_runs` 要保存每次 agent 调 opencode 的 prompt、output、exit code、duration、session id、continue count。实现 repository 层，能保存、按 id 查询、按状态查询、更新和删除。验收标准：迁移脚本可在空 MySQL 库执行，repository 测试能通过 Testcontainers MySQL 或本地测试替身。

### Task 04: 实现领域模型

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要定义整个系统的领域模型，让后续 agent 和 orchestrator 都使用同一套对象。必须实现 `Task`、`AgentRun`、`TaskStatus`、`TaskPriority`、`TaskSource`、`ModelSpec`、`ReviewerResult`、`OpenCodeRequest`、`OpenCodeEvent`、`PlannerResult`、`ReviewVerdict` 等类型。

`TaskStatus` 必须包含 TDD 状态：`PENDING`、`PLANNING`、`TEST_WRITING`、`TEST_WRITE_FAILED`、`TEST_REVIEWING`、`TEST_REVIEW_FAILED`、`CODING`、`REVIEWING`、`REVIEW_FAILED`、`NEEDS_ARBITRATION`、`COMPLETED`、`FAILED`、`CANCELLED`。`Task` 必须能保存 `testRetryCount` 和 `codeRetryCount`，测试阶段的失败包括 TestWriter 自检失败和 TestReviewer 拒绝。验收标准：JSON 字段能稳定序列化/反序列化，枚举非法值有明确错误，领域对象有单元测试。

### Task 05: 实现 OpenCodeClient

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现系统最底层的 opencode 调用客户端。Java daemon 不直接调用任何大模型 SDK，所有模型交互都必须通过本机命令行：`opencode run --model <model> --dir <worktree> --format json <prompt>`。请用 `ProcessBuilder` 构造命令，设置工作目录为任务 worktree，注入 `OPENCODE_CONFIG`，支持可选 `--session`、`--variant`、`--agent`。

必须实现 timeout、stdout 完整收集、stderr 记录、exit code 记录、duration 记录、task id 到进程的映射、`killTask(taskId)`、`killAll()`。返回值必须是 `AgentRun`，其中保存原始 prompt 和原始 output。不要在本任务中解析 Planner JSON 或 Reviewer verdict。验收标准：用 fake opencode 脚本测试正常输出、非零退出、timeout、kill 和环境变量注入。

### Task 06: 实现 opencode NDJSON 解析

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现 opencode NDJSON 输出解析器和自动续跑机制。opencode `--format json` 会输出多行 JSON event，系统必须保留 raw output，同时解析出 session id、text event 文本、最后一个可读 step、最后 stop step 文本、tool 调用摘要和输出是否完整。

解析必须容错：空行跳过；单行不是合法 JSON 时跳过该行并记录 debug/warn 日志；未知字段必须忽略；不能因为 opencode 某个版本增加字段或输出一行非 JSON 诊断信息就让整个 AgentRun 解析失败。

完整性的判定规则：最后一个 step 必须有 `step_finish` 且 `reason` 为 `stop`。如果 exit code 非 0 或输出不完整，但已经拿到 session id，`OpenCodeClient` 必须自动再次调用同一模型、同一 worktree、同一 variant/agent，并传入 `--session <sessionId> "Continue"`，最多 `max_continues` 次。验收标准：单元测试覆盖正常 stop、无 stop、无 session、非法 JSON 行被跳过、未知字段被忽略、自动 Continue 拼接输出。

### Task 07: 实现 PromptTemplateRegistry

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现 prompt 模板中心，避免 agent 代码里散落大段字符串。请建立 `PromptTemplateRegistry` 或等价组件，集中保存本文档第 11 章定义的所有 prompt：系统通用片段、Planner analyze/split、Planner no-split、TestWriter、TestWriter retry、TestReviewer、Coder implement、Coder retry、Coder test-failure retry、Reviewer review、Reviewer patch、BranchSlug、Continue。

模板渲染必须支持 `{{variable_name}}` 占位符，缺失变量要抛出清晰异常，不能静默输出未替换变量。TestWriter prompt 必须包含 `PASS / EXPECTED_RED / INVALID` 分类要求。验收标准：每个模板都有快照测试，关键变量缺失测试会失败，agent 类只能通过 registry 获取 prompt。

### Task 08: 实现 BaseAgent 和各 Agent 类

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现 agent 抽象层。所有 agent 都只是 opencode 的不同职责包装器，不直接调用模型 API。请实现 `BaseAgent`，负责接收 `ModelSpec`、`OpenCodeClient`、prompt、worktree、task id、session id，并返回 `AgentRun`。在其上派生 `PlannerAgent`、`TestWriterAgent`、`TestReviewerAgent`、`CoderAgent`、`ReviewerAgent`。

每个派生 agent 只做三件事：准备对应 prompt、调用 `OpenCodeClient`、把 AgentRun 交给集中结构化提取器得到该阶段结果。Planner JSON、TestReviewer/Reviewer 首行 verdict、TestWriter 测试结果分类、Coder 最终说明等解析逻辑不应散落在各 agent 类中，应由 `AgentOutputExtractor` 或等价组件统一管理。不要在 agent 类里做调度、状态流转或数据库事务。验收标准：用 fake OpenCodeClient 单元测试每个 agent 的 prompt 变量和解析结果。

### Task 09: 实现 Planner JSON 解析和拆分逻辑

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现 Planner 阶段的结构化结果处理。Planner 的职责是判断任务复杂度，并决定任务是否应拆成多个子任务。请解析 Planner 最终文本中的 JSON 对象，支持 `complexity`、`split`、`reason`、`plan`、`sub_tasks`、`priority`、`depends_on`。`complexity` 只能是 `very_complex/complex/medium/simple`。

当 `split=false` 时，把 `plan` 保存到当前 task。`split=true` 时，当前 task 作为父任务等待，不创建 worktree；系统要创建多个子任务，子任务描述必须包含父任务整体目标、该子任务边界、依赖关系和验收要求。`depends_on` 是 Planner 输出中的 0 基下标，必须转换为真实子任务 id。解析失败时重试 Planner 一次，仍失败则任务失败并保存错误。验收标准：测试覆盖单任务、拆分任务、非法 JSON、空 sub_tasks、非法 depends_on。

### Task 10: 实现 Git WorktreeManager

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现 git worktree 隔离层。系统每执行一个开发子任务，都必须基于配置的 repo path 和 base branch 创建独立分支和 worktree，避免并发任务互相污染。请实现 fetch base branch、生成 worktree 路径、`git worktree add -b <branch> <path> origin/<baseBranch>`、复制 repo 根目录中的 `AGENTS.md` 和 `hooks/`、按配置执行 worktree hooks。

还要实现查询 `git status --short --branch`、查询 changed files、删除 worktree、删除本地 branch、publish branch 到 remote。删除操作必须校验目标路径在配置的 worktree_dir 下，不能删除任意路径。所有 git 命令必须有 timeout，失败时保留 stdout/stderr。验收标准：用临时 git repo 测试创建、查询、删除和发布命令构造。

### Task 11: 实现 TaskExecutionService 主状态机

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现单个开发任务的主状态机，这是系统的核心。状态机必须严格按 TDD 顺序推进：`PLANNING -> TEST_WRITING -> TEST_REVIEWING -> CODING -> REVIEWING -> COMPLETED`。Planner 可以把任务拆成子任务；未拆分任务才创建 worktree 并进入测试阶段。

每个阶段开始前和结束后都要保存 task 状态、时间戳、agent run、session id、阶段输出和错误信息。任何阶段执行前都必须检查任务是否已取消。异常不能只写日志，必须落库到 task error 和 task_events。TestWriter 自检失败应进入 `TEST_WRITE_FAILED` 并按测试阶段重试规则处理；TestReviewer 拒绝应进入 `TEST_REVIEW_FAILED`；Reviewer 拒绝应进入 `REVIEW_FAILED`。验收标准：用 fake agent 测试成功链路、Planner 拆分链路、异常失败链路、取消链路。

### Task 12: 实现 TestWriter/TestReviewer 重试循环

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现测试阶段完整闭环，而不是只调用一次 TestWriter。TestWriter 写完测试后必须有测试相关 git commit，并且最终响应必须给出测试命令和 `PASS / EXPECTED_RED / INVALID` 分类。`PASS` 表示当前实现已经满足测试；`EXPECTED_RED` 表示测试能运行但因功能未实现而红，这是 TDD 可接受结果；`INVALID` 表示测试自身不可用。

如果 TestWriter 的 opencode run 失败、输出不完整、没有产生测试 commit、没有结果分类、或分类为 `INVALID`，系统必须把反馈传回 TestWriter 重做。TestReviewer 输出 `REQUEST_CHANGES` 时，也必须回到 TestWriter 重做。两类失败共用 `testRetryCount` 和 `max_test_retries`，超过次数后状态变为 `NEEDS_ARBITRATION`，不再自动继续。验收标准：测试覆盖 TestWriter invalid 重试、TestReviewer 拒绝重试、EXPECTED_RED 可进入 TestReviewer、超过次数进入仲裁。

TestWriter 重试的 session 策略允许配置：可以复用上一轮 TestWriter session，也可以默认新建 session。新建 session 是可接受的，因为测试无效或测试策略错误时，重新审视需求通常比沿用错误上下文更稳。无论是否复用 session，上一轮反馈必须通过 retry prompt 明确传入。

### Task 13: 实现 Coder/Reviewer 重试循环

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现编码和最终审查闭环。只有 TestReviewer 审批通过后，任务才能进入 Coder。Coder 必须在已审批测试约束下实现生产代码，不能删除、跳过、弱化测试。Coder 完成后必须有实现相关 git commit，并在输出中记录运行过的测试命令和结果。

最终 Reviewer 可以配置多个模型或多个 agent，所有 Reviewer 都必须输出 `APPROVE`，任务才算完成。任一 Reviewer 输出 `REQUEST_CHANGES` 时，系统必须把最新反馈传回 Coder，并优先复用最近一次 coder session：调用 opencode 时传入 `--session <coderSessionId>` 和 coder retry prompt，让 opencode 保留前一轮已读文件、工具调用和实现上下文。如果没有 coder session id，才允许新建 session 并记录 task_event。

注意区分测试阶段和编码阶段：TestWriter 重试可以复用 session，也可以按配置新建 session；Coder 重试默认必须复用 session。超过 `max_code_retries` 后任务进入 `NEEDS_ARBITRATION`。验收标准：测试覆盖单 reviewer 通过、多 reviewer 全通过、首个 reviewer 拒绝短路、拒绝后回 Coder 且复用 coder session、缺失 session 时记录事件并新建 session、超过次数仲裁。

### Task 14: 实现 Reviewer 路由到 TestWriter 的判定

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现最终 Reviewer 拒绝后的路由判断。Reviewer 拒绝不一定总是实现问题：如果 Reviewer 明确指出测试无效、测试过弱、测试与需求不相关、测试被 Coder 删除/跳过/弱化、测试 fixture 错误，系统应该回到 TestWriter，而不是只让 Coder 修改实现。

v1 可先实现启发式分类：review 文本中出现 `test is invalid`、`weak test`、`insufficient test coverage`、`test was weakened`、`skipped test`、`assertion is wrong`、`fixture is wrong` 等含义时归类为 `test_issue`，回到 TestWriter 并消耗测试阶段重试次数；其他归类为 `implementation_issue`，回到 Coder。分类结果必须写入 task_events。验收标准：单元测试覆盖 test_issue、implementation_issue、模糊反馈和大小写变体。

### Task 15: 实现 Orchestrator 并发调度

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现 daemon 级任务调度器。用户或系统可以把多个 pending task 派发给 orchestrator，但同时运行的任务数不能超过 `max_parallel_tasks`。请使用固定大小 `ExecutorService`，维护 `taskId -> Future` 的 running map，防止同一任务重复调度。

当并发槽满时，任务不能失败，应进入 pending dispatch 队列，等待其他任务结束后自动调度。调度前必须检查依赖是否满足、任务是否仍存在、状态是否允许运行、是否已经取消。任务结束后必须从 running map 移除，并触发 pending 队列刷新。验收标准：测试覆盖并发上限、重复 dispatch、队列等待、任务完成后自动补位、取消任务不再调度。

### Task 16: 实现依赖图 DependencyTracker

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现 Planner 拆分子任务后的依赖管理。Planner 输出的 `depends_on` 是子任务数组下标，系统保存时要转换成真实 task id。DependencyTracker 必须能判断某个子任务是否被依赖阻塞；被阻塞时不能创建 worktree、不能启动 agent。

当一个子任务 `COMPLETED` 后，系统要释放依赖它的下游子任务；如果依赖任务失败、取消或进入仲裁，下游任务和父任务要展示阻塞原因。daemon 重启后，依赖图必须从 MySQL 中的 parent_id 和 depends_on_json 重建，不能只存在内存里。验收标准：测试覆盖并行子任务、串行依赖、依赖失败、重启重建和父任务完成状态聚合。

### Task 17: 实现 REST API

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现 REST API，让外部 UI 或 CLI 可以控制这个自动研发 daemon。必须提供任务创建、任务列表、任务详情、dispatch、cancel、revise、clean、publish、agent runs 查询、系统状态查询。API 返回不能只给数据库字段，要提供面向调试的聚合信息：当前状态、父子任务、依赖、worktree、branch、test/code retry count、agent session ids、最近错误、最近 agent run 摘要。

创建任务 API 必须接收 title、description、priority、forceNoSplit。dispatch API 只负责把任务交给 orchestrator，不应同步跑完整流程。cancel API 必须触发 opencode 进程终止。revise API 用于人工仲裁后重新给反馈。验收标准：Web 层测试覆盖参数校验、任务不存在、状态不允许操作、正常返回结构。

### Task 18: 实现任务取消和 opencode 进程清理

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现可靠取消。用户取消任务时，系统必须把 task 标记为 `CANCELLED`，并通过 `OpenCodeClient.killTask(taskId)` 终止该任务当前关联的 opencode 进程。取消不能删除 worktree，也不能删除 agent run，因为这些内容用于排查问题。

状态机每进入一个阶段前都必须检查取消状态，避免进程已杀但后续阶段继续运行。daemon stop 时必须调用 `killAll()`，终止所有 active opencode 进程，并安全关闭线程池。验收标准：fake long-running opencode 能被取消；取消后状态不会继续推进；取消任务仍可查询历史输出；stop 会清理所有进程。

### Task 19: 实现 worktree 清理和分支发布

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现任务产物生命周期管理。clean 操作用于删除某个已完成、失败、取消或仲裁任务的 worktree 和本地分支；publish 操作用于把 `COMPLETED` 任务的分支 push 到配置的 remote。二者都必须先检查任务不在运行中。

clean 删除前必须校验 worktree 路径在配置的 worktree_dir 下，并优先使用 `git worktree remove --force`，再 prune，最后删除本地 branch。不得对未知路径执行递归删除。publish 应执行 `git push --force --set-upstream <remote> <branch>`，成功后保存 `published_at`。验收标准：测试覆盖不能 clean 运行中任务、不能 publish 未完成任务、路径越界保护、成功 clean 后 task 资源字段更新。

### Task 20: 实现 daemon 启动恢复

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现 daemon 重启恢复。系统重启时 MySQL 中可能存在上次运行遗留的 `PLANNING`、`TEST_WRITING`、`TEST_REVIEWING`、`CODING`、`REVIEWING` 等 active 状态任务，但 Java 进程和 opencode 子进程已经不存在。默认策略是把这些 active 状态任务标记为 `FAILED`，error 写明 daemon restarted during active execution，并保留 worktree 供人工检查。

同时必须重建依赖图，恢复 pending dispatch 语义，核对每个任务记录的 branch/worktree 是否仍存在，生成资源快照供 API 展示。恢复动作必须写入 task_events，方便用户知道哪些状态是重启恢复造成的。验收标准：测试覆盖 active task 恢复、pending task 保持、completed task 保持、依赖图重建。

### Task 21: 实现日志与可观测性

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现可观测性。系统跑的是长时间 agent 任务，失败排查高度依赖日志。请使用结构化日志或统一 MDC，关键日志必须包含 task id、agent type、model、session id、worktree path、branch、duration、exit code、retry count。

接入 Spring Boot Actuator，至少提供 health、info、metrics。health 应检查数据库连接和基础配置，不要求检查真实 opencode provider。日志中不得输出 MySQL 密码、token、完整环境变量或其他敏感信息。验收标准：单元或集成测试覆盖敏感字段脱敏，手动启动时能看到结构化关键字段。

### Task 22: 编写单元测试

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要补齐核心单元测试，确保后续让 agent 自动改代码时不会破坏状态机。必须覆盖配置加载、ModelSpec 解析、prompt 渲染和缺失变量、opencode NDJSON 解析、自动 Continue 判定、AgentOutputExtractor、Planner JSON 解析、TestWriter `PASS/EXPECTED_RED/INVALID` 分类解析、Reviewer verdict 解析、Reviewer 路由分类、状态机转移、依赖图、repository JSON 序列化。

所有模型 I/O 必须 mock，不允许单元测试调用真实 opencode 或真实 LLM。测试命名要清晰表达行为，失败时能定位具体规则。验收标准：单元测试在无 opencode、无 MySQL 服务时可运行；需要数据库的测试应使用嵌入替身或单独归为集成测试。

### Task 23: 编写集成测试

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现端到端集成测试，但仍不能调用真实模型。请使用 Testcontainers MySQL 启动真实 MySQL，使用 fake opencode 可执行文件模拟 opencode CLI 的 NDJSON 输出、session id、失败、timeout 和 Continue。集成测试应尽量跑真实 Spring Boot 上下文、真实 repository、真实状态机。

必须覆盖：单任务成功完成；TestWriter 第一次输出 `INVALID` 后被打回重做；TestReviewer 拒绝后回 TestWriter；Coder 测试失败后回 Coder；Reviewer 拒绝后回 Coder；Reviewer 指出测试问题后回 TestWriter；Planner split 创建子任务；依赖子任务按顺序调度；取消任务终止 fake opencode。验收标准：集成测试可在 CI 中稳定运行，fake opencode 脚本随测试资源提交。

### Task 24: 编写 Linux 部署脚本和 systemd 文件

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要让系统能在 Linux 服务器上部署。请提供部署文档、目录结构建议、`config.yaml` 模板、`opencode.json` 放置说明、MySQL 初始化 SQL 或迁移执行说明、systemd service 文件。服务必须建议使用非 root 用户运行，例如 `opengiraffe`。

文档必须列出前置依赖：Java 21、git、opencode CLI、MySQL 8、目标 repo 的访问权限、SSH key 或 token、worktree 目录写权限。systemd 文件要设置 WorkingDirectory、ExecStart、Restart、User、Environment 或配置文件路径。验收标准：按文档在 Linux 上能启动服务，服务日志能显示配置加载成功和 HTTP 端口。

### Task 25: 编写最小 Dashboard

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现最小可用 Dashboard，服务于调试和日常操作，不做复杂设计系统。页面必须能查看任务列表、任务状态、父子任务、retry count、worktree、branch、最近错误；任务详情页必须能查看各阶段输出和 agent runs，包括 prompt、原始 output、session id、duration、exit code。

Dashboard 必须提供 dispatch、cancel、revise、clean、publish 操作入口，并对不可用操作禁用或显示原因。状态刷新可以用轮询实现，不要求 WebSocket。不要把 Dashboard 做成营销页。验收标准：在本地启动后能通过浏览器完成创建任务、派发任务、查看 fake agent 输出、取消任务和查看详情。

### Task 26: 实现 AgentOutputExtractor 结构化输出提取

任务描述：

系统背景：我们正在实现一个 Java 21 + Spring Boot 3 + MySQL 8 的自动研发任务编排 daemon。它运行在 Linux 上，接收代码修改任务，为每个任务创建独立 git worktree，通过 ProcessBuilder 调用本机 opencode CLI，并按 Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer 的 TDD 多 agent 流程推进；所有任务状态、prompt、opencode 原始输出、session id、worktree 和分支信息都必须持久化到 MySQL，失败会按阶段重试，超过次数进入 NEEDS_ARBITRATION 等待人工。

本任务要实现集中式 agent 输出提取组件，避免 Planner、TestWriter、Coder、Reviewer 各自用零散字符串逻辑解析 opencode 输出。请实现 `AgentOutputExtractor` 或等价服务，输入为 `AgentRun` 和 agent 类型，输出为结构化结果对象。它必须基于 `OpenCodeClient` 或 NDJSON 解析器提取出的最终文本工作，同时保留 raw output 供排查。

必须支持：

- Planner：从最终文本中提取 JSON 对象，解析为 `PlannerResult`，包含 complexity、split、plan、sub_tasks、depends_on。
- TestWriter：提取测试文件列表、测试命令、结果分类 `PASS / EXPECTED_RED / INVALID`、提交信息或 commit hash、简短测试说明。
- TestReviewer：提取首行 verdict `APPROVE / REQUEST_CHANGES` 和正文反馈。
- Coder：提取实现摘要、修改文件列表、测试命令、测试结果、提交信息或 commit hash。
- Reviewer：提取首行 verdict、正文反馈，并分类为 `implementation_issue / test_issue / unclear`。

提取规则必须宽容：如果模型输出没有完全遵循格式，组件应返回带 warning 的部分结果，而不是丢弃 raw output；只有 Planner JSON 无法解析、Reviewer verdict 缺失、TestWriter 分类缺失这类会影响状态机的关键字段，才应返回明确错误给调用方。验收标准：单元测试覆盖规范输出、缺少字段、额外 markdown、非法 JSON、多个 verdict 关键词、TestWriter 三种分类、Coder 文件列表提取和 reviewer test_issue 分类。

## 17. 风险与对策

### 17.1 opencode 输出格式变化

风险：opencode NDJSON 字段变化导致解析失败。

对策：保留 raw output，解析器必须宽容；关键字段缺失时降级为 raw 文本展示。

### 17.2 TestWriter 写出无效测试

风险：TDD 流程被弱测试污染。

对策：TestReviewer 独立审批；最终 Reviewer 再次审查测试。

### 17.3 Coder 弱化测试

风险：Coder 删除或跳过测试。

对策：Coder prompt 明确禁止；Reviewer 必须检查测试提交和实现提交差异。

### 17.4 并发 worktree 冲突

风险：分支名冲突、依赖分支 cherry-pick 冲突。

对策：分支名包含 task id；依赖冲突进入失败或仲裁，不自动强行解决。

### 17.5 长时间运行进程泄漏

风险：opencode 进程超时或 daemon 停止后残留。

对策：OpenCodeClient 维护 task id 到 Process 映射，stop/cancel 必须 kill。

## 18. v1 完成定义

v1 只有在以下条件全部满足时才算完成：

- 能在 Linux 上启动 Java daemon。
- 能连接 MySQL 并自动建表。
- 能创建任务并完整执行 `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer`。
- 能通过 fake opencode 集成测试验证状态机。
- 能用真实 opencode 在测试 repo 上完成一个简单开发任务。
- 能查询所有 agent run prompt 和 output。
- 能取消正在运行任务。
- 能清理 worktree。
- 能发布完成分支。
