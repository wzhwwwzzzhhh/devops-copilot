package com.devboss.graph;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.ToolCallHelper;
import com.devboss.knowledge.RagService;
import com.devboss.tools.DatabaseService;
import com.devboss.tools.DeployService;
import com.devboss.tools.LogService;
import com.devboss.tools.MetricsService;
import com.devboss.tools.TraceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolEntry> tools = new LinkedHashMap<>();
    private final ToolCallHelper toolCallHelper;

    public ToolRegistry(MetricsService metricsService, LogService logService,
                        TraceService traceService, DatabaseService databaseService,
                        DeployService deployService, ToolCallHelper toolCallHelper,
                        RagService ragService) {
        this.toolCallHelper = toolCallHelper;

        register("query_metrics", "查询服务的实时监控指标",
                "serviceName: 服务名称 (order-service / payment-service / user-service)",
                ctx -> metricsService.getMetrics(ctx.getServiceName()));

        register("query_logs", "检索服务的错误日志，查找异常信息",
                "serviceName: 服务名称, keyword: 搜索关键词(可选)",
                ctx -> logService.getLogs(ctx.getServiceName(), null));

        register("query_traces", "查询服务的链路追踪数据，分析请求耗时",
                "serviceName: 服务名称",
                ctx -> traceService.getTraces(ctx.getServiceName()));

        register("check_db_status", "检查数据库实例的连接池和慢查询状态",
                "instanceName: 数据库实例名称 (可选)",
                ctx -> databaseService.getDbStatus(ctx.getServiceName()));

        register("list_deployments", "查看服务的部署版本和副本数",
                "serviceName: 服务名称",
                ctx -> deployService.getDeployments(ctx.getServiceName()));

        register("execute_action", "执行扩容/回滚/重启等高危运维操作",
                "action: scale(扩容)/rollback(回滚)/restart(重启), serviceName: 服务名",
                ctx -> deployService.executeAction(ctx.getPendingAction(),
                        ctx.getServiceName(), ctx.getActionParams()));

        register("search_knowledge", "搜索运维知识库，查找排障方案和处理规范",
                "question: 搜索关键词，如'数据库连接池耗尽'",
                ctx -> ragService.search(ctx.getUserMessage()));

        register("kill_query", "终止 MySQL 中长时间运行的查询",
                "connectionId: 要终止的连接ID（从运行中查询获取）",
                ctx -> {
                    String msg = ctx.getUserMessage();
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(msg);
                    long id = m.find() ? Long.parseLong(m.group()) : 0;
                    if (id == 0) return "{\"error\": \"未找到有效的连接ID\"}";
                    return databaseService.killQuery(id);
                });

        register("execute_sql", "在 MySQL 上执行安全的运维 SQL",
                "sql: 要执行的 SQL（支持: ALTER TABLE ADD INDEX/KEY/PK, ANALYZE TABLE, OPTIMIZE TABLE, KILL, CREATE TABLE, DROP TABLE, INSERT）",
                ctx -> {
                    String msg = ctx.getUserMessage();
                    // 尝试从消息中提取 SQL（TOOL:execute_sql 之后的部分）
                    String sql = msg;
                    if (sql.toUpperCase().contains("TOOL:EXECUTE_SQL")) {
                        sql = sql.substring(sql.toUpperCase().indexOf("TOOL:EXECUTE_SQL") + 16).trim();
                    }
                    if (sql.isEmpty()) {
                        return "{\"error\": \"请提供要执行的 SQL\"}";
                    }
                    return databaseService.executeSafeSQL(sql);
                });

        log.info("ToolRegistry 已注册 {} 个工具", tools.size());
    }

    private void register(String name, String description, String inputFormat, ToolFunction function) {
        tools.put(name, new ToolEntry(name, description, inputFormat, function));
    }

    public ToolEntry get(String name) {
        return tools.get(name);
    }

    public boolean exists(String name) {
        return tools.containsKey(name);
    }

    public String getToolDescriptions() {
        StringBuilder sb = new StringBuilder();
        sb.append("可用工具列表：\n");
        for (ToolEntry entry : tools.values()) {
            sb.append("- ").append(entry.name).append(": ").append(entry.description).append("\n");
            sb.append("  输入参数: ").append(entry.inputFormat).append("\n");
        }
        return sb.toString();
    }

    public String executeTool(String toolName, InvestigationContext ctx, String sessionId) {
        ToolEntry entry = tools.get(toolName);
        if (entry == null) {
            return "错误：未知工具 " + toolName;
        }
        String result = toolCallHelper.logAndCall(sessionId, toolName,
                "via ReAct, service=" + ctx.getServiceName(), () -> entry.function.execute(ctx));
        ctx.addCollectedData(toolName, result);
        ctx.logToolCall(toolName, result);
        return result;
    }

    public String getLastToolResults(InvestigationContext ctx) {
        StringBuilder sb = new StringBuilder();
        sb.append("已采集的数据：\n");
        for (Map.Entry<String, ToolEntry> entry : tools.entrySet()) {
            Object data = ctx.getCollectedData(entry.getKey());
            if (data != null) {
                String str = data.toString();
                sb.append("【").append(entry.getKey()).append("】:\n");
                sb.append(str.length() > 300 ? str.substring(0, 300) + "..." : str).append("\n\n");
            }
        }
        return sb.toString();
    }

    public static class ToolEntry {
        public final String name;
        public final String description;
        public final String inputFormat;
        private final ToolFunction function;

        public ToolEntry(String name, String description, String inputFormat, ToolFunction function) {
            this.name = name;
            this.description = description;
            this.inputFormat = inputFormat;
            this.function = function;
        }
    }
}
