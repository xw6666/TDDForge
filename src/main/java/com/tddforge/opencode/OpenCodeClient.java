package com.tddforge.opencode;

import com.tddforge.config.OpencodeConfig;
import com.tddforge.domain.AgentRun;
import com.tddforge.domain.OpenCodeRequest;
import com.tddforge.util.LogSanitizer;
import com.tddforge.util.MdcSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        MdcSupport.setTaskContext(taskId);
        MdcSupport.setAgentContext(agentType, request.model());
        try {
            return doRun(taskId, agentType, request);
        } finally {
            if (previousMdc != null) {
                MDC.setContextMap(previousMdc);
            } else {
                MDC.clear();
            }
        }
    }

    private AgentRun doRun(String taskId, String agentType, OpenCodeRequest request) {
        Instant startTime = Instant.now();
        String accumulatedOutput = "";
        String sessionId = request.sessionId();
        int continueCount = 0;
        int finalExitCode = 0;
        int maxContinues = config.getMaxContinues();

        log.debug("Opencode run starting: taskId={}, agentType={}, model={}, prompt={}",
                taskId, agentType, request.model(), LogSanitizer.truncatePrompt(request.prompt()));

        for (int attempt = 0; attempt <= maxContinues; attempt++) {
            String prompt = (attempt == 0) ? request.prompt() : CONTINUE_PROMPT;

            List<String> command = buildCommand(request, sessionId, prompt);
            log.info("Running opencode attempt {}/{}: model={}, worktree={}",
                    attempt, maxContinues, request.model(), request.worktreeDir());

            RunResult result = executeProcess(taskId, request.worktreeDir(), command, request.timeoutSeconds());
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
                MdcSupport.setSessionId(sessionId);
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

        MdcSupport.setRunMetrics(durationMs, finalExitCode);
        MdcSupport.setContinueCount(continueCount);
        log.info("Opencode run completed: exitCode={}, durationMs={}, sessionId={}, continueCount={}",
                finalExitCode, durationMs, sessionId, continueCount);

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

    private RunResult executeProcess(String taskId, Path worktreeDir, List<String> command, long timeoutSeconds) {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("OPENCODE_CONFIG", config.getConfigPath());
        pb.directory(worktreeDir.toFile());

        Path outFile = null;
        Path errFile = null;
        int exitCode = 0;
        long pid = -1;

        try {
            outFile = Files.createTempFile("opencode-stdout-", ".log");
            errFile = Files.createTempFile("opencode-stderr-", ".log");
            pb.redirectOutput(outFile.toFile());
            pb.redirectError(errFile.toFile());

            log.debug("Process environment: {}", LogSanitizer.sanitizeEnvString(formatEnv(pb.environment())));

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

            String output = Files.readString(outFile);
            String stderr = Files.readString(errFile).trim();

            if (!stderr.isEmpty()) {
                log.warn("Opencode stderr (PID {}): {}", pid, LogSanitizer.truncateOutput(stderr));
            }

            if (exitCode != 0 && !stderr.isEmpty()) {
                output = output + "\n--- stderr ---\n" + stderr;
            }

            return new RunResult(output, exitCode);

        } catch (IOException e) {
            log.error("Failed to start opencode process", e);
            return new RunResult("", -2);
        } catch (InterruptedException e) {
            log.error("Opencode process was interrupted (PID {})", pid, e);
            Thread.currentThread().interrupt();
            return new RunResult("", -3);
        } finally {
            runningProcesses.remove(taskId);
            if (outFile != null) {
                try { Files.deleteIfExists(outFile); } catch (IOException ignored) {}
            }
            if (errFile != null) {
                try { Files.deleteIfExists(errFile); } catch (IOException ignored) {}
            }
        }
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

    private static String formatEnv(Map<String, String> env) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : env.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
            first = false;
        }
        return sb.toString();
    }

    private record RunResult(String output, int exitCode) {
    }
}
