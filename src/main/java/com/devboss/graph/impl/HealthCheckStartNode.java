package com.devboss.graph.impl;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.Node;
import com.devboss.graph.NodeResult;
import org.springframework.stereotype.Component;

/** 健康巡检入口节点：初始化巡检参数并触发各检查项 */
@Component
public class HealthCheckStartNode implements Node {

    @Override
    public NodeResult execute(InvestigationContext ctx) {
        return new NodeResult("开始对核心服务进行健康巡检...\n", "HEALTH_CHECK_METRICS");
    }
}
