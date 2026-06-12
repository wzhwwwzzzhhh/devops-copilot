package com.devboss.graph.impl;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.Node;
import com.devboss.graph.NodeResult;
import com.devboss.llm.ChatService;
import com.devboss.llm.FallbackAnalyzer;
import com.devboss.llm.PromptTemplates;
import com.devboss.memory.StateManager;
import com.devboss.service.MessageService;
import org.springframework.stereotype.Component;

@Component
public class GenerateReportNode implements Node {

    private final ChatService chatService;
    private final MessageService messageService;
    private final StateManager stateManager;

    public GenerateReportNode(ChatService chatService, MessageService messageService,
                              StateManager stateManager) {
        this.chatService = chatService;
        this.messageService = messageService;
        this.stateManager = stateManager;
    }

    @Override
    public NodeResult execute(InvestigationContext ctx) {
        String sessionId = ctx.getSessionId();
        String history = messageService.getHistoryContext(sessionId);
        String analysisResult = ctx.getAnalysisResult();
        String prompt = PromptTemplates.reportPrompt(
                ctx.getUserMessage(), ctx.getToolCallLogSummary(),
                analysisResult, history);
        String finalReport = chatService.chat(prompt);

        if (FallbackAnalyzer.isFallback(finalReport)) {
            finalReport = analysisResult;
        }

        ctx.setReport(finalReport);
        ctx.addMessage("assistant", finalReport);
        messageService.saveMessage(sessionId, "assistant", finalReport);
        stateManager.saveContext(sessionId, ctx);

        return new NodeResult("\n" + finalReport, "END");
    }
}
