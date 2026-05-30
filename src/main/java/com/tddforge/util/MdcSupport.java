package com.tddforge.util;

import org.slf4j.MDC;

import java.util.Map;

public final class MdcSupport {

    public static final String TASK_ID = "taskId";
    public static final String AGENT_TYPE = "agentType";
    public static final String MODEL = "model";
    public static final String SESSION_ID = "sessionId";
    public static final String WORKTREE_PATH = "worktreePath";
    public static final String BRANCH = "branch";
    public static final String DURATION_MS = "durationMs";
    public static final String EXIT_CODE = "exitCode";
    public static final String RETRY_COUNT = "retryCount";
    public static final String CONTINUE_COUNT = "continueCount";

    private MdcSupport() {
    }

    public static void setTaskContext(String taskId) {
        if (taskId != null) {
            MDC.put(TASK_ID, taskId);
        }
    }

    public static void setAgentContext(String agentType, String model) {
        if (agentType != null) {
            MDC.put(AGENT_TYPE, agentType);
        }
        if (model != null) {
            MDC.put(MODEL, model);
        }
    }

    public static void setSessionId(String sessionId) {
        if (sessionId != null) {
            MDC.put(SESSION_ID, sessionId);
        }
    }

    public static void setWorktreeContext(String worktreePath, String branch) {
        if (worktreePath != null) {
            MDC.put(WORKTREE_PATH, worktreePath);
        }
        if (branch != null) {
            MDC.put(BRANCH, branch);
        }
    }

    public static void setRunMetrics(long durationMs, int exitCode) {
        MDC.put(DURATION_MS, String.valueOf(durationMs));
        MDC.put(EXIT_CODE, String.valueOf(exitCode));
    }

    public static void setRetryCount(int retryCount) {
        MDC.put(RETRY_COUNT, String.valueOf(retryCount));
    }

    public static void setContinueCount(int continueCount) {
        MDC.put(CONTINUE_COUNT, String.valueOf(continueCount));
    }

    public static void put(String key, String value) {
        if (key != null && value != null) {
            MDC.put(key, value);
        }
    }

    public static void clearTaskContext() {
        MDC.remove(TASK_ID);
        MDC.remove(AGENT_TYPE);
        MDC.remove(MODEL);
        MDC.remove(SESSION_ID);
        MDC.remove(WORKTREE_PATH);
        MDC.remove(BRANCH);
        MDC.remove(DURATION_MS);
        MDC.remove(EXIT_CODE);
        MDC.remove(RETRY_COUNT);
        MDC.remove(CONTINUE_COUNT);
    }

    public static Map<String, String> getSnapshot() {
        return MDC.getCopyOfContextMap() != null ? Map.copyOf(MDC.getCopyOfContextMap()) : Map.of();
    }
}
