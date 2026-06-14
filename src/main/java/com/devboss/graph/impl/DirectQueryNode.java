package com.devboss.graph.impl;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.Node;
import com.devboss.graph.NodeResult;
import com.devboss.graph.ToolCallHelper;
import com.devboss.tools.DeployService;
import com.devboss.tools.MetricsService;
import org.springframework.stereotype.Component;

/** 直接查询节点：根据用户消息快速查询服务版本或状态 */
@Component
public class DirectQueryNode implements Node {

    private final MetricsService metricsService;
    private final DeployService deployService;
    private final ToolCallHelper toolCallHelper;

    public DirectQueryNode(MetricsService metricsService, DeployService deployService,
                           ToolCallHelper toolCallHelper) {
        this.metricsService = metricsService;
        this.deployService = deployService;
        this.toolCallHelper = toolCallHelper;
    }

    @Override
    public NodeResult execute(InvestigationContext ctx) {
        String msg = ctx.getUserMessage().toLowerCase();
        String service = ctx.getServiceName();
        String sessionId = ctx.getSessionId();
        StringBuilder result = new StringBuilder();

        // 如果没有识别到具体服务，直接返回提示并跳转到 END
        if (service == null || service.isEmpty()) {
            result.append("未识别到具体服务名称，无法直接查询。");
            result.append("\n请明确指定服务名，如：order-service、payment-service、user-service");
            ctx.addCollectedData("direct_query", result.toString());
            ctx.addMessage("assistant", result.toString());
            return new NodeResult("\n" + result.toString(), "END");
        }

        if (msg.contains("版本") || msg.contains("version")) {
            String deployments = toolCallHelper.logAndCall(sessionId, "list_deployments", "service=" + service,
                    () -> deployService.getDeployments(service));
            result.append("### 部署版本查询\n");
            result.append(String.format("服务: %s\n", service)).append(deployments);
        } else {
            String metrics = toolCallHelper.logAndCall(sessionId, "query_metrics", "service=" + service,
                    () -> metricsService.getMetrics(service));
            result.append("### 服务状态查询\n");
            result.append(String.format("服务: %s\n", service)).append(metrics);
        }

        ctx.addCollectedData("direct_query", result.toString());
        ctx.addMessage("assistant", result.toString());
        return new NodeResult("\n" + result.toString(), "END");
    }
}
