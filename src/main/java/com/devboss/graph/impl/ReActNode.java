package com.devboss.graph.impl;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.Node;
import com.devboss.graph.NodeResult;
import com.devboss.graph.ToolRegistry;
import com.devboss.knowledge.ExperienceMemoryService;
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
    private final ExperienceMemoryService experienceMemoryService;

    public ReActNode(ChatService chatService, ToolRegistry toolRegistry,
                     MessageService messageService,
                     ExperienceMemoryService experienceMemoryService) {
        this.chatService = chatService;
        this.toolRegistry = toolRegistry;
        this.messageService = messageService;
        this.experienceMemoryService = experienceMemoryService;
    }

    @Override
    public NodeResult execute(InvestigationContext ctx) {
        String sessionId = ctx.getSessionId();
        String userMessage = ctx.getUserMessage();
        String toolResults = toolRegistry.getLastToolResults(ctx);
        String history = messageService.getHistoryContext(sessionId);

        // 检索历史经验，注入 Prompt
        String experienceHint = experienceMemoryService.searchSimilar(userMessage);
        if (experienceHint == null) experienceHint = "";

        String prompt = buildReActPrompt(userMessage, toolResults, history, experienceHint);
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

    private String buildReActPrompt(String userMessage, String toolResults, String history,
                                     String experienceHint) {
        String toolDesc = toolRegistry.getToolDescriptions();
        String dataSection = toolResults.isEmpty() ? "还没有采集数据，请先调用工具。" : toolResults;
        String historySection = history.isEmpty() ? "" : "\n之前的对话记录：\n" + history + "\n";
        String experienceSection = experienceHint.isEmpty() ? "" : "\n" + experienceHint + "\n";

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
                - 如果发现慢查询或需要分析 SQL 执行效率，调用 explain_query 分析执行计划
                - 如果怀疑有死锁或锁等待，调用 detect_lock_wait 检查
                - 如果需要预热数据库缓存，调用 warm_up_cache
                - 如果需要参考过去的排查经验，调用 search_experience
                - 如果需要检查 Redis 状态，调用 check_redis_status
                - 如果需要检查 RabbitMQ 状态，调用 check_rabbitmq_status
                - 如果需要检查系统主机状态（CPU/内存/磁盘），调用 check_system_status
                - 如果需要检查 Elasticsearch 集群状态，调用 check_es_status
                - 如果需要检查 Docker 容器状态，调用 check_docker_status
                - 如果需要检查 Kubernetes 集群状态，调用 check_k8s_status
                - 如果需要检查 Nginx 访问日志分析（QPS/状态码/延迟），调用 check_nginx_status
                - 如果需要检查 SSL 证书到期状态，调用 check_ssl_status
                - 如果需要查看当前告警中心的告警列表，调用 check_alerts

                %s
                用户问题：%s
                %s
                已采集数据：
                %s
                """.formatted(toolDesc, experienceSection, userMessage, historySection, dataSection);
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
                "kill_query", "execute_sql", "explain_query", "detect_lock_wait",
                "warm_up_cache", "search_experience", "check_redis_status",
                "check_rabbitmq_status", "check_system_status", "check_es_status",
                "check_docker_status", "check_k8s_status",
                "check_nginx_status", "check_ssl_status", "check_alerts"}) {
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
