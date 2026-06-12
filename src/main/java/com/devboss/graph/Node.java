package com.devboss.graph;

import com.devboss.agent.InvestigationContext;

@FunctionalInterface
public interface Node {

    NodeResult execute(InvestigationContext ctx);
}
