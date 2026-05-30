# TDDForge

TDD-based Auto R&D Task Orchestration Daemon built with Java 21 + Spring Boot 3 + MySQL 8.

TDDForge automates software development tasks through a multi-agent TDD pipeline. It receives code modification tasks, creates isolated git worktrees for each task, and drives them through a `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` pipeline via the `opencode` CLI.

## Architecture Overview

```
User submits task
  -> Planner analyzes complexity and decides whether to split
  -> TestWriter writes tests first (TDD)
  -> TestReviewer approves test quality
  -> Coder implements code to pass tests
  -> Reviewer reviews both tests and implementation
  -> Task completed / retry / human arbitration
```

Key features:
- Multi-agent TDD pipeline with automatic retries
- Isolated git worktrees for each task
- Concurrent task execution (configurable parallelism)
- MySQL persistence for tasks, agent runs, prompts, and outputs
- REST API for task management
- Web dashboard for monitoring
- systemd deployment support

## Prerequisites

| Dependency | Version | Purpose |
|---|---|---|
| Java (OpenJDK) | 21+ | Runtime for the Spring Boot daemon |
| Maven | 3.8+ | Build tool |
| Git | 2.30+ | Worktree creation, branch management |
| opencode CLI | latest | Agent execution via `opencode run` |
| MySQL | 8.0+ | Persistence for tasks, agent runs, events |

## Build and Test

```bash
# Run all tests
mvn clean test

# Build executable JAR
mvn clean package -DskipTests

# Build with tests
mvn clean package
```

The JAR will be created at `target/tddforge-0.0.1-SNAPSHOT.jar`.

## Run Locally

```bash
# Run with Maven
mvn spring-boot:run

# Run the built JAR directly
java -jar target/tddforge-0.0.1-SNAPSHOT.jar
```

The application starts on port 8778 by default. Verify it is running:

```bash
curl http://localhost:8778/actuator/health
```

Expected response:

```json
{
  "status": "UP",
  "components": {
    "baseConfiguration": { "status": "UP" },
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" }
  }
}
```

## Configuration

TDDForge loads configuration from `/etc/opengiraffe-java/config.yaml`. Copy the example template:

```bash
sudo mkdir -p /etc/opengiraffe-java
sudo cp doc/config/example/config.yaml /etc/opengiraffe-java/config.yaml
sudo chown opengiraffe:opengiraffe /etc/opengiraffe-java/config.yaml
sudo chmod 600 /etc/opengiraffe-java/config.yaml
```

### Key Configuration Sections

```yaml
server:
  address: 0.0.0.0
  port: 8778

repo:
  path: /data/repos/target-project      # Path to your target repository
  base_branch: master                    # Base branch for worktrees
  worktree_dir: /data/opengiraffe/worktrees

opencode:
  config_path: /etc/opengiraffe-java/opencode.json
  timeout_seconds: 3600
  max_continues: 8
  planner:
    model: opencode/qwen3.6-plus-free
  test_writer:
    model: opencode/qwen3.6-plus-free
  coder_default:
    model: opencode/qwen3.6-plus-free
  reviewers:
    - model: opencode/qwen3.6-plus-free

orchestrator:
  max_parallel_tasks: 3
  max_test_retries: 2
  max_code_retries: 4
  poll_interval_seconds: 30

mysql:
  url: jdbc:mysql://127.0.0.1:3306/opengiraffe_java?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
  username: opengiraffe
  password: <YOUR_MYSQL_PASSWORD>

publish:
  remote: origin

logging:
  level:
    root: INFO
```

### opencode Configuration

Place the opencode CLI configuration at the path specified by `opencode.config_path` (default: `/etc/opengiraffe-java/opencode.json`):

```json
{
  "provider": {
    "name": "your-provider",
    "apiKey": "<YOUR_API_KEY>"
  }
}
```

Set appropriate permissions:

```bash
sudo chown opengiraffe:opengiraffe /etc/opengiraffe-java/opencode.json
sudo chmod 600 /etc/opengiraffe-java/opencode.json
```

## MySQL Setup

### Create Database and User

```sql
CREATE DATABASE opengiraffe_java CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'opengiraffe'@'localhost' IDENTIFIED BY '<YOUR_MYSQL_PASSWORD>';
GRANT ALL PRIVILEGES ON opengiraffe_java.* TO 'opengiraffe'@'localhost';
FLUSH PRIVILEGES;
```

### Schema Migration

TDDForge uses Flyway for automatic schema migration. When the application starts with a valid MySQL connection, Flyway will automatically create all required tables (`tasks`, `agent_runs`, `task_events`, `app_state`).

No manual SQL execution is required. The migration scripts are embedded in the JAR at `classpath:db/migration/`.

### Verify Tables

After the first successful startup:

```bash
mysql -u opengiraffe -p -e "SHOW TABLES;" opengiraffe_java
```

Expected tables:
- `agent_runs`
- `app_state`
- `flyway_schema_history`
- `task_events`
- `tasks`

## Deployment (Linux systemd)

### 1. Create System User

```bash
sudo useradd --system --create-home --shell /bin/false opengiraffe
sudo mkdir -p /opt/tddforge /etc/opengiraffe-java /data/opengiraffe/worktrees
sudo chown -R opengiraffe:opengiraffe /opt/tddforge /etc/opengiraffe-java /data/opengiraffe
```

### 2. Deploy the JAR

```bash
# Build
mvn clean package -DskipTests

# Copy to server
scp target/tddforge-0.0.1-SNAPSHOT.jar user@server:/opt/tddforge/tddforge.jar

# Set ownership
sudo chown opengiraffe:opengiraffe /opt/tddforge/tddforge.jar
```

### 3. Install systemd Service

```bash
sudo cp deploy/systemd/tddforge.service /etc/systemd/system/tddforge.service
sudo systemctl daemon-reload
sudo systemctl enable tddforge
sudo systemctl start tddforge
```

### 4. Check Status

```bash
sudo systemctl status tddforge
```

### 5. View Logs

```bash
# Follow live logs
sudo journalctl -u tddforge -f

# View recent logs
sudo journalctl -u tddforge -n 100
```

## REST API

| Method | Endpoint | Description |
|---|---|---|
| POST | `/api/tasks` | Create a new task |
| GET | `/api/tasks` | List all tasks |
| GET | `/api/tasks/{id}` | Get task details |
| POST | `/api/tasks/{id}/dispatch` | Dispatch a task |
| POST | `/api/tasks/{id}/cancel` | Cancel a task |
| POST | `/api/tasks/{id}/revise` | Revise a task with feedback |
| POST | `/api/tasks/{id}/clean` | Clean task worktree |
| POST | `/api/tasks/{id}/publish` | Publish task branch |
| GET | `/api/tasks/{id}/runs` | Get task agent runs |
| GET | `/api/tasks/{id}/status` | Get task status |
| GET | `/api/system/status` | Get system status |

### Create Task Example

```bash
curl -X POST http://localhost:8778/api/tasks \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Fix cache invalidation bug",
    "description": "When user permissions change, cached access decisions should be invalidated.",
    "priority": "MEDIUM",
    "forceNoSplit": false
  }'
```

## Dashboard

Access the web dashboard at `http://localhost:8778/dashboard.html` after starting the application.

## Package Structure

```
com.tddforge
  config          - Configuration classes and validation
  domain          - Domain models (Task, AgentRun, etc.)
  persistence     - JPA repositories and converters
  opencode        - OpenCode CLI client
  agent           - Agent implementations (Planner, TestWriter, etc.)
  git             - Git worktree management
  orchestrator    - Task orchestration and scheduling
  web             - REST API controllers
  service         - Business logic services
  util            - Utility classes
```

## Troubleshooting

### Application fails to start with "Repo path does not exist"

Ensure `repo.path` in `config.yaml` points to an existing git repository.

### MySQL connection refused

Verify MySQL is running and the user can connect:

```bash
mysql -u opengiraffe -p -h 127.0.0.1 opengiraffe_java
```

### opencode CLI not found

Ensure `opencode` is installed and available in the system PATH. When running under systemd, you may need to specify the full path in the service file:

```ini
Environment="PATH=/usr/local/bin:/usr/bin:/bin"
```

## Security Notes

- Configuration files containing passwords should have `chmod 600` permissions.
- Never commit `config.yaml` or `opencode.json` with real credentials to version control.
- The daemon runs as a non-root user (`opengiraffe`) in production.
