-- TDDForge initial schema
-- MySQL 8, InnoDB, utf8mb4, DATETIME(3) millisecond precision

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

CREATE TABLE app_state (
  state_key VARCHAR(128) PRIMARY KEY,
  data_json JSON NOT NULL,
  updated_at DATETIME(3) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
