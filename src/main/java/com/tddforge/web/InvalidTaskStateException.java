package com.tddforge.web;

import com.tddforge.domain.TaskStatus;

public class InvalidTaskStateException extends RuntimeException {

    private final String taskId;
    private final TaskStatus currentStatus;
    private final String operation;

    public InvalidTaskStateException(String taskId, TaskStatus currentStatus, String operation, String message) {
        super(message);
        this.taskId = taskId;
        this.currentStatus = currentStatus;
        this.operation = operation;
    }

    public String getTaskId() {
        return taskId;
    }

    public TaskStatus getCurrentStatus() {
        return currentStatus;
    }

    public String getOperation() {
        return operation;
    }
}
