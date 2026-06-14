package com.devboss.graph.impl;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.Node;
import com.devboss.graph.NodeResult;
import com.devboss.graph.ToolCallHelper;
import com.devboss.tools.DeployService;
import org.springframework.stereotype.Component;

/** 执行动作节点：执行经审批通过的运维操作（如重启、回滚） */
@Component
public class ExecuteActionNode implements Node {

    private final DeployService deployService;
    private final ToolCallHelper toolCallHelper;

    public ExecuteActionNode(DeployService deployService, ToolCallHelper toolCallHelper) {
        this.deployService = deployService;
        this.toolCallHelper = toolCallHelper;
    }

    @Override
    public NodeResult execute(InvestigationContext ctx) {
        String action = ctx.getPendingAction();
        String service = ctx.getServiceName();
        String sessionId = ctx.getSessionId();
        String result = toolCallHelper.logAndCall(sessionId, "execute_action",
                "action=" + action + ", service=" + service,
                () -> deployService.executeAction(action, service, ctx.getActionParams()));
        ctx.logToolCall("execute_action", result);
        return new NodeResult(String.format("[%s] %s\n", "执行操作", result), "GENERATE_REPORT");
    }
}
