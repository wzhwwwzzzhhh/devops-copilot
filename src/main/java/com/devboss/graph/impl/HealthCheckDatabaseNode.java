package com.devboss.graph.impl;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.Node;
import com.devboss.graph.NodeResult;
import com.devboss.graph.ToolCallHelper;
import com.devboss.service.ServiceConnectionService;
import com.devboss.tools.DatabaseService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
/** 数据库健康检查节点：检测目标服务的数据库连接与状态 */
public class HealthCheckDatabaseNode implements Node {

    private final DatabaseService databaseService;
    private final ToolCallHelper toolCallHelper;
    private final ServiceConnectionService connectionService;

    public HealthCheckDatabaseNode(DatabaseService databaseService, ToolCallHelper toolCallHelper,
                                    ServiceConnectionService connectionService) {
        this.databaseService = databaseService;
        this.toolCallHelper = toolCallHelper;
        this.connectionService = connectionService;
    }

    @Override
    public NodeResult execute(InvestigationContext ctx) {
        String sessionId = ctx.getSessionId();
        List<String> services = connectionService.getServiceNames();

        if (services.isEmpty()) {
            return new NodeResult("未注册任何服务，跳过数据库检查。\n", "HEALTH_CHECK_DEPLOYMENTS");
        }

        StringBuilder allDb = new StringBuilder();
        for (String service : services) {
            String result = toolCallHelper.logAndCall(sessionId, "check_db_status", "instance=" + service + "-db",
                    () -> databaseService.getDbStatus(service + "-db-primary"));
            allDb.append("【").append(service).append("】\n").append(result).append("\n\n");
        }
        ctx.addCollectedData("health_database", allDb.toString());
        ctx.logToolCall("check_db_status", allDb.toString());
        return new NodeResult("[健康巡检 - 数据库检查]\n" + allDb, "HEALTH_CHECK_DEPLOYMENTS");
    }
}
