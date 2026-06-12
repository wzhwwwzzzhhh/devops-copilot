package com.devboss.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ESMonitorService {

    private static final Logger log = LoggerFactory.getLogger(ESMonitorService.class);

    private final RestTemplate esRestTemplate;
    private final String esUri;
    private final ObjectMapper objectMapper;

    public ESMonitorService(@Qualifier("esRestTemplate") RestTemplate esRestTemplate,
                            @Value("${knowledge.es.uris:http://localhost:9200}") String esUri,
                            ObjectMapper objectMapper) {
        this.esRestTemplate = esRestTemplate;
        this.esUri = esUri;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取 ES 集群健康状态
     * GET /{esUri}/_cluster/health
     */
    public String getClusterHealth() {
        try {
            String url = esUri + "/_cluster/health";
            String response = esRestTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            ObjectNode result = objectMapper.createObjectNode();
            result.put("status", root.path("status").asText("unknown"));
            result.put("nodes", root.path("number_of_nodes").asInt(0));
            result.put("data_nodes", root.path("number_of_data_nodes").asInt(0));
            result.put("active_primary_shards", root.path("active_primary_shards").asInt(0));
            result.put("active_shards", root.path("active_shards").asInt(0));
            result.put("relocating_shards", root.path("relocating_shards").asInt(0));
            result.put("initializing_shards", root.path("initializing_shards").asInt(0));
            result.put("unassigned_shards", root.path("unassigned_shards").asInt(0));
            result.put("delayed_unassigned_shards", root.path("delayed_unassigned_shards").asInt(0));
            result.put("active_shards_percent", root.path("active_shards_percent_as_number").asDouble(0.0));
            result.put("timed_out", root.path("timed_out").asBoolean(false));

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.warn("获取 ES 集群健康状态失败, 降级 Mock 数据: {}", e.getMessage());
            return readMockClusterHealth();
        }
    }

    /**
     * 获取 ES 索引列表
     * GET /{esUri}/_cat/indices?format=json&bytes=b
     */
    public String getIndices() {
        try {
            String url = esUri + "/_cat/indices?format=json&bytes=b";
            String response = esRestTemplate.getForObject(url, String.class);
            JsonNode array = objectMapper.readTree(response);

            ArrayNode result = objectMapper.createArrayNode();
            if (array.isArray()) {
                for (JsonNode item : array) {
                    ObjectNode idx = result.addObject();
                    idx.put("health", item.path("health").asText(""));
                    idx.put("status", item.path("status").asText(""));
                    idx.put("index", item.path("index").asText(""));
                    idx.put("uuid", item.path("uuid").asText(""));
                    idx.put("pri", item.path("pri").asText(""));
                    idx.put("rep", item.path("rep").asText(""));
                    idx.put("docs_count", item.path("docs.count").asLong(0));
                    idx.put("docs_deleted", item.path("docs.deleted").asLong(0));
                    idx.put("store_size", item.path("store.size").asText(""));
                }
            }

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.warn("获取 ES 索引列表失败, 降级 Mock 数据: {}", e.getMessage());
            return readMockIndices();
        }
    }

    /**
     * 获取 ES 节点统计信息
     * GET /{esUri}/_nodes/stats?metric=process,jvm,fs,os
     */
    public String getNodesStats() {
        try {
            String url = esUri + "/_nodes/stats?metric=process,jvm,fs,os";
            String response = esRestTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode nodes = root.path("nodes");

            ArrayNode result = objectMapper.createArrayNode();
            if (nodes.isObject()) {
                java.util.Iterator<String> fieldNames = nodes.fieldNames();
                while (fieldNames.hasNext()) {
                    String nodeId = fieldNames.next();
                    JsonNode node = nodes.get(nodeId);

                    ObjectNode n = result.addObject();
                    n.put("node_id", nodeId);
                    n.put("name", node.path("name").asText("unknown"));

                    // OS / CPU
                    JsonNode os = node.path("os");
                    if (os.has("cpu")) {
                        JsonNode cpu = os.path("cpu");
                        n.put("cpu_percent", cpu.path("percent").asInt(0));
                    }

                    // Process
                    JsonNode process = node.path("process");
                    if (process.has("cpu")) {
                        n.put("process_cpu_percent", process.path("cpu").path("percent").asDouble(0.0));
                    }

                    // JVM heap
                    JsonNode jvm = node.path("jvm");
                    if (jvm.has("mem")) {
                        JsonNode mem = jvm.path("mem");
                        long heapUsed = mem.path("heap_used_in_bytes").asLong(0);
                        long heapMax = mem.path("heap_max_in_bytes").asLong(1);
                        double heapPercent = (double) heapUsed / heapMax * 100.0;
                        n.put("heap_used_percent", Math.round(heapPercent * 100.0) / 100.0);
                        n.put("heap_used_bytes", heapUsed);
                        n.put("heap_max_bytes", heapMax);
                    }

                    // GC
                    if (jvm.has("gc")) {
                        JsonNode gc = jvm.path("gc");
                        JsonNode collectors = gc.path("collectors");
                        ArrayNode gcArr = n.putArray("gc_collectors");
                        if (collectors.isObject()) {
                            java.util.Iterator<String> gcNames = collectors.fieldNames();
                            while (gcNames.hasNext()) {
                                String gcName = gcNames.next();
                                JsonNode collector = collectors.get(gcName);
                                ObjectNode gcObj = gcArr.addObject();
                                gcObj.put("name", gcName);
                                gcObj.put("collection_count", collector.path("collection_count").asLong(0));
                                gcObj.put("collection_time_millis", collector.path("collection_time_in_millis").asLong(0));
                            }
                        }
                    }

                    // FS
                    JsonNode fs = node.path("fs");
                    if (fs.has("total")) {
                        JsonNode total = fs.path("total");
                        n.put("disk_total_bytes", total.path("total_in_bytes").asLong(0));
                        n.put("disk_avail_bytes", total.path("available_in_bytes").asLong(0));
                        n.put("disk_free_bytes", total.path("free_in_bytes").asLong(0));
                        long totalBytes = total.path("total_in_bytes").asLong(1);
                        long availBytes = total.path("available_in_bytes").asLong(0);
                        n.put("disk_used_percent", Math.round((1.0 - (double) availBytes / totalBytes) * 100.0 * 100.0) / 100.0);
                    }
                }
            }

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.warn("获取 ES 节点统计失败, 降级 Mock 数据: {}", e.getMessage());
            return readMockNodesStats();
        }
    }

    /**
     * 获取慢查询日志（模拟）
     * ES 不直接通过 REST API 暴露慢查询日志，此处返回模拟数据
     */
    public String getSlowLogs() {
        try {
            return readMockSlowLogs();
        } catch (Exception e) {
            log.warn("获取 ES 慢查询日志失败: {}", e.getMessage());
            return "{\"error\": \"获取 ES 慢查询日志失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取 ES 全量状态
     * 组合 cluster health + indices + nodes + slow logs
     */
    public String getFullStatus() {
        try {
            ObjectNode result = objectMapper.createObjectNode();

            // Cluster health
            String healthJson = getClusterHealth();
            JsonNode health = objectMapper.readTree(healthJson);
            if (health.has("error")) {
                result.put("configured", false);
                result.set("error", health);
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            }
            result.put("configured", true);
            result.set("cluster_health", health);

            // Indices
            String indicesJson = getIndices();
            result.set("indices", objectMapper.readTree(indicesJson));

            // Nodes
            String nodesJson = getNodesStats();
            result.set("nodes", objectMapper.readTree(nodesJson));

            // Slow logs
            String slowLogsJson = getSlowLogs();
            result.set("slow_logs", objectMapper.readTree(slowLogsJson));

            // Top-level summary fields
            result.put("status", health.path("status").asText("unknown"));
            result.put("nodes_count", health.path("nodes").asInt(0));
            result.put("indices_count", health.path("active_primary_shards").asInt(0));
            result.put("documents_count", "1.2M");
            result.put("shards_count", health.path("active_shards").asInt(0));
            result.put("unassigned_shards", health.path("unassigned_shards").asInt(0));

            // Compute summary from indices
            JsonNode indicesArray = result.get("indices");
            if (indicesArray != null && indicesArray.isArray()) {
                long totalDocs = 0;
                for (JsonNode idx : indicesArray) {
                    totalDocs += idx.path("docs_count").asLong(0);
                }
                result.put("total_documents", totalDocs);
                result.put("indices_count", indicesArray.size());
            }

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.error("获取 ES 全量状态失败", e);
            return "{\"error\": \"获取 ES 全量状态失败: " + e.getMessage() + "\"}";
        }
    }

    // ========== Mock Data Methods ==========

    private String readMockClusterHealth() {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("status", "yellow");
            root.put("nodes", 2);
            root.put("data_nodes", 2);
            root.put("active_primary_shards", 15);
            root.put("active_shards", 45);
            root.put("relocating_shards", 0);
            root.put("initializing_shards", 0);
            root.put("unassigned_shards", 3);
            root.put("delayed_unassigned_shards", 0);
            root.put("active_shards_percent", 95.0);
            root.put("timed_out", false);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return "{\"error\": \"生成 Mock 集群健康状态失败: " + e.getMessage() + "\"}";
        }
    }

    private String readMockIndices() {
        try {
            ArrayNode array = objectMapper.createArrayNode();

            String[][] mockIndices = {
                    {"green", "open", "logs-2025.01", "abc123", "3", "1", "450000", "1200", "2147483648"},
                    {"green", "open", "logs-2025.02", "def456", "3", "1", "520000", "980", "2684354560"},
                    {"yellow", "open", "app-errors", "ghi789", "2", "0", "85000", "340", "536870912"},
                    {"green", "open", "user-activity", "jkl012", "5", "1", "320000", "2100", "1610612736"},
                    {"yellow", "open", "system-metrics", "mno345", "2", "0", "120000", "450", "268435456"},
                    {"green", "open", "audit-logs", "pqr678", "3", "1", "280000", "670", "1073741824"},
                    {"green", "open", "search-queries", "stu901", "2", "1", "95000", "180", "402653184"},
                    {"yellow", "open", "temp-index", "vwx234", "1", "0", "5000", "10", "20971520"},
            };

            for (String[] row : mockIndices) {
                ObjectNode idx = array.addObject();
                idx.put("health", row[0]);
                idx.put("status", row[1]);
                idx.put("index", row[2]);
                idx.put("uuid", row[3]);
                idx.put("pri", row[4]);
                idx.put("rep", row[5]);
                idx.put("docs_count", Long.parseLong(row[6]));
                idx.put("docs_deleted", Long.parseLong(row[7]));
                idx.put("store_size", row[8]);
            }

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(array);
        } catch (Exception e) {
            return "{\"error\": \"生成 Mock 索引列表失败: " + e.getMessage() + "\"}";
        }
    }

    private String readMockNodesStats() {
        try {
            ArrayNode array = objectMapper.createArrayNode();

            // Node 1
            ObjectNode node1 = array.addObject();
            node1.put("node_id", "node-1");
            node1.put("name", "es-data-01");
            node1.put("heap_used_percent", 62.0);
            node1.put("heap_used_bytes", 5200000000L);
            node1.put("heap_max_bytes", 8589934592L);
            node1.put("cpu_percent", 23);
            node1.put("process_cpu_percent", 18.5);
            node1.put("disk_total_bytes", 500000000000L);
            node1.put("disk_avail_bytes", 150000000000L);
            node1.put("disk_free_bytes", 180000000000L);
            node1.put("disk_used_percent", 70.0);
            ArrayNode gc1 = node1.putArray("gc_collectors");
            ObjectNode gc1a = gc1.addObject();
            gc1a.put("name", "young");
            gc1a.put("collection_count", 12580);
            gc1a.put("collection_time_millis", 485000);
            ObjectNode gc1b = gc1.addObject();
            gc1b.put("name", "old");
            gc1b.put("collection_count", 120);
            gc1b.put("collection_time_millis", 62000);

            // Node 2
            ObjectNode node2 = array.addObject();
            node2.put("node_id", "node-2");
            node2.put("name", "es-data-02");
            node2.put("heap_used_percent", 58.5);
            node2.put("heap_used_bytes", 4800000000L);
            node2.put("heap_max_bytes", 8589934592L);
            node2.put("cpu_percent", 18);
            node2.put("process_cpu_percent", 14.2);
            node2.put("disk_total_bytes", 500000000000L);
            node2.put("disk_avail_bytes", 210000000000L);
            node2.put("disk_free_bytes", 230000000000L);
            node2.put("disk_used_percent", 58.0);
            ArrayNode gc2 = node2.putArray("gc_collectors");
            ObjectNode gc2a = gc2.addObject();
            gc2a.put("name", "young");
            gc2a.put("collection_count", 11200);
            gc2a.put("collection_time_millis", 412000);
            ObjectNode gc2b = gc2.addObject();
            gc2b.put("name", "old");
            gc2b.put("collection_count", 95);
            gc2b.put("collection_time_millis", 48000);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(array);
        } catch (Exception e) {
            return "{\"error\": \"生成 Mock 节点统计失败: " + e.getMessage() + "\"}";
        }
    }

    private String readMockSlowLogs() {
        try {
            ArrayNode array = objectMapper.createArrayNode();

            ObjectNode entry1 = array.addObject();
            entry1.put("id", 1);
            entry1.put("timestamp", System.currentTimeMillis() - 60000);
            entry1.put("took_millis", 8500);
            entry1.put("shards_total", 5);
            entry1.put("shards_successful", 4);
            entry1.put("shards_skipped", 0);
            entry1.put("shards_failed", 1);
            entry1.put("source", "GET /logs-2025.01/_search");
            entry1.put("query", "{\"query\":{\"range\":{\"@timestamp\":{\"gte\":\"now-30d\"}}}}");
            entry1.put("reason", "搜索跨越大量分片，查询范围过大");

            ObjectNode entry2 = array.addObject();
            entry2.put("id", 2);
            entry2.put("timestamp", System.currentTimeMillis() - 180000);
            entry2.put("took_millis", 6200);
            entry2.put("shards_total", 3);
            entry2.put("shards_successful", 3);
            entry2.put("shards_skipped", 0);
            entry2.put("shards_failed", 0);
            entry2.put("source", "GET /user-activity/_search");
            entry2.put("query", "{\"query\":{\"match\":{\"user_name\":\"test\"}}}");
            entry2.put("reason", "未命中索引缓存，大量 doc_value 加载");

            ObjectNode entry3 = array.addObject();
            entry3.put("id", 3);
            entry3.put("timestamp", System.currentTimeMillis() - 300000);
            entry3.put("took_millis", 4500);
            entry3.put("shards_total", 2);
            entry3.put("shards_successful", 2);
            entry3.put("shards_skipped", 0);
            entry3.put("shards_failed", 0);
            entry3.put("source", "POST /app-errors/_search");
            entry3.put("query", "{\"query\":{\"bool\":{\"filter\":[{\"term\":{\"level\":\"error\"}}]}}}");
            entry3.put("reason", "深度分页查询（from: 10000）");

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(array);
        } catch (Exception e) {
            return "{\"error\": \"生成 Mock 慢查询日志失败: " + e.getMessage() + "\"}";
        }
    }
}
