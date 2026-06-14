package com.devboss.graph.impl;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.Node;
import com.devboss.graph.NodeResult;
import com.devboss.llm.ChatService;
import com.devboss.llm.FallbackAnalyzer;
import com.devboss.llm.PromptTemplates;
import com.devboss.service.MessageService;
import com.devboss.service.ServiceConnectionService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
/** 巡检分析节点：汇总各检查项结果并调用大模型生成巡检结论 */
public class HealthCheckAnalyzeNode implements Node {

    private final ChatService chatService;
    private final MessageService messageService;
    private final ServiceConnectionService connectionService;

    public HealthCheckAnalyzeNode(ChatService chatService, MessageService messageService,
                                   ServiceConnectionService connectionService) {
        this.chatService = chatService;
        this.messageService = messageService;
        this.connectionService = connectionService;
    }

    @Override
    public NodeResult execute(InvestigationContext ctx) {
        String sessionId = ctx.getSessionId();
        List<String> services = connectionService.getServiceNames();

        if (services.isEmpty()) {
            return new NodeResult("未注册任何服务，健康巡检结束。\n", "GENERATE_REPORT");
        }

        String history = messageService.getHistoryContext(sessionId);
        String prompt = PromptTemplates.healthCheckPrompt(ctx.getToolCallLogSummary(), history);
        String analysis = chatService.chat(prompt);

        if (FallbackAnalyzer.isFallback(analysis)) {
            analysis = FallbackAnalyzer.analyzeHealthCheckData(ctx);
        }

        ctx.setAnalysisResult(analysis);
        ctx.addMessage("assistant", analysis);
        messageService.saveMessage(sessionId, "assistant", analysis);
        return new NodeResult(analysis, "GENERATE_REPORT");
    }
}
