package com.tddforge.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentRunRepository extends JpaRepository<AgentRunEntity, String> {

    List<AgentRunEntity> findByTaskId(String taskId);

    List<AgentRunEntity> findByAgentType(String agentType);

    Optional<AgentRunEntity> findFirstByTaskIdAndAgentTypeOrderByCreatedAtDesc(String taskId, String agentType);
}
