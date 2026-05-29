package com.tddforge.agent;

public interface Agent<T> {

    AgentResult<T> run(AgentContext context);
}
