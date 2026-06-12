package com.devboss.graph.impl;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.Node;
import com.devboss.graph.NodeResult;
import com.devboss.graph.ToolCallHelper;
import com.devboss.service.ServiceConnectionService;
import com.devboss.tools.MetricsService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HealthCheckMetricsNode implements Node {

    private final MetricsService metricsService;
    private final ToolCallHelper toolCallHelper;
    private final ServiceConnectionService connectionService;

    public HealthCheckMetricsNode(MetricsService metricsService, ToolCallHelper toolCallHelper,
                                   ServiceConnectionService connectionService) {
        this.metricsService = metricsService;
        this.toolCallHelper = toolCallHelper;
        this.connectionService = connectionService;
    }

    @Override
    public NodeResult execute(InvestigationContext ctx) {
        String sessionId = ctx.getSessionId();
        List<String> services = connectionService.getServiceNames();

        if (services.isEmpty()) {
            String msg = "未注册任何服务，请先通过 API 注册服务后再进行健康巡检。\n";
            return new NodeResult(msg, "HEALTH_CHECK_LOGS");
        }

        StringBuilder allMetrics = new StringBuilder();
        for (String service : services) {
            String result = toolCallHelper.logAndCall(sessionId, "query_metrics", "service=" + service,
                    () -> metricsService.getMetrics(service));
            allMetrics.append("【").append(service).append("】\n").append(result).append("\n\n");
        }
        ctx.addCollectedData("health_metrics", allMetrics.toString());
        ctx.logToolCall("query_metrics", allMetrics.toString());
        return new NodeResult("[健康巡检 - 指标检查]\n" + allMetrics, "HEALTH_CHECK_LOGS");
    }
}
