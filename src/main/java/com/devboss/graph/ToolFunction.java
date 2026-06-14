package com.devboss.graph;

import com.devboss.agent.InvestigationContext;

/** 工具函数接口：定义单个运维工具的调用签名 */
@FunctionalInterface
public interface ToolFunction {
    String execute(InvestigationContext ctx);
}
