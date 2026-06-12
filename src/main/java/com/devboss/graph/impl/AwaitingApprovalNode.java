package com.devboss.graph.impl;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.Node;
import com.devboss.graph.NodeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AwaitingApprovalNode implements Node {

    private static final Logger log = LoggerFactory.getLogger(AwaitingApprovalNode.class);

    @Override
    public NodeResult execute(InvestigationContext ctx) {
        String msg = ctx.getUserMessage().trim().toUpperCase();

        if (msg.equals("Y") || msg.equals("YES") || msg.equals("是") || msg.equals("确认")) {
            ctx.setAwaitingApproval(false);
            return new NodeResult("已确认，开始执行操作...\n", "EXECUTE_ACTION");
        }

        if (msg.equals("N") || msg.equals("NO") || msg.equals("否") || msg.equals("取消")) {
            ctx.setAwaitingApproval(false);
            return new NodeResult("已取消操作，生成排查报告...\n", "GENERATE_REPORT");
        }

        // 连续 3 次未回复 Y/N，自动取消防止死循环
        ctx.incrementRetry();
        if (ctx.isMaxRetriesExceeded()) {
            log.warn("审批超时(已等待3次)，自动取消操作: sessionId={}", ctx.getSessionId());
            ctx.setAwaitingApproval(false);
            return new NodeResult("审批超时，操作已自动取消。\n", "GENERATE_REPORT");
        }

        int remaining = 3 - ctx.getRetryCount();
        return new NodeResult("请回复 **Y** 确认执行，或 **N** 取消操作（还可等待 " + remaining + " 次）。\n",
                "AWAITING_APPROVAL");
    }
}
