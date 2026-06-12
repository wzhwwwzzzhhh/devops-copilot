package com.devboss.graph;

import com.devboss.agent.InvestigationContext;

@FunctionalInterface
public interface ToolFunction {
    String execute(InvestigationContext ctx);
}
