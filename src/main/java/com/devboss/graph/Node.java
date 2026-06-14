package com.devboss.graph;

import com.devboss.agent.InvestigationContext;

/** 图节点接口：定义图编排中每个节点的执行契约 */
@FunctionalInterface
public interface Node {

    NodeResult execute(InvestigationContext ctx);
}
