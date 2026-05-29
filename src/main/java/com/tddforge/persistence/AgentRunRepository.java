package com.tddforge.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentRunRepository extends JpaRepository<AgentRunEntity, String> {

    List<AgentRunEntity> findByTaskId(String taskId);

    List<AgentRunEntity> findByAgentType(String agentType);
}
