package com.devboss.graph.impl;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.Node;
import com.devboss.graph.NodeResult;
import com.devboss.graph.ToolRegistry;
import com.devboss.llm.ChatService;
import com.devboss.llm.FallbackAnalyzer;
import com.devboss.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReActNode implements Node {

    private static final Logger log = LoggerFactory.getLogger(ReActNode.class);

    private final ChatService chatService;
    private final ToolRegistry toolRegistry;
    private final MessageService messageService;

    public ReActNode(ChatService chatService, ToolRegistry toolRegistry,
                     MessageService messageService) {
        this.chatService = chatService;
        this.toolRegistry = toolRegistry;
        this.messageService = messageService;
    }

    @Override
    public NodeResult execute(InvestigationContext ctx) {
        String sessionId = ctx.getSessionId();
        String userMessage = ctx.getUserMessage();
        String toolResults = toolRegistry.getLastToolResults(ctx);
        String history = messageService.getHistoryContext(sessionId);

        String prompt = buildReActPrompt(userMessage, toolResults, history);
        String response = chatService.chat(prompt);

        if (FallbackAnalyzer.isFallback(response)) {
            log.warn("LLM 不可用，切换为降级模式 (sessionId={})", sessionId);
            return fallbackToSequential(ctx);
        }

        log.info("ReAct 原始响应: {}", response.substring(0, Math.min(150, response.length())));

        String decision = response.trim();
        String upper = decision.toUpperCase();

        // 检测是否是 TOOL 指令（多种格式兼容）
        String toolName = tryExtractToolName(decision);
        if (toolName != null && toolRegistry.exists(toolName)) {
            ctx.addCollectedData("react_last_decision", "TOOL:" + toolName);
            ctx.addMessage("assistant", "需要查询" + toolName + "，正在获取数据...");
            messageService.saveMessage(sessionId, "assistant", "正在调用工具: " + toolName);
            return new NodeResult("正在调用 [" + toolName + "]...\n", "CALL_TOOL");
        }

        // 如果不是 TOOL 指令，全部当作 FINAL_ANSWER 处理
        ctx.setAnalysisResult(decision);
        ctx.addMessage("assistant", decision);
        messageService.saveMessage(sessionId, "assistant", decision);

        // 只在回复的后半部分检测动作指令，防止分析中顺带提到"扩容"就误触发
        String latterHalf = decision.length() > 20 ? decision.substring(decision.length() * 2 / 3) : decision;
        if (latterHalf.contains("扩容") || latterHalf.contains("回滚") || latterHalf.contains("重启")) {
            ctx.setAwaitingApproval(true);
            ctx.setPendingAction(extractAction(decision));
            return new NodeResult(decision + "\n\n[需要审批] 以上操作涉及高危变更，请确认是否执行？(Y/N)\n",
                    "AWAITING_APPROVAL");
        }
        return new NodeResult(decision, "GENERATE_REPORT");
    }

    private String buildReActPrompt(String userMessage, String toolResults, String history) {
        String toolDesc = toolRegistry.getToolDescriptions();
        String dataSection = toolResults.isEmpty() ? "还没有采集数据，请先调用工具。" : toolResults;
        String historySection = history.isEmpty() ? "" : "\n之前的对话记录：\n" + history + "\n";

        return """
                你是一个运维排查助手。你有以下工具可以使用：

                %s

                请严格按照以下格式回复：

                【如果需要调用工具】回复：TOOL:工具名称
                例如：TOOL:query_metrics

                【如果已经查到了足够信息，直接给出结论】回复你的分析结论即可

                注意：
                - 如果需要扩容/回滚/重启，请在结论中说清楚
                - 一次只调用一个工具，等拿到结果后再决定下一步
                - 最后将你用到的主要指标数据以 ```json 代码块附在末尾（字段用英文）
                - 如果需要修复数据库问题（加索引、优化表、kill 连接），可以调用 execute_sql 工具执行

                用户问题：%s
                %s
                已采集数据：
                %s
                """.formatted(toolDesc, userMessage, historySection, dataSection);
    }

    /**
     * 尝试从回复中提取工具名。支持多种格式：
     * - TOOL:query_metrics
     * - TOOL: query_metrics
     * - 调用 query_metrics
     * - 使用 query_metrics
     */
    private String tryExtractToolName(String decision) {
        String upper = decision.toUpperCase().trim();

        // TOOL:xxx 格式
        if (upper.startsWith("TOOL:")) {
            return decision.substring(5).trim().split("\\s+")[0].split("\\n")[0];
        }

        // "调用xxx" / "使用xxx" 格式
        String[] lines = decision.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.contains("TOOL:")) {
                return trimmed.substring(trimmed.indexOf("TOOL:") + 5).trim().split("\\s+")[0].split("\\n")[0];
            }
        }

        // 在全文搜索已知工具名
        for (String name : new String[]{"query_metrics", "query_logs", "query_traces",
                "check_db_status", "list_deployments", "execute_action", "search_knowledge",
                "kill_query", "execute_sql"}) {
            if (decision.contains(name)) {
                return name;
            }
        }

        return null;
    }

    private NodeResult fallbackToSequential(InvestigationContext ctx) {
        String sessionId = ctx.getSessionId();

        // ChatService 返回 __FALLBACK__ 说明模型不可用，给用户清晰的引导信息
        String guidance = """
                ⚠️ **当前没有可用的 AI 模型**
                
                我无法连接到任何语言模型来处理你的问题。
                
                请按以下步骤配置模型：
                
                1. 点击右上角 ⚙ 打开 **系统设置**
                2. 切换到 **模型配置** 标签页
                3. 添加一个模型：
                   - **Ollama 本地模型**: 填写 Ollama 地址如 `http://localhost:11434`，模型名如 `hermes3:8b` 或 `qwen2.5`
                   - **DeepSeek / 通义千问等**: 选择供应商，填写 API 地址和 Key
                4. 点击 **切换** 激活该模型
                
                > 配置完成后即可使用 AI 进行故障排查。
                """;

        String analysis = guidance;
        ctx.setAnalysisResult(analysis);
        ctx.addMessage("assistant", analysis);
        messageService.saveMessage(sessionId, "assistant", analysis);
        return new NodeResult("\n" + analysis, "END");
    }

    private String extractAction(String text) {
        if (text.contains("扩容")) return "scale";
        if (text.contains("回滚")) return "rollback";
        if (text.contains("重启")) return "restart";
        return "scale";
    }
}
