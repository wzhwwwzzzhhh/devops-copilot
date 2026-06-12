package com.devboss.graph.impl;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.Node;
import com.devboss.graph.NodeResult;
import org.springframework.stereotype.Component;

@Component
public class StartNode implements Node {

    @Override
    public NodeResult execute(InvestigationContext ctx) {
        // 重置上次的重试计数
        ctx.resetRetryCount();

        String message = ctx.getUserMessage().toLowerCase();
        String service = extractServiceName(ctx.getUserMessage());
        if (service != null) {
            ctx.setServiceName(service);
        }

        if (message.contains("巡检") || message.contains("健康检查") || message.contains("health")) {
            return new NodeResult("收到巡检指令，开始对核心服务进行健康检查...\n", "HEALTH_CHECK_START");
        }
        if (isSimpleCommand(message)) {
            return new NodeResult("收到，快速查询...\n", "DIRECT_QUERY");
        }
        return new NodeResult("收到，让我自主决策排查步骤...\n", "REACT_DECIDE");
    }

    private boolean isSimpleCommand(String message) {
        // 只有明确提到已知服务名 + 查询关键词时，才走快速查询路径
        // 避免"查看下数据库信息"这类问题误入 DIRECT_QUERY
        boolean hasService = message.contains("order-service") || message.contains("payment-service")
                || message.contains("user-service") || message.contains("order")
                || message.contains("payment") || message.contains("user");
        return hasService && (message.contains("版本") || message.contains("version")
                || (message.contains("查") && (message.contains("状态") || message.contains("status")))
                || message.contains("什么版本") || message.contains("当前版本")
                || (message.contains("状态") && (message.contains("怎么样") || message.contains("如何") || message.contains("正常"))));
    }

    private String extractServiceName(String message) {
        if (message.contains("order-service") || message.contains("order")) return "order-service";
        if (message.contains("payment-service") || message.contains("payment")) return "payment-service";
        if (message.contains("user-service") || message.contains("user")) return "user-service";
        return null; // unknown service, caller should handle gracefully
    }
}
