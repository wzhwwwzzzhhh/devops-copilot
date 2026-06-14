package com.devboss.graph.impl;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.Node;
import com.devboss.graph.NodeResult;
import com.devboss.graph.ToolCallHelper;
import com.devboss.service.ServiceConnectionService;
import com.devboss.tools.LogService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
/** 日志健康检查节点：采集目标服务的近期日志，分析异常信息 */
public class HealthCheckLogsNode implements Node {

    private final LogService logService;
    private final ToolCallHelper toolCallHelper;
    private final ServiceConnectionService connectionService;

    public HealthCheckLogsNode(LogService logService, ToolCallHelper toolCallHelper,
                                ServiceConnectionService connectionService) {
        this.logService = logService;
        this.toolCallHelper = toolCallHelper;
        this.connectionService = connectionService;
    }

    @Override
    public NodeResult execute(InvestigationContext ctx) {
        String sessionId = ctx.getSessionId();
        List<String> services = connectionService.getServiceNames();

        if (services.isEmpty()) {
            return new NodeResult("未注册任何服务，跳过日志检查。\n", "HEALTH_CHECK_DATABASE");
        }

        StringBuilder allLogs = new StringBuilder();
        for (String service : services) {
            String result = toolCallHelper.logAndCall(sessionId, "query_logs", "service=" + service + ", keyword=ERROR",
                    () -> logService.getLogs(service, "ERROR"));
            allLogs.append("【").append(service).append("】\n").append(result).append("\n\n");
        }
        ctx.addCollectedData("health_logs", allLogs.toString());
        ctx.logToolCall("query_logs", allLogs.toString());
        return new NodeResult("[健康巡检 - 日志检查]\n" + allLogs, "HEALTH_CHECK_DATABASE");
    }
}
