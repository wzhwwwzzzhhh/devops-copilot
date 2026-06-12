package com.devboss.graph.impl;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.Node;
import com.devboss.graph.NodeResult;
import org.springframework.stereotype.Component;

@Component
public class EndNode implements Node {

    @Override
    public NodeResult execute(InvestigationContext ctx) {
        String report = ctx.getReport();
        if (report != null) {
            return new NodeResult(report, null);
        }
        return new NodeResult("排查完成。", null);
    }
}
