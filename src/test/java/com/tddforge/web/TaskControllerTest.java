package com.tddforge.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tddforge.domain.*;
import com.tddforge.web.dto.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskWebService taskWebService;

    private TaskDetailResponse createSampleTaskDetail(String id, TaskStatus status) {
        return new TaskDetailResponse(
                id, "Test Task", "A test description",
                status, TaskPriority.MEDIUM, TaskSource.MANUAL, "develop",
                null, List.of(), List.of(), false,
                "task/" + id + "/test-task", "/tmp/worktrees/" + id,
                "medium", null, null, null, null, null,
                0, 0, 0, 2, 4,
                List.of("session-1"), false, List.of(),
                null, null, null,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:00:00Z"),
                null, null, null
        );
    }

    private TaskSummary createSampleTaskSummary(String id, TaskStatus status) {
        return new TaskSummary(
                id, "Task " + id, status, TaskPriority.MEDIUM,
                null, "branch-" + id, null,
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:00:00Z")
        );
    }

    @Nested
    class CreateTask {

        @Test
        void shouldCreateTaskWithValidRequest() throws Exception {
            TaskDetailResponse response = createSampleTaskDetail("task-1", TaskStatus.PENDING);
            when(taskWebService.createTask(any(CreateTaskRequest.class))).thenReturn(response);

            mockMvc.perform(post("/api/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "title": "Test Task",
                                        "description": "A test description",
                                        "priority": "medium",
                                        "forceNoSplit": false
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value("task-1"))
                    .andExpect(jsonPath("$.title").value("Test Task"))
                    .andExpect(jsonPath("$.description").value("A test description"))
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.priority").value("MEDIUM"))
                    .andExpect(jsonPath("$.branchName").value("task/task-1/test-task"))
                    .andExpect(jsonPath("$.sessionIds", hasSize(1)));
        }

        @Test
        void shouldReturn400WhenTitleIsBlank() throws Exception {
            mockMvc.perform(post("/api/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "title": "",
                                        "description": "A test description"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation_error"))
                    .andExpect(jsonPath("$.message").value(containsString("title")));
        }

        @Test
        void shouldReturn400WhenTitleIsMissing() throws Exception {
            mockMvc.perform(post("/api/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "description": "A test description"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation_error"));
        }

        @Test
        void shouldReturn400WhenDescriptionIsBlank() throws Exception {
            mockMvc.perform(post("/api/tasks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "title": "Test Task",
                                        "description": ""
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation_error"))
                    .andExpect(jsonPath("$.message").value(containsString("description")));
        }
    }

    @Nested
    class ListTasks {

        @Test
        void shouldReturnTaskList() throws Exception {
            List<TaskSummary> tasks = List.of(
                    createSampleTaskSummary("task-1", TaskStatus.PENDING),
                    createSampleTaskSummary("task-2", TaskStatus.COMPLETED)
            );
            when(taskWebService.listTasks()).thenReturn(tasks);

            mockMvc.perform(get("/api/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].id").value("task-1"))
                    .andExpect(jsonPath("$[0].status").value("PENDING"))
                    .andExpect(jsonPath("$[1].id").value("task-2"))
                    .andExpect(jsonPath("$[1].status").value("COMPLETED"));
        }

        @Test
        void shouldReturnEmptyListWhenNoTasks() throws Exception {
            when(taskWebService.listTasks()).thenReturn(List.of());

            mockMvc.perform(get("/api/tasks"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    @Nested
    class GetTask {

        @Test
        void shouldReturnTaskDetail() throws Exception {
            TaskDetailResponse response = createSampleTaskDetail("task-1", TaskStatus.CODING);
            when(taskWebService.getTask("task-1")).thenReturn(response);

            mockMvc.perform(get("/api/tasks/task-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("task-1"))
                    .andExpect(jsonPath("$.status").value("CODING"))
                    .andExpect(jsonPath("$.branchName").value("task/task-1/test-task"))
                    .andExpect(jsonPath("$.worktreePath").value("/tmp/worktrees/task-1"));
        }

        @Test
        void shouldReturn404WhenTaskNotFound() throws Exception {
            when(taskWebService.getTask("nonexistent"))
                    .thenThrow(new TaskNotFoundException("nonexistent"));

            mockMvc.perform(get("/api/tasks/nonexistent"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("task_not_found"))
                    .andExpect(jsonPath("$.taskId").value("nonexistent"));
        }
    }

    @Nested
    class DispatchTask {

        @Test
        void shouldDispatchPendingTask() throws Exception {
            when(taskWebService.dispatchTask("task-1"))
                    .thenReturn(OperationResponse.success("task-1", "Task dispatched successfully"));

            mockMvc.perform(post("/api/tasks/task-1/dispatch"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Task dispatched successfully"))
                    .andExpect(jsonPath("$.taskId").value("task-1"));

            verify(taskWebService).dispatchTask("task-1");
        }

        @Test
        void shouldReturn404WhenDispatchingNonexistentTask() throws Exception {
            when(taskWebService.dispatchTask("nonexistent"))
                    .thenThrow(new TaskNotFoundException("nonexistent"));

            mockMvc.perform(post("/api/tasks/nonexistent/dispatch"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error").value("task_not_found"));
        }

        @Test
        void shouldReturn409WhenDispatchingNonPendingTask() throws Exception {
            when(taskWebService.dispatchTask("task-1"))
                    .thenThrow(new InvalidTaskStateException("task-1", TaskStatus.CODING, "dispatch",
                            "Task must be in PENDING status to dispatch, current status: CODING"));

            mockMvc.perform(post("/api/tasks/task-1/dispatch"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("invalid_task_state"))
                    .andExpect(jsonPath("$.taskId").value("task-1"));
        }
    }

    @Nested
    class CancelTask {

        @Test
        void shouldCancelRunningTask() throws Exception {
            when(taskWebService.cancelTask("task-1"))
                    .thenReturn(OperationResponse.success("task-1", "Task cancelled successfully"));

            mockMvc.perform(post("/api/tasks/task-1/cancel"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.message").value("Task cancelled successfully"));

            verify(taskWebService).cancelTask("task-1");
        }

        @Test
        void shouldReturn404WhenCancellingNonexistentTask() throws Exception {
            when(taskWebService.cancelTask("nonexistent"))
                    .thenThrow(new TaskNotFoundException("nonexistent"));

            mockMvc.perform(post("/api/tasks/nonexistent/cancel"))
                    .andExpect(status().isNotFound());
        }

        @Test
        void shouldReturn409WhenCancellingCompletedTask() throws Exception {
            when(taskWebService.cancelTask("task-1"))
                    .thenThrow(new InvalidTaskStateException("task-1", TaskStatus.COMPLETED, "cancel",
                            "Cannot cancel task in COMPLETED status"));

            mockMvc.perform(post("/api/tasks/task-1/cancel"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("invalid_task_state"));
        }
    }

    @Nested
    class ReviseTask {

        @Test
        void shouldReviseTaskInArbitration() throws Exception {
            when(taskWebService.reviseTask(eq("task-1"), eq("Please fix the tests")))
                    .thenReturn(OperationResponse.success("task-1", "Task revised successfully"));

            mockMvc.perform(post("/api/tasks/task-1/revise")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "feedback": "Please fix the tests"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(taskWebService).reviseTask("task-1", "Please fix the tests");
        }

        @Test
        void shouldReturn400WhenFeedbackIsBlank() throws Exception {
            mockMvc.perform(post("/api/tasks/task-1/revise")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "feedback": ""
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("validation_error"))
                    .andExpect(jsonPath("$.message").value(containsString("feedback")));
        }

        @Test
        void shouldReturn409WhenReviseNonArbitrationTask() throws Exception {
            when(taskWebService.reviseTask(eq("task-1"), any()))
                    .thenThrow(new InvalidTaskStateException("task-1", TaskStatus.PENDING, "revise",
                            "Task must be in NEEDS_ARBITRATION status to revise"));

            mockMvc.perform(post("/api/tasks/task-1/revise")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "feedback": "Some feedback"
                                    }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("invalid_task_state"));
        }
    }

    @Nested
    class CleanTask {

        @Test
        void shouldCleanCompletedTask() throws Exception {
            when(taskWebService.cleanTask("task-1"))
                    .thenReturn(OperationResponse.success("task-1", "Task cleaned successfully"));

            mockMvc.perform(post("/api/tasks/task-1/clean"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(taskWebService).cleanTask("task-1");
        }

        @Test
        void shouldReturn409WhenCleaningActiveTask() throws Exception {
            when(taskWebService.cleanTask("task-1"))
                    .thenThrow(new InvalidTaskStateException("task-1", TaskStatus.CODING, "clean",
                            "Cannot clean task in active status: CODING"));

            mockMvc.perform(post("/api/tasks/task-1/clean"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("invalid_task_state"));
        }
    }

    @Nested
    class PublishTask {

        @Test
        void shouldPublishCompletedTask() throws Exception {
            when(taskWebService.publishTask("task-1"))
                    .thenReturn(OperationResponse.success("task-1", "Task published successfully"));

            mockMvc.perform(post("/api/tasks/task-1/publish"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(taskWebService).publishTask("task-1");
        }

        @Test
        void shouldReturn409WhenPublishingNonCompletedTask() throws Exception {
            when(taskWebService.publishTask("task-1"))
                    .thenThrow(new InvalidTaskStateException("task-1", TaskStatus.CODING, "publish",
                            "Task must be in COMPLETED status to publish"));

            mockMvc.perform(post("/api/tasks/task-1/publish"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("invalid_task_state"));
        }
    }

    @Nested
    class GetTaskRuns {

        @Test
        void shouldReturnAgentRuns() throws Exception {
            AgentRun run = new AgentRun(
                    "run-1", "task-1", "planner", "test-model",
                    null, null, "test prompt", "test output",
                    0, 1500L, "session-1", 0,
                    Instant.parse("2025-01-01T00:00:00Z")
            );
            when(taskWebService.getTaskRuns("task-1")).thenReturn(List.of(run));

            mockMvc.perform(get("/api/tasks/task-1/runs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value("run-1"))
                    .andExpect(jsonPath("$[0].agentType").value("planner"))
                    .andExpect(jsonPath("$[0].exitCode").value(0));
        }

        @Test
        void shouldReturn404WhenGettingRunsForNonexistentTask() throws Exception {
            when(taskWebService.getTaskRuns("nonexistent"))
                    .thenThrow(new TaskNotFoundException("nonexistent"));

            mockMvc.perform(get("/api/tasks/nonexistent/runs"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class GetTaskStatus {

        @Test
        void shouldReturnTaskStatus() throws Exception {
            when(taskWebService.getTaskStatus("task-1"))
                    .thenReturn(new TaskStatusResponse("task-1", TaskStatus.CODING, null));

            mockMvc.perform(get("/api/tasks/task-1/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.taskId").value("task-1"))
                    .andExpect(jsonPath("$.status").value("CODING"));
        }

        @Test
        void shouldReturnStatusWithError() throws Exception {
            when(taskWebService.getTaskStatus("task-1"))
                    .thenReturn(new TaskStatusResponse("task-1", TaskStatus.FAILED, "Something went wrong"));

            mockMvc.perform(get("/api/tasks/task-1/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.error").value("Something went wrong"));
        }
    }

    @Nested
    class SystemStatus {

        @Test
        void shouldReturnSystemStatus() throws Exception {
            when(taskWebService.getSystemStatus())
                    .thenReturn(new SystemStatusResponse(true, 2, 1, 3, null));

            mockMvc.perform(get("/api/system/status"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.started").value(true))
                    .andExpect(jsonPath("$.runningTasks").value(2))
                    .andExpect(jsonPath("$.pendingTasks").value(1))
                    .andExpect(jsonPath("$.maxParallelTasks").value(3));
        }
    }

    @Nested
    class TaskDetailPhaseOutputs {

        @Test
        void shouldReturnPhaseOutputFields() throws Exception {
            TaskDetailResponse response = new TaskDetailResponse(
                    "task-po", "Task With Outputs", "desc",
                    TaskStatus.COMPLETED, TaskPriority.HIGH, TaskSource.MANUAL, "develop",
                    null, List.of(), List.of(), false,
                    "task/task-po/branch", "/tmp/wt/task-po",
                    "high", "plan result", "test result", "test review result", "code result", "review result",
                    1, 0, 1, 2, 4,
                    List.of(), true, List.of(),
                    null, null, null,
                    Instant.parse("2025-01-01T00:00:00Z"),
                    Instant.parse("2025-01-01T00:00:00Z"),
                    null, null, null
            );
            when(taskWebService.getTask("task-po")).thenReturn(response);

            mockMvc.perform(get("/api/tasks/task-po"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.planOutput").value("plan result"))
                    .andExpect(jsonPath("$.testOutput").value("test result"))
                    .andExpect(jsonPath("$.testReviewOutput").value("test review result"))
                    .andExpect(jsonPath("$.codeOutput").value("code result"))
                    .andExpect(jsonPath("$.reviewOutput").value("review result"));
        }

        @Test
        void shouldReturnNullPhaseOutputsWhenEmpty() throws Exception {
            TaskDetailResponse response = createSampleTaskDetail("task-no", TaskStatus.PENDING);
            when(taskWebService.getTask("task-no")).thenReturn(response);

            mockMvc.perform(get("/api/tasks/task-no"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.planOutput").doesNotExist())
                    .andExpect(jsonPath("$.testOutput").doesNotExist())
                    .andExpect(jsonPath("$.testReviewOutput").doesNotExist())
                    .andExpect(jsonPath("$.codeOutput").doesNotExist())
                    .andExpect(jsonPath("$.reviewOutput").doesNotExist());
        }
    }

    @Nested
    class Dashboard {

        @Test
        void shouldServeDashboardHtml() throws Exception {
            mockMvc.perform(get("/dashboard.html"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("text/html"))
                    .andExpect(content().string(containsString("TDDForge Dashboard")));
        }
    }
}
