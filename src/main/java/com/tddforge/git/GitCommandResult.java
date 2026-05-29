package com.tddforge.git;

import jakarta.annotation.Nullable;
import java.util.List;

public record GitCommandResult(
        int exitCode,
        String stdout,
        String stderr,
        long durationMs,
        List<String> command
) {

    public GitCommandResult {
        if (stdout == null) stdout = "";
        if (stderr == null) stderr = "";
        if (command == null) command = List.of();
    }

    public boolean success() {
        return exitCode == 0;
    }

    public String combinedOutput() {
        if (stderr.isEmpty()) return stdout;
        if (stdout.isEmpty()) return stderr;
        return stdout + "\n" + stderr;
    }
}
