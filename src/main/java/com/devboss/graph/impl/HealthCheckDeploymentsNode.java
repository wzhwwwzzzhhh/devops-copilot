package com.devboss.graph.impl;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.Node;
import com.devboss.graph.NodeResult;
import com.devboss.graph.ToolCallHelper;
import com.devboss.service.ServiceConnectionService;
import com.devboss.tools.DeployService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
/** 部署健康检查节点：检查目标服务的部署状态与 Pod 运行情况 */
public class HealthCheckDeploymentsNode implements Node {

    private final DeployService deployService;
    private final ToolCallHelper toolCallHelper;
    private final ServiceConnectionService connectionService;

    public HealthCheckDeploymentsNode(DeployService deployService, ToolCallHelper toolCallHelper,
                                       ServiceConnectionService connectionService) {
        this.deployService = deployService;
        this.toolCallHelper = toolCallHelper;
        this.connectionService = connectionService;
    }

    @Override
    public NodeResult execute(InvestigationContext ctx) {
        String sessionId = ctx.getSessionId();
        List<String> services = connectionService.getServiceNames();

        if (services.isEmpty()) {
            return new NodeResult("未注册任何服务，跳过部署检查。\n", "HEALTH_CHECK_ANALYZE");
        }

        StringBuilder allDeploy = new StringBuilder();
        for (String service : services) {
            String result = toolCallHelper.logAndCall(sessionId, "list_deployments", "service=" + service,
                    () -> deployService.getDeployments(service));
            allDeploy.append("【").append(service).append("】\n").append(result).append("\n\n");
        }
        ctx.addCollectedData("health_deployments", allDeploy.toString());
        ctx.logToolCall("list_deployments", allDeploy.toString());
        return new NodeResult("[健康巡检 - 部署检查]\n" + allDeploy, "HEALTH_CHECK_ANALYZE");
    }
}
