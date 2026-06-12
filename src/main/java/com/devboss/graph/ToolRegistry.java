package com.devboss.graph;

import com.devboss.agent.InvestigationContext;
import com.devboss.graph.ToolCallHelper;
import com.devboss.knowledge.ExperienceMemoryService;
import com.devboss.knowledge.RagService;
import com.devboss.tools.DatabaseService;
import com.devboss.tools.DeployService;
import com.devboss.tools.DockerService;
import com.devboss.tools.ESMonitorService;
import com.devboss.tools.K8sService;
import com.devboss.tools.LogService;
import com.devboss.tools.MetricsService;
import com.devboss.tools.NginxService;
import com.devboss.tools.RabbitMQService;
import com.devboss.tools.RedisService;
import com.devboss.tools.SslService;
import com.devboss.tools.SystemMonitorService;
import com.devboss.tools.AlertService;
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
                        RagService ragService, ExperienceMemoryService experienceMemoryService,
                        RedisService redisService, RabbitMQService rabbitMQService,
                        SystemMonitorService systemMonitorService,
                        ESMonitorService esMonitorService, DockerService dockerService,
                        K8sService k8sService, NginxService nginxService,
                        SslService sslService, AlertService alertService) {
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

        register("execute_action", "执行高危运维操作：扩容/回滚/重启/金丝雀部署/部署新版本",
                "action: scale(扩容, params: replicas=N)/rollback(回滚, params: version=X)/restart(滚动重启)/canary(金丝雀部署, params: version=X, 10%->50%->100%)/deploy(部署新版本, params: version=X), serviceName: 服务名",
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

        register("detect_lock_wait", "检测 MySQL 死锁和锁等待情况，定位死锁根因",
                "instanceName: 数据库实例名称 (可选)",
                ctx -> databaseService.detectDeadlocks());

        register("warm_up_cache", "预热数据库缓存，对主要表执行查询以加载到 InnoDB Buffer Pool",
                "instanceName: 数据库实例名称 (可选)",
                ctx -> databaseService.warmUpCache());

        register("explain_query", "对 SQL 执行 EXPLAIN FORMAT=JSON 分析，检查索引使用情况和扫描行数",
                "sql: 要分析的 SQL 语句",
                ctx -> {
                    String msg = ctx.getUserMessage();
                    String sql = msg;
                    if (sql.toUpperCase().contains("TOOL:EXPLAIN_QUERY")) {
                        sql = sql.substring(sql.toUpperCase().indexOf("TOOL:EXPLAIN_QUERY") + 17).trim();
                    }
                    // 也支持通过 collectedData 传入
                    String collectedSql = ctx.getCollectedData("explain_sql");
                    if ((sql.isEmpty() || sql.equals(msg)) && collectedSql != null) {
                        sql = collectedSql;
                    }
                    if (sql.isEmpty() || sql.equals(msg)) {
                        // 尝试从消息中提取 SQL（TOOL:explain_query 之后的部分）
                        return "{\"error\": \"请提供要分析的 SQL 语句\"}";
                    }
                    return databaseService.explainQuery(sql);
                });

        register("search_experience", "搜索历史故障排查经验，查找过去已解决的同类问题",
                "question: 问题描述，如'数据库连接池耗尽'",
                ctx -> experienceMemoryService.searchSimilar(ctx.getUserMessage()));

        register("check_redis_status", "检查 Redis 运行状态，包括内存使用、Key 统计、慢查询、客户端连接",
                "无参数（检查默认 Redis 实例）",
                ctx -> redisService.getFullStatus());

        register("check_rabbitmq_status", "检查 RabbitMQ 状态，包括队列积压、消费者状态、节点健康、消息速率",
                "无参数（检查默认 RabbitMQ 实例）",
                ctx -> rabbitMQService.getFullStatus());

        register("check_system_status", "检查 Linux 主机的 CPU、内存、磁盘和网络使用情况",
                "无参数（检查当前主机）",
                ctx -> systemMonitorService.getFullStatus());

        register("check_es_status", "检查 Elasticsearch 集群状态，包括集群健康、节点堆内存、索引性能、分片分布",
                "无参数（检查默认 ES 实例）",
                ctx -> esMonitorService.getFullStatus());

        register("check_docker_status", "检查 Docker 容器状态，包括运行中容器、资源占用、镜像列表",
                "无参数（检查当前主机 Docker）",
                ctx -> dockerService.getFullStatus());

        register("check_k8s_status", "检查 Kubernetes 集群状态，包括节点/Pod/Deployment/事件",
                "无参数（检查默认 K8s 集群）",
                ctx -> k8sService.getFullStatus());

        register("check_nginx_status", "检查 Nginx 访问日志分析，包括 QPS、状态码分布、响应时间、Top URL 和客户端 IP",
                "无参数（检查默认 Nginx 实例）",
                ctx -> nginxService.getFullStatus());

        register("check_ssl_status", "检查 SSL 证书状态，包括域名列表、到期时间、剩余天数",
                "无参数（检查所有已配置域名）",
                ctx -> sslService.getFullStatus());

        register("check_alerts", "检查当前告警中心的所有告警，包括严重级别、来源、状态",
                "无参数（返回所有告警列表）",
                ctx -> alertService.getAlerts());

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
