package com.tddforge.persistence;

import com.tddforge.domain.TaskEvent;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "task_events")
public class TaskEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "task_id", nullable = false, length = 32)
    private String taskId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "data_json", columnDefinition = "JSON")
    private String dataJson;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(3)")
    private Instant createdAt;

    public TaskEventEntity() {
    }

    public static TaskEventEntity fromDomain(TaskEvent event) {
        TaskEventEntity entity = new TaskEventEntity();
        entity.id = event.id();
        entity.taskId = event.taskId();
        entity.eventType = event.eventType();
        entity.message = event.message();
        entity.dataJson = event.dataJson();
        entity.createdAt = event.createdAt();
        return entity;
    }

    public TaskEvent toDomain() {
        return new TaskEvent(id, taskId, eventType, message, dataJson, createdAt);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getDataJson() { return dataJson; }
    public void setDataJson(String dataJson) { this.dataJson = dataJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}