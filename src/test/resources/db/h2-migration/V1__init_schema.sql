-- H2-compatible test migration (mirrors MySQL schema)
-- H2 MySQL compatibility mode supports DATETIME(3) and JSON

CREATE TABLE tasks (
  id VARCHAR(32) PRIMARY KEY,
  title VARCHAR(512) NOT NULL,
  description CLOB NOT NULL,
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
  plan_output CLOB,
  test_output CLOB,
  test_review_output CLOB,
  code_output CLOB,
  review_output CLOB,
  review_pass BOOLEAN NOT NULL DEFAULT FALSE,
  reviewer_results_json JSON NOT NULL,
  session_ids_json JSON NOT NULL,

  retry_count INT NOT NULL DEFAULT 0,
  test_retry_count INT NOT NULL DEFAULT 0,
  code_retry_count INT NOT NULL DEFAULT 0,
  max_test_retries INT NOT NULL DEFAULT 2,
  max_code_retries INT NOT NULL DEFAULT 4,

  user_feedback CLOB,
  error CLOB,

  created_at TIMESTAMP(3) NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL,
  started_at TIMESTAMP(3) NULL,
  completed_at TIMESTAMP(3) NULL,
  published_at TIMESTAMP(3) NULL
);

CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_parent_id ON tasks(parent_id);
CREATE INDEX idx_tasks_updated_at ON tasks(updated_at);

CREATE TABLE agent_runs (
  id VARCHAR(32) PRIMARY KEY,
  task_id VARCHAR(32) NOT NULL,
  agent_type VARCHAR(64) NOT NULL,
  model VARCHAR(256) NOT NULL,
  variant VARCHAR(128) NOT NULL DEFAULT '',
  agent VARCHAR(128) NOT NULL DEFAULT '',
  prompt CLOB NOT NULL,
  output CLOB NOT NULL,
  exit_code INT NOT NULL,
  duration_ms BIGINT NOT NULL,
  session_id VARCHAR(256) NOT NULL DEFAULT '',
  continue_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP(3) NOT NULL,

  CONSTRAINT fk_agent_runs_task
    FOREIGN KEY (task_id) REFERENCES tasks(id)
    ON DELETE CASCADE
);

CREATE INDEX idx_agent_runs_task_id ON agent_runs(task_id);
CREATE INDEX idx_agent_runs_agent_type ON agent_runs(agent_type);

CREATE TABLE task_events (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id VARCHAR(32) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  message CLOB NOT NULL,
  data_json JSON NULL,
  created_at TIMESTAMP(3) NOT NULL,

  CONSTRAINT fk_task_events_task
    FOREIGN KEY (task_id) REFERENCES tasks(id)
    ON DELETE CASCADE
);

CREATE INDEX idx_task_events_task_id ON task_events(task_id);

CREATE TABLE app_state (
  state_key VARCHAR(128) PRIMARY KEY,
  data_json JSON NOT NULL,
  updated_at TIMESTAMP(3) NOT NULL
);
