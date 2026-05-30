package com.tddforge.orchestrator;

import com.tddforge.config.OrchestratorConfig;
import com.tddforge.domain.Task;
import com.tddforge.domain.TaskStatus;
import com.tddforge.opencode.OpenCodeClient;
import com.tddforge.persistence.TaskEntity;
import com.tddforge.persistence.TaskRepository;
import com.tddforge.service.DependencyTracker;
import com.tddforge.service.TaskExecutionService;
import com.tddforge.util.MdcSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

public class Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);

    private final TaskRepository taskRepository;
    private final DependencyTracker dependencyTracker;
    private final TaskExecutionService taskExecutionService;
    private final OrchestratorConfig orchestratorConfig;
    private final OpenCodeClient openCodeClient;

    private final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private final Set<String> pendingDispatch = ConcurrentHashMap.newKeySet();
    private final Set<String> dispatchedOrPending = ConcurrentHashMap.newKeySet();

    private ExecutorService executorService;
    private volatile boolean started = false;

    public Orchestrator(TaskRepository taskRepository,
                        DependencyTracker dependencyTracker,
                        TaskExecutionService taskExecutionService,
                        OrchestratorConfig orchestratorConfig,
                        OpenCodeClient openCodeClient) {
        this.taskRepository = taskRepository;
        this.dependencyTracker = dependencyTracker;
        this.taskExecutionService = taskExecutionService;
        this.orchestratorConfig = orchestratorConfig;
        this.openCodeClient = openCodeClient;
    }

    public synchronized void start() {
        if (started) {
            log.warn("Orchestrator already started");
            return;
        }
        int maxParallel = orchestratorConfig.getMaxParallelTasks();
        executorService = Executors.newFixedThreadPool(maxParallel);
        started = true;
        log.info("Orchestrator started with max_parallel_tasks={}", maxParallel);
    }

    public synchronized void stop() {
        if (!started) {
            log.warn("Orchestrator not started");
            return;
        }
        started = false;
        openCodeClient.killAll();
        if (executorService != null) {
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("Orchestrator executor did not terminate within 10 seconds after shutdownNow");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        runningTasks.clear();
        dispatchedOrPending.clear();
        pendingDispatch.clear();
        log.info("Orchestrator stopped");
    }

    public boolean isStarted() {
        return started;
    }

    public int getRunningCount() {
        return runningTasks.size();
    }

    public int getPendingCount() {
        return pendingDispatch.size();
    }

    public boolean dispatchTask(String taskId) {
        if (!started) {
            log.warn("Cannot dispatch task {}: orchestrator not started", taskId);
            return false;
        }

        Optional<TaskEntity> taskEntityOpt = taskRepository.findById(taskId);
        if (taskEntityOpt.isEmpty()) {
            log.warn("Cannot dispatch task {}: not found", taskId);
            return false;
        }

        Task task = taskEntityOpt.get().toDomain();

        if (task.getStatus() == TaskStatus.CANCELLED) {
            log.info("Cannot dispatch task {}: cancelled", taskId);
            return false;
        }

        if (!isRunnable(task.getStatus())) {
            log.info("Cannot dispatch task {}: status {} is not runnable", taskId, task.getStatus());
            return false;
        }

        if (runningTasks.containsKey(taskId)) {
            log.info("Cannot dispatch task {}: already running", taskId);
            return false;
        }

        if (!dispatchedOrPending.add(taskId)) {
            log.info("Cannot dispatch task {}: already dispatched or pending", taskId);
            return false;
        }

        if (dependencyTracker.isBlockedByDependencies(taskId)) {
            log.info("Cannot dispatch task {}: blocked by dependencies", taskId);
            dispatchedOrPending.remove(taskId);
            return false;
        }

        if (runningTasks.size() < orchestratorConfig.getMaxParallelTasks()) {
            submitForExecution(taskId);
            return true;
        }

        pendingDispatch.add(taskId);
        log.info("Task {} queued for pending dispatch (capacity full)", taskId);
        return true;
    }

    private void submitForExecution(String taskId) {
        Runnable taskWrapper = createTaskWrapper(taskId);
        Future<?> future = executorService.submit(taskWrapper);
        runningTasks.put(taskId, future);
        if (future.isDone()) {
            runningTasks.remove(taskId);
            dispatchedOrPending.remove(taskId);
            refreshPendingQueue();
        }
        log.info("Task {} submitted for execution, running={}", taskId, runningTasks.size());
    }

    private Runnable createTaskWrapper(String taskId) {
        return () -> {
            MdcSupport.setTaskContext(taskId);
            try {
                log.info("Task {} execution started", taskId);
                taskExecutionService.executeTask(taskId);
                log.info("Task {} execution completed", taskId);
            } catch (Exception e) {
                log.error("Task {} execution failed with exception", taskId, e);
            } finally {
                MdcSupport.clearTaskContext();
                runningTasks.remove(taskId);
                dispatchedOrPending.remove(taskId);
                log.info("Task {} removed from running map, running={}", taskId, runningTasks.size());
                refreshPendingQueue();
            }
        };
    }

    private void refreshPendingQueue() {
        if (!started) {
            return;
        }
        List<String> candidates = new ArrayList<>(pendingDispatch);
        for (String taskId : candidates) {
            if (runningTasks.size() >= orchestratorConfig.getMaxParallelTasks()) {
                break;
            }
            if (!pendingDispatch.remove(taskId)) {
                continue;
            }
            Optional<TaskEntity> taskEntityOpt = taskRepository.findById(taskId);
            if (taskEntityOpt.isEmpty()) {
                dispatchedOrPending.remove(taskId);
                continue;
            }

            Task task = taskEntityOpt.get().toDomain();

            if (task.getStatus() == TaskStatus.CANCELLED) {
                dispatchedOrPending.remove(taskId);
                continue;
            }

            if (!isRunnable(task.getStatus())) {
                dispatchedOrPending.remove(taskId);
                continue;
            }

            if (runningTasks.containsKey(taskId)) {
                continue;
            }

            if (dependencyTracker.isBlockedByDependencies(taskId)) {
                pendingDispatch.add(taskId);
                continue;
            }

            submitForExecution(taskId);
        }
    }

    private boolean isRunnable(TaskStatus status) {
        return status == TaskStatus.PENDING;
    }
}