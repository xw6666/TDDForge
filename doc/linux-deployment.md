# TDDForge Linux Deployment Guide

## 1. Overview

TDDForge is a Java 21 + Spring Boot 3 + MySQL 8 daemon that orchestrates automated TDD coding tasks. It runs on Linux, creates isolated git worktrees for each task, and drives them through a `Planner -> TestWriter -> TestReviewer -> Coder -> Reviewer` pipeline via the `opencode` CLI.

This guide covers deploying TDDForge on a Linux server with systemd.

## 2. Prerequisites

Before deploying, ensure the following are available on the target server:

| Dependency | Version | Purpose |
|---|---|---|
| Java (OpenJDK) | 21+ | Runtime for the Spring Boot daemon |
| Git | 2.30+ | Worktree creation, branch management |
| opencode CLI | latest | Agent execution via `opencode run` |
| MySQL | 8.0+ | Persistence for tasks, agent runs, events |
| Target repository | — | The code repository TDDForge will work on |

Additional requirements:

- **Repository access**: The `opengiraffe` user must have read/write access to the target repository. This typically means an SSH key (`~/.ssh/id_ed25519`) or a Git credential/token configured for the user.
- **Worktree directory write permission**: The `opengiraffe` user must be able to create subdirectories in the configured `worktree_dir`.
- **opencode configuration**: An `opencode.json` must be placed at the configured path (see Section 6).

## 3. Recommended Directory Structure

```
/opt/tddforge/                          # Application home
  tddforge.jar                          # Built JAR file

/etc/opengiraffe-java/                  # Configuration directory
  config.yaml                           # Main configuration
  opencode.json                         # opencode CLI configuration

/data/repos/target-project/             # Target git repository (configurable)
  .git/
  AGENTS.md
  src/

/data/opengiraffe/worktrees/            # Worktree root (configurable)

/var/log/tddforge/                      # Optional: symlink or log directory
```

All directories under `/opt/tddforge`, `/etc/opengiraffe-java`, and `/data/opengiraffe` should be owned by the `opengiraffe` user.

## 4. System User Setup

Create a dedicated non-root user for the daemon:

```bash
sudo useradd --system --create-home --shell /bin/false opengiraffe
```

Create required directories:

```bash
sudo mkdir -p /opt/tddforge
sudo mkdir -p /etc/opengiraffe-java
sudo mkdir -p /data/opengiraffe/worktrees
sudo chown -R opengiraffe:opengiraffe /opt/tddforge /etc/opengiraffe-java /data/opengiraffe
```

## 5. Build and Deploy the JAR

From a development machine or CI pipeline:

```bash
mvn clean package -DskipTests
```

Copy the JAR to the server:

```bash
scp target/tddforge-0.0.1-SNAPSHOT.jar user@server:/opt/tddforge/tddforge.jar
```

On the server, set ownership:

```bash
sudo chown opengiraffe:opengiraffe /opt/tddforge/tddforge.jar
```

## 6. Configuration

### 6.1 config.yaml

Copy the example configuration template to `/etc/opengiraffe-java/config.yaml`:

```bash
sudo cp doc/config/example/config.yaml /etc/opengiraffe-java/config.yaml
sudo chown opengiraffe:opengiraffe /etc/opengiraffe-java/config.yaml
sudo chmod 600 /etc/opengiraffe-java/config.yaml
```

Edit the file to match your environment. Key fields to configure:

```yaml
server:
  address: 0.0.0.0
  port: 8778

repo:
  path: /data/repos/target-project      # Path to your target repository
  base_branch: master                    # Base branch to create worktrees from
  worktree_dir: /data/opengiraffe/worktrees

opencode:
  config_path: /etc/opengiraffe-java/opencode.json
  timeout_seconds: 3600
  max_continues: 8
  planner:
    model: opencode/qwen3.6-plus-free    # Change to your model
  test_writer:
    model: opencode/qwen3.6-plus-free
  test_reviewer:
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
  password: <YOUR_MYSQL_PASSWORD>        # Replace with a strong password

logging:
  level: INFO
```

> **Security**: Never commit or share configuration files containing real passwords or tokens. Use restrictive file permissions (`chmod 600`) and keep the file owned by the `opengiraffe` user only.

### 6.2 opencode.json

Place the opencode CLI configuration at the path specified by `opencode.config_path` (default: `/etc/opengiraffe-java/opencode.json`). This file configures the opencode provider, model access, and authentication.

Refer to the [opencode documentation](https://opencode.ai) for the configuration format. Example structure:

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

## 7. MySQL Setup

### 7.1 Create Database and User

```sql
CREATE DATABASE opengiraffe_java CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'opengiraffe'@'localhost' IDENTIFIED BY '<YOUR_MYSQL_PASSWORD>';
GRANT ALL PRIVILEGES ON opengiraffe_java.* TO 'opengiraffe'@'localhost';
FLUSH PRIVILEGES;
```

### 7.2 Schema Migration

TDDForge uses Flyway for automatic schema migration. When the application starts with a valid MySQL connection, Flyway will automatically create all required tables (`tasks`, `agent_runs`, `task_events`, `app_state`) in the configured database.

No manual SQL execution is required. The migration scripts are embedded in the JAR at `classpath:db/migration/`.

### 7.3 Verification

After the first successful startup, verify tables were created:

```bash
mysql -u opengiraffe -p opengiraffe_java -e "SHOW TABLES;"
```

Expected output:

```
+-----------------------------+
| Tables_in_opengiraffe_java  |
+-----------------------------+
| agent_runs                  |
| app_state                   |
| flyway_schema_history       |
| task_events                 |
| tasks                       |
+-----------------------------+
```

## 8. systemd Service

### 8.1 Install the Service File

Copy the provided service template to systemd:

```bash
sudo cp deploy/systemd/tddforge.service /etc/systemd/system/tddforge.service
```

### 8.2 Review and Customize

Edit the service file if your paths differ from the defaults:

```bash
sudo nano /etc/systemd/system/tddforge.service
```

Key settings in the template:

| Setting | Value | Description |
|---|---|---|
| `User` | `opengiraffe` | Non-root user running the daemon |
| `WorkingDirectory` | `/opt/tddforge` | Application home directory |
| `ExecStart` | `/usr/bin/java -jar /opt/tddforge/tddforge.jar` | Launch command |
| `Restart` | `on-failure` | Auto-restart on non-zero exit |
| `RestartSec` | `10` | Seconds between restart attempts |
| `Environment` | `SPRING_CONFIG_ADDITIONAL_LOCATION=/etc/opengiraffe-java/` | Config directory override |

### 8.3 Enable and Start

```bash
sudo systemctl daemon-reload
sudo systemctl enable tddforge
sudo systemctl start tddforge
```

### 8.4 Check Status

```bash
sudo systemctl status tddforge
```

## 9. Logs and Monitoring

### 9.1 Viewing Logs

Logs are sent to the systemd journal by default:

```bash
# Follow live logs
sudo journalctl -u tddforge -f

# View recent logs
sudo journalctl -u tddforge -n 100

# View logs since a specific time
sudo journalctl -u tddforge --since "2024-01-01 00:00:00"
```

### 9.2 Structured Logging

TDDForge uses structured logging with MDC context. Each log line includes:

```
2024-01-15 10:30:45.123 [main] INFO  c.t.config.ConfigValidator [taskId= agentType= model= sessionId=] - Repo path validated: /data/repos/target-project
```

Key fields: `taskId`, `agentType`, `model`, `sessionId`.

### 9.3 Health Check

Verify the application is running:

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

## 10. Verification Checklist

After deployment, verify the following:

1. **Configuration loads successfully**: Check logs for `Repo path validated` and `Opencode config path resolved`.
2. **HTTP port is active**: Check logs for `Tomcat started on port` or the configured port message.
3. **Database connection works**: The health endpoint should show `db.status: UP`.
4. **Flyway migration ran**: The `flyway_schema_history` table should exist in MySQL.

## 11. Troubleshooting

### Application fails to start with "Repo path does not exist"

Ensure `repo.path` in `config.yaml` points to an existing git repository and the `opengiraffe` user has read access.

### Application fails to start with "Failed to create worktree dir"

Ensure the parent directory of `worktree_dir` exists and the `opengiraffe` user has write permission.

### MySQL connection refused

Verify MySQL is running and the `opengiraffe` user can connect:

```bash
mysql -u opengiraffe -p -h 127.0.0.1 opengiraffe_java
```

### opencode CLI not found

Ensure `opencode` is installed and available in the system PATH. When running under systemd, the PATH is minimal. You may need to specify the full path in the service file or add it to the `Environment` line:

```ini
Environment="PATH=/usr/local/bin:/usr/bin:/bin"
```

## 12. Security Notes

- The daemon runs as a non-root user (`opengiraffe`).
- Configuration files containing passwords should have `chmod 600` permissions.
- Never commit `config.yaml` or `opencode.json` with real credentials to version control.
- The MySQL password placeholder (`<YOUR_MYSQL_PASSWORD>`) in the example template must be replaced before use.
