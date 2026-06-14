package com.devboss.llm;

import com.devboss.agent.InvestigationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/** 降级分析器：当 LLM 不可用时提供备选数据分析方案 */
public class FallbackAnalyzer {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private FallbackAnalyzer() {
    }

    public static boolean isFallback(String response) {
        return response == null || response.equals("__FALLBACK__");
    }

    public static String analyzeFromData(InvestigationContext ctx) {
        String serviceName = ctx.getServiceName();
        String metrics = ctx.getCollectedData("metrics");
        String logs = ctx.getCollectedData("logs");
        String dbStatus = ctx.getCollectedData("database");
        String traces = ctx.getCollectedData("traces");
        String deployments = ctx.getCollectedData("deployments");

        StringBuilder sb = new StringBuilder();

        if (serviceName != null && !serviceName.isEmpty()) {
            sb.append("## 故障分析报告 - ").append(serviceName).append("\n\n");
        } else {
            sb.append("## 故障分析报告\n\n");
        }

        // Parse metrics
        appendMetricsSummary(sb, metrics);

        // Parse logs
        appendLogSummary(sb, logs);

        // Parse database
        appendDatabaseSummary(sb, dbStatus);

        // Generate root cause analysis
        sb.append("### 根因分析\n");
        String rootCause = generateRootCause(metrics, logs, dbStatus, traces);
        sb.append(rootCause).append("\n\n");

        // Generate recommendations
        sb.append("### 处理建议\n");
        if (rootCause.contains("数据库连接池") || rootCause.contains("慢 SQL")) {
            sb.append("1. 紧急扩容连接池（建议从 50 -> 100）\n");
            sb.append("2. 为 orders(user_id, status, created_at) 添加复合索引优化慢 SQL\n");
            sb.append("3. 考虑对 /create 接口做限流保护\n\n");
            sb.append("是否需要执行扩容操作？\n");
        } else if (rootCause.contains("NullPointer") || rootCause.contains("NPE")) {
            sb.append("1. 立即修复 RoleService.checkPermission() 中的空指针问题\n");
            sb.append("2. 在 UserController 入口处增加参数校验\n");
            sb.append("3. 监控 user-service 的错误率是否持续下降\n\n");
        } else {
            sb.append("1. 建议进一步排查相关服务的详细日志\n");
            sb.append("2. 检查服务最近是否有代码变更\n\n");
        }

        return sb.toString();
    }

    private static void appendMetricsSummary(StringBuilder sb, String metricsJson) {
        if (metricsJson == null || metricsJson.isEmpty()) return;
        try {
            JsonNode root = objectMapper.readTree(metricsJson);

            if (root.has("metrics")) {
                JsonNode m = root.get("metrics");
                double cpu = m.path("cpu").path("usage_percent").asDouble(0);
                double mem = m.path("memory").path("usage_percent").asDouble(0);
                double errorRate = m.path("http").path("error_rate_percent").asDouble(0);
                double p99 = m.path("http").path("p99_latency_ms").asDouble(0);

                sb.append("### 监控指标\n");
                sb.append(String.format("- CPU 使用率: %.1f%%\n", cpu));
                sb.append(String.format("- 内存使用率: %.1f%%\n", mem));
                sb.append(String.format("- 错误率: %.1f%%\n", errorRate));
                sb.append(String.format("- P99 延迟: %.0fms\n", p99));

                if (root.has("alerts") && root.get("alerts").isArray()) {
                    List<JsonNode> alerts = StreamSupport.stream(root.get("alerts").spliterator(), false)
                            .collect(Collectors.toList());
                    if (!alerts.isEmpty()) {
                        sb.append("- 告警: ");
                        sb.append(alerts.stream()
                                .map(a -> a.path("message").asText())
                                .collect(Collectors.joining("; ")));
                        sb.append("\n");
                    }
                }
                sb.append("\n");
            }
            if (root.has("services") && root.get("services").isArray()) {
                sb.append("### 各服务指标\n");
                for (JsonNode service : root.get("services")) {
                    String name = service.path("service").asText("unknown");
                    JsonNode m = service.path("metrics");
                    double errorRate = m.path("http").path("error_rate_percent").asDouble(0);
                    String status = errorRate > 3 ? "⚠️ 异常" : "✅ 健康";
                    sb.append(String.format("- %s: %s (错误率 %.1f%%)\n", name, status, errorRate));
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            // skip if parsing fails
        }
    }

    private static void appendLogSummary(StringBuilder sb, String logs) {
        if (logs == null || logs.trim().isEmpty() || logs.contains("未找到匹配的日志")) return;
        try {
            long errorCount = logs.lines().filter(l -> l.contains("ERROR")).count();
            long warnCount = logs.lines().filter(l -> l.contains("WARN")).count();
            sb.append("### 日志分析\n");
            sb.append(String.format("- ERROR 日志: %d 条\n", errorCount));
            sb.append(String.format("- WARN 日志: %d 条\n", warnCount));

            logs.lines()
                    .filter(l -> l.contains("ERROR") || l.contains("WARN"))
                    .limit(3)
                    .forEach(l -> {
                        String msg = l.length() > 120 ? l.substring(0, 120) + "..." : l;
                        sb.append("  - ").append(msg).append("\n");
                    });
            sb.append("\n");
        } catch (Exception e) {
            // skip
        }
    }

    private static void appendDatabaseSummary(StringBuilder sb, String dbJson) {
        if (dbJson == null || dbJson.isEmpty()) return;
        try {
            JsonNode root = objectMapper.readTree(dbJson);
            if (root.has("instances")) {
                sb.append("### 数据库状态\n");
                for (JsonNode instance : root.get("instances")) {
                    String name = instance.path("name").asText("unknown");
                    String status = instance.path("status").asText("UNKNOWN");
                    String host = instance.path("host").asText("");
                    String type = instance.path("type").asText("");

                    // 连接池（真实 + Mock 通用）
                    if (instance.has("connection_pool")) {
                        JsonNode pool = instance.get("connection_pool");
                        int usage = pool.path("usage_percent").asInt(0);
                        int active = pool.path("active").asInt(0);
                        int max = pool.path("max").asInt(0);
                        sb.append(String.format("- %s [%s] (%s): %s (连接池 %d%% 活跃 %d/%d)",
                                name, type, host, status, usage, active, max));
                    } else {
                        sb.append(String.format("- %s (%s): %s", name, type, status));
                    }
                    sb.append("\n");

                    // 慢查询（真实格式: slow_queries_analysis）
                    if (instance.has("slow_queries_analysis")) {
                        sb.append("  SQL 性能分析（TOP 耗时）:\n");
                        for (JsonNode sq : instance.get("slow_queries_analysis")) {
                            double avgMs = sq.path("avg_time_ms").asDouble(0);
                            long count = sq.path("execution_count").asLong(0);
                            double totalSec = sq.path("total_time_sec").asDouble(0);
                            String pattern = sq.path("sql_pattern").asText("");
                            String warn = sq.path("warning").asText("");
                            if (avgMs > 100 || count > 100) {
                                sb.append(String.format("  ⚠️ 平均 %.0fms x %d次 (共 %.1fs): %s %s\n",
                                        avgMs, count, totalSec,
                                        pattern.length() > 60 ? pattern.substring(0, 60) + "..." : pattern,
                                        warn.isEmpty() ? "" : "[" + warn + "]"));
                            }
                        }
                    }

                    // 慢查询（Mock 格式: slow_queries）
                    if (instance.has("slow_queries")) {
                        for (JsonNode sq : instance.get("slow_queries")) {
                            long avgMs = sq.path("avg_duration_ms").asLong(0);
                            int count = sq.path("count_5min").asInt(0);
                            if (avgMs > 1000) {
                                String sql = sq.path("sql").asText("");
                                sb.append(String.format("  ⚠️ 慢SQL(%dms, %d次/5min): %s\n",
                                        avgMs, count, sql.length() > 80 ? sql.substring(0, 80) + "..." : sql));
                            }
                        }
                    }

                    // 运行中查询（真实格式）
                    if (instance.has("running_queries") && instance.get("running_queries").isArray()
                            && instance.get("running_queries").size() > 0) {
                        sb.append("  运行中的查询:\n");
                        for (JsonNode q : instance.get("running_queries")) {
                            long timeSec = q.path("time_sec").asLong(0);
                            if (timeSec > 5) {
                                sb.append(String.format("  ⚡ %ds: [%s] %s %s\n",
                                        timeSec, q.path("user").asText(),
                                        q.path("state").asText(),
                                        q.path("sql").asText("").length() > 60
                                                ? q.path("sql").asText("").substring(0, 60) + "..."
                                                : q.path("sql").asText("")));
                            }
                        }
                    }

                    // 慢查询日志（实时 slow_log）
                    if (instance.has("slow_log_recent") && instance.get("slow_log_recent").isArray()
                            && instance.get("slow_log_recent").size() > 0) {
                        sb.append("  最近 30 分钟慢查询日志:\n");
                        for (JsonNode s : instance.get("slow_log_recent")) {
                            sb.append(String.format("  ⏱ %s %s | %s | 扫描 %d 行\n",
                                    s.path("start_time").asText(),
                                    s.path("query_time").asText(),
                                    s.path("sql_text").asText("").length() > 80
                                            ? s.path("sql_text").asText("").substring(0, 80) + "..."
                                            : s.path("sql_text").asText(""),
                                    s.path("rows_examined").asLong(0)));
                        }
                    }

                    // 表健康检查
                    if (instance.has("table_health")) {
                        long warnTables = 0;
                        for (JsonNode t : instance.get("table_health")) {
                            if (t.has("warning")) warnTables++;
                        }
                        if (warnTables > 0) {
                            sb.append(String.format("  🏥 表健康: %d 张表有告警\n", warnTables));
                            for (JsonNode t : instance.get("table_health")) {
                                if (t.has("warning")) {
                                    sb.append(String.format("    ⚠️ %s.%s: %s\n",
                                            t.path("schema").asText(), t.path("table").asText(),
                                            t.path("warning").asText()));
                                }
                            }
                        }
                    }
                }
                sb.append("\n");
            }
        } catch (Exception e) {
            // skip
        }
    }

    private static String generateRootCause(String metrics, String logs, String db, String traces) {
        List<String> causes = new java.util.ArrayList<>();

        if (metrics != null) {
            try {
                JsonNode root = objectMapper.readTree(metrics);
                if (root.has("metrics")) {
                    double errorRate = root.path("metrics").path("http").path("error_rate_percent").asDouble(0);
                    double p99 = root.path("metrics").path("http").path("p99_latency_ms").asDouble(0);
                    if (errorRate > 10) causes.add(String.format("错误率异常(%.1f%%)", errorRate));
                    if (p99 > 2000) causes.add(String.format("P99延迟过高(%.0fms)", p99));
                }
            } catch (Exception ignored) {
            }
        }

        if (db != null) {
            try {
                JsonNode root = objectMapper.readTree(db);
                if (root.has("instances")) {
                    for (JsonNode ins : root.get("instances")) {
                        // 连接池耗尽
                        int poolUsage = ins.path("connection_pool").path("usage_percent").asInt(0);
                        if (poolUsage >= 100) causes.add("数据库连接池耗尽");
                        else if (poolUsage >= 80) causes.add(String.format("连接池使用率过高(%d%%)", poolUsage));

                        // 慢查询分析（真实格式）
                        if (ins.has("slow_queries_analysis")) {
                            for (JsonNode sq : ins.get("slow_queries_analysis")) {
                                double avgMs = sq.path("avg_time_ms").asDouble(0);
                                if (avgMs > 500) causes.add(String.format("慢查询(平均%.0fms)", avgMs));
                                if (sq.path("no_index_count").asLong(0) > 0) causes.add("存在未使用索引的查询");
                                if (sq.has("warning") && !sq.path("warning").asText().isEmpty()) {
                                    causes.add(sq.path("warning").asText());
                                }
                            }
                        }

                        // 慢查询（Mock 格式）
                        if (ins.has("slow_queries")) {
                            for (JsonNode sq : ins.get("slow_queries")) {
                                long avgMs = sq.path("avg_duration_ms").asLong(0);
                                if (avgMs > 1000) causes.add("慢SQL导致连接堆积");
                            }
                        }

                        // 长时间运行的查询
                        if (ins.has("running_queries")) {
                            for (JsonNode q : ins.get("running_queries")) {
                                long timeSec = q.path("time_sec").asLong(0);
                                if (timeSec > 30) causes.add(String.format("查询运行超时(%ds)", timeSec));
                            }
                        }

                        // 表缺少主键
                        if (ins.has("table_health")) {
                            for (JsonNode t : ins.get("table_health")) {
                                if (t.has("warning") && "缺少主键".equals(t.path("warning").asText())) {
                                    causes.add(String.format("表 %s 缺少主键", t.path("table").asText()));
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (logs != null && logs.contains("NullPointerException")) {
            causes.add("代码空指针异常(NPE)");
        }

        if (causes.isEmpty()) {
            return "根据采集的数据分析，服务存在异常指标，建议进一步检查。";
        }
        return "根因分析: " + String.join(" + ", causes) + "。综合判断为性能瓶颈和代码缺陷。";
    }

    public static String analyzeHealthCheckData(InvestigationContext ctx) {
        String allMetrics = ctx.getCollectedData("health_metrics");
        String logs = ctx.getCollectedData("health_logs");
        String db = ctx.getCollectedData("health_database");

        StringBuilder sb = new StringBuilder();
        sb.append("## 健康巡检报告\n\n");

        if (allMetrics != null) {
            appendMetricsSummary(sb, allMetrics);
        }

        if (logs != null) {
            appendLogSummary(sb, logs);
        }

        if (db != null) {
            appendDatabaseSummary(sb, db);
        }

        // Summary
        sb.append("### 巡检总结\n");
        boolean hasIssue = false;
        if (allMetrics != null) {
            try {
                JsonNode root = objectMapper.readTree(allMetrics);
                if (root.has("services")) {
                    for (JsonNode svc : root.get("services")) {
                        String name = svc.path("service").asText("unknown");
                        double errRate = svc.path("metrics").path("http").path("error_rate_percent").asDouble(0);
                        if (errRate > 3) {
                            sb.append(String.format("- %s: ⚠️ 异常（错误率 %.1f%%）\n", name, errRate));
                            hasIssue = true;
                        } else {
                            sb.append(String.format("- %s: ✅ 健康\n", name));
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (!hasIssue) {
            sb.append("所有服务运行正常，未发现异常。\n");
        }

        return sb.toString();
    }
}
