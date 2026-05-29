package com.tddforge.opencode;

import com.tddforge.config.OpencodeConfig;
import com.tddforge.domain.AgentRun;
import com.tddforge.domain.OpenCodeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class OpenCodeClient {

    private static final Logger log = LoggerFactory.getLogger(OpenCodeClient.class);

    private static final String CONTINUE_PROMPT = "Continue";

    private final OpencodeConfig config;
    private final OpenCodeNdjsonParser parser;
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();
    private volatile String opencodeBinary = "opencode";

    public OpenCodeClient(OpencodeConfig config) {
        this.config = config;
        this.parser = new OpenCodeNdjsonParser();
    }

    public OpenCodeClient(OpencodeConfig config, OpenCodeNdjsonParser parser) {
        this.config = config;
        this.parser = parser;
    }

    public void setOpencodeBinary(String opencodeBinary) {
        this.opencodeBinary = opencodeBinary;
    }

    public AgentRun run(String taskId, String agentType, OpenCodeRequest request) {
        Instant startTime = Instant.now();
        String accumulatedOutput = "";
        String sessionId = request.sessionId();
        int continueCount = 0;
        int finalExitCode = 0;
        int maxContinues = config.getMaxContinues();

        for (int attempt = 0; attempt <= maxContinues; attempt++) {
            String prompt = (attempt == 0) ? request.prompt() : CONTINUE_PROMPT;

            List<String> command = buildCommand(request, sessionId, prompt);
            log.info("Running opencode (attempt {}/{}): {}", attempt, maxContinues, String.join(" ", command));

            RunResult result = executeProcess(taskId, command, request.timeoutSeconds());
            String attemptOutput = result.output();

            if (!accumulatedOutput.isEmpty()) {
                accumulatedOutput = accumulatedOutput + "\n" + attemptOutput;
            } else {
                accumulatedOutput = attemptOutput;
            }

            finalExitCode = result.exitCode();

            OpenCodeResult parsed = parser.parse(accumulatedOutput);

            if (parsed.sessionId() != null && !parsed.sessionId().isBlank()) {
                sessionId = parsed.sessionId();
            }

            if (exitNormally(finalExitCode, parsed, sessionId, attempt, maxContinues)) {
                break;
            }

            continueCount = attempt + 1;
            log.info("Output incomplete (exitCode={}, complete={}), continuing with session={}",
                    finalExitCode, parsed.complete(), sessionId);
        }

        Instant endTime = Instant.now();
        long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();

        return new AgentRun(
                generateId(),
                taskId,
                agentType,
                request.model(),
                request.variant(),
                request.agent(),
                request.prompt(),
                accumulatedOutput,
                finalExitCode,
                durationMs,
                sessionId,
                continueCount,
                startTime
        );
    }

    private boolean exitNormally(int exitCode, OpenCodeResult parsed, String sessionId,
                                  int attempt, int maxContinues) {
        if (attempt >= maxContinues) {
            return true;
        }
        if (exitCode == 0 && parsed.complete()) {
            return true;
        }
        if (sessionId == null || sessionId.isBlank()) {
            return true;
        }
        return false;
    }

    private RunResult executeProcess(String taskId, List<String> command, long timeoutSeconds) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("OPENCODE_CONFIG", config.getConfigPath());

        StringBuilder outputBuilder = new StringBuilder();
        int exitCode = 0;
        long pid = -1;

        try {
            Process process = pb.start();
            pid = process.pid();
            runningProcesses.put(taskId, process);
            log.debug("Started opencode process with PID: {}", pid);

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                log.warn("Opencode process timed out after {}s, destroying (PID {})", timeoutSeconds, pid);
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                exitCode = -1;
            } else {
                exitCode = process.exitValue();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (outputBuilder.length() > 0) {
                        outputBuilder.append("\n");
                    }
                    outputBuilder.append(line);
                }
            }

            StringBuilder stderrBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (stderrBuilder.length() > 0) {
                        stderrBuilder.append("\n");
                    }
                    stderrBuilder.append(line);
                }
            }

            String stderr = stderrBuilder.toString().trim();
            if (!stderr.isEmpty()) {
                log.warn("Opencode stderr (PID {}): {}", pid, stderr);
                if (exitCode != 0) {
                    outputBuilder.append("\n--- stderr ---\n").append(stderr);
                }
            }

        } catch (IOException e) {
            log.error("Failed to start opencode process", e);
            exitCode = -2;
        } catch (InterruptedException e) {
            log.error("Opencode process was interrupted (PID {})", pid, e);
            Thread.currentThread().interrupt();
            exitCode = -3;
        } finally {
            runningProcesses.remove(taskId);
        }

        return new RunResult(outputBuilder.toString(), exitCode);
    }

    private List<String> buildCommand(OpenCodeRequest request, String sessionId, String prompt) {
        List<String> command = new ArrayList<>();
        command.add(opencodeBinary);
        command.add("run");
        command.add("--model");
        command.add(request.model());
        command.add("--dir");
        command.add(request.worktreeDir().toString());
        command.add("--format");
        command.add("json");

        if (sessionId != null && !sessionId.isBlank()) {
            command.add("--session");
            command.add(sessionId);
        }
        if (request.variant() != null && !request.variant().isBlank()) {
            command.add("--variant");
            command.add(request.variant());
        }
        if (request.agent() != null && !request.agent().isBlank()) {
            command.add("--agent");
            command.add(request.agent());
        }

        command.add(prompt);
        return command;
    }

    public void killTask(String taskId) {
        Process process = runningProcesses.get(taskId);
        if (process != null && process.isAlive()) {
            log.warn("Killing opencode process for task {} (PID {})", taskId, process.pid());
            process.destroyForcibly();
        }
    }

    public void killAll() {
        for (Map.Entry<String, Process> entry : runningProcesses.entrySet()) {
            Process process = entry.getValue();
            if (process.isAlive()) {
                log.warn("Killing opencode process for task {} (PID {})", entry.getKey(), process.pid());
                process.destroyForcibly();
            }
        }
        runningProcesses.clear();
    }

    private static String generateId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private record RunResult(String output, int exitCode) {
    }
}
