package com.tddforge.git;

public class WorktreeManagerException extends RuntimeException {

    private final GitCommandResult commandResult;

    public WorktreeManagerException(String message) {
        super(message);
        this.commandResult = null;
    }

    public WorktreeManagerException(String message, GitCommandResult commandResult) {
        super(message);
        this.commandResult = commandResult;
    }

    public WorktreeManagerException(String message, Throwable cause) {
        super(message, cause);
        this.commandResult = null;
    }

    public GitCommandResult getCommandResult() {
        return commandResult;
    }
}
