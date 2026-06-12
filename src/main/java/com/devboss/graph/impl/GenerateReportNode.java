package com.devboss.graph.impl;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.Node;
import com.devboss.graph.NodeResult;
import com.devboss.knowledge.ExperienceMemoryService;
import com.devboss.llm.ChatService;
import com.devboss.llm.FallbackAnalyzer;
import com.devboss.llm.PromptTemplates;
import com.devboss.memory.StateManager;
import com.devboss.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class GenerateReportNode implements Node {

    private static final Logger log = LoggerFactory.getLogger(GenerateReportNode.class);

    private final ChatService chatService;
    private final MessageService messageService;
    private final StateManager stateManager;
    private final ExperienceMemoryService experienceMemoryService;

    public GenerateReportNode(ChatService chatService, MessageService messageService,
                              StateManager stateManager,
                              ExperienceMemoryService experienceMemoryService) {
        this.chatService = chatService;
        this.messageService = messageService;
        this.stateManager = stateManager;
        this.experienceMemoryService = experienceMemoryService;
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

        // 将已解决的问题存入经验记忆
        saveExperienceIfResolved(ctx, finalReport);

        return new NodeResult("\n" + finalReport, "END");
    }

    /**
     * 判断本次排查是否解决了问题，如果是则存入经验记忆
     */
    private void saveExperienceIfResolved(InvestigationContext ctx, String report) {
        try {
            String userMessage = ctx.getUserMessage();
            // 健康巡检不保存为经验
            if (userMessage != null && (userMessage.contains("健康巡检") || userMessage.contains("巡检"))) {
                return;
            }

            // 收集排查过程中的工具调用数据
            String toolLog = ctx.getToolCallLogSummary();
            String analysis = ctx.getAnalysisResult();
            if (analysis == null) analysis = "";

            // 从 collectedData 中提取修复 SQL
            String fixSql = ctx.getCollectedData("executed_sql");
            if (fixSql == null) {
                // 尝试从 toolCallLog 中提取
                String execSqlData = ctx.getCollectedData("execute_sql");
                if (execSqlData instanceof String && !((String) execSqlData).isEmpty()) {
                    fixSql = (String) execSqlData;
                    if (fixSql.length() > 500) fixSql = fixSql.substring(0, 500);
                }
            }

            // 判断是否有修复操作（执行了 SQL / KILL / 扩容 等）
            boolean hasFixAction = false;
            String fixAction = "";
            if (toolLog != null) {
                if (toolLog.contains("execute_sql") || toolLog.contains("execute_action")
                        || toolLog.contains("kill_query") || toolLog.contains("warm_up_cache")) {
                    hasFixAction = true;
                }
                if (toolLog.contains("execute_action")) {
                    fixAction = "执行了运维操作（扩容/回滚/重启）";
                } else if (toolLog.contains("execute_sql") && fixSql != null && !fixSql.isEmpty()) {
                    fixAction = "执行 SQL: " + (fixSql.length() > 200 ? fixSql.substring(0, 200) + "..." : fixSql);
                } else if (toolLog.contains("kill_query")) {
                    fixAction = "终止了长时间运行的查询";
                } else if (toolLog.contains("warm_up_cache")) {
                    fixAction = "执行了缓存预热";
                }
            }

            // 有修复动作或分析包含修复建议 → 保存经验
            boolean shouldSave = hasFixAction
                    || (analysis.contains("已添加") || analysis.contains("已执行")
                        || analysis.contains("已终止") || analysis.contains("已扩容"))
                    || (report != null && (report.contains("修复") || report.contains("解决")
                        || report.contains("已处理") || report.contains("已优化")));

            if (!shouldSave) {
                log.debug("本次排查无修复操作，跳过保存经验");
                return;
            }

            // 提取问题类型
            String problemType = ExperienceMemoryService.ProblemType.fromString(userMessage).name();

            // 从分析结果中提取根因（简化版：取分析的前 200 字）
            String rootCause = analysis;
            if (rootCause.length() > 300) rootCause = rootCause.substring(0, 300);

            // 生成经验总结（取最终报告的核心内容）
            String summary = report;
            if (summary != null && summary.length() > 500) summary = summary.substring(0, 500);

            experienceMemoryService.saveExperience(
                    problemType,
                    rootCause,
                    fixAction,
                    fixSql,
                    ctx.getServiceName(),
                    true,
                    summary
            );

        } catch (Exception e) {
            log.debug("保存经验失败 (非关键): {}", e.getMessage());
        }
    }
}
