package com.tddforge.persistence;

import com.tddforge.domain.TaskEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskEventRepository extends JpaRepository<TaskEventEntity, Long> {

    List<TaskEventEntity> findByTaskIdOrderByCreatedAtAsc(String taskId);
}