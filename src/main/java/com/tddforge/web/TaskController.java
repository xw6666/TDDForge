package com.tddforge.web;

import com.tddforge.domain.AgentRun;
import com.tddforge.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class TaskController {

    private final TaskWebService taskWebService;

    public TaskController(TaskWebService taskWebService) {
        this.taskWebService = taskWebService;
    }

    @PostMapping("/tasks")
    public ResponseEntity<TaskDetailResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        TaskDetailResponse response = taskWebService.createTask(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<TaskSummary>> listTasks() {
        return ResponseEntity.ok(taskWebService.listTasks());
    }

    @GetMapping("/tasks/{id}")
    public ResponseEntity<TaskDetailResponse> getTask(@PathVariable String id) {
        return ResponseEntity.ok(taskWebService.getTask(id));
    }

    @PostMapping("/tasks/{id}/dispatch")
    public ResponseEntity<OperationResponse> dispatchTask(@PathVariable String id) {
        return ResponseEntity.ok(taskWebService.dispatchTask(id));
    }

    @PostMapping("/tasks/{id}/cancel")
    public ResponseEntity<OperationResponse> cancelTask(@PathVariable String id) {
        return ResponseEntity.ok(taskWebService.cancelTask(id));
    }

    @PostMapping("/tasks/{id}/revise")
    public ResponseEntity<OperationResponse> reviseTask(@PathVariable String id,
                                                         @Valid @RequestBody ReviseRequest request) {
        return ResponseEntity.ok(taskWebService.reviseTask(id, request.feedback()));
    }

    @PostMapping("/tasks/{id}/clean")
    public ResponseEntity<OperationResponse> cleanTask(@PathVariable String id) {
        return ResponseEntity.ok(taskWebService.cleanTask(id));
    }

    @PostMapping("/tasks/{id}/publish")
    public ResponseEntity<OperationResponse> publishTask(@PathVariable String id) {
        return ResponseEntity.ok(taskWebService.publishTask(id));
    }

    @GetMapping("/tasks/{id}/runs")
    public ResponseEntity<List<AgentRun>> getTaskRuns(@PathVariable String id) {
        return ResponseEntity.ok(taskWebService.getTaskRuns(id));
    }

    @GetMapping("/tasks/{id}/status")
    public ResponseEntity<TaskStatusResponse> getTaskStatus(@PathVariable String id) {
        return ResponseEntity.ok(taskWebService.getTaskStatus(id));
    }

    @GetMapping("/system/status")
    public ResponseEntity<SystemStatusResponse> getSystemStatus() {
        return ResponseEntity.ok(taskWebService.getSystemStatus());
    }
}
