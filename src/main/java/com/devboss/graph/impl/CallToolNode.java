package com.devboss.graph.impl;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.Node;
import com.devboss.graph.NodeResult;
import com.devboss.graph.ToolRegistry;
import org.springframework.stereotype.Component;

@Component
public class CallToolNode implements Node {

    private final ToolRegistry toolRegistry;

    public CallToolNode(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public NodeResult execute(InvestigationContext ctx) {
        String decision = ctx.getCollectedData("react_last_decision");
        if (decision == null || decision.isEmpty()) {
            return new NodeResult("错误：没有工具调用决策\n", "REACT_DECIDE");
        }

        String toolName = decision.replace("TOOL:", "").trim().split("\\s+")[0];
        String result = toolRegistry.executeTool(toolName, ctx, ctx.getSessionId());

        String output = String.format("[工具:%s] 执行完成\n%s\n", toolName, result);
        return new NodeResult(output, "REACT_DECIDE");
    }
}
