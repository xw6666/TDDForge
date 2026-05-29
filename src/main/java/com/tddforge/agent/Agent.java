package com.tddforge.agent;

import com.tddforge.domain.AgentRun;

public interface Agent {

    AgentRun run(AgentContext context);
}
