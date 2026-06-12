package com.devboss.graph.impl;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.Node;
import com.devboss.graph.NodeResult;
import org.springframework.stereotype.Component;

@Component
public class HealthCheckStartNode implements Node {

    @Override
    public NodeResult execute(InvestigationContext ctx) {
        return new NodeResult("开始对核心服务进行健康巡检...\n", "HEALTH_CHECK_METRICS");
    }
}
