package com.devboss.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RedisService {

    private static final Logger log = LoggerFactory.getLogger(RedisService.class);
    private final ObjectMapper objectMapper;

    public RedisService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 获取 Redis INFO 各分段信息
     * 返回包含 server / memory / stats / cpu 的 JSON
     */
    public String getRedisInfo() {
        try {
            return readMockRedisInfo();
        } catch (Exception e) {
            log.error("获取 Redis INFO 失败", e);
            return "{\"error\": \"获取 Redis INFO 失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取慢查询日志
     * 返回慢日志数组 JSON
     */
    public String getSlowLog() {
        try {
            return readMockSlowLog();
        } catch (Exception e) {
            log.error("获取 Redis 慢查询日志失败", e);
            return "{\"error\": \"获取 Redis 慢查询日志失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取 Key 空间汇总
     * 返回 db0..db15 keys / expires / avg_ttl JSON
     */
    public String getKeySummary() {
        try {
            return readMockKeySummary();
        } catch (Exception e) {
            log.error("获取 Redis Key 汇总失败", e);
            return "{\"error\": \"获取 Redis Key 汇总失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取客户端连接列表
     * 返回客户端连接数组 JSON
     */
    public String getClientList() {
        try {
            return readMockClientList();
        } catch (Exception e) {
            log.error("获取 Redis 客户端列表失败", e);
            return "{\"error\": \"获取 Redis 客户端列表失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取 Redis 全量状态
     * 组合 info / slowlog / keys / clients 到统一 JSON
     */
    public String getFullStatus() {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("configured", true);

            // Info sections
            String infoJson = getRedisInfo();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> infoMap = objectMapper.readValue(infoJson, java.util.Map.class);
            result.set("server", objectMapper.valueToTree(infoMap.get("server")));
            result.set("memory", objectMapper.valueToTree(infoMap.get("memory")));
            result.set("stats", objectMapper.valueToTree(infoMap.get("stats")));
            result.set("cpu", objectMapper.valueToTree(infoMap.get("cpu")));

            // Slow log
            String slowLogJson = getSlowLog();
            result.set("slowlog", objectMapper.readTree(slowLogJson));

            // Key summary
            String keyJson = getKeySummary();
            result.set("keyspace", objectMapper.readTree(keyJson));

            // Client list
            String clientJson = getClientList();
            result.set("clients", objectMapper.readTree(clientJson));

            // Top-level summary fields
            ObjectNode memory = (ObjectNode) result.get("memory");
            ObjectNode stats = (ObjectNode) result.get("stats");
            ObjectNode server = (ObjectNode) result.get("server");
            ObjectNode keyspace = (ObjectNode) result.get("keyspace");

            result.put("used_memory_human", memory != null ? memory.path("used_memory_human").asText("N/A") : "N/A");
            result.put("total_keys", keyspace != null ? keyspace.path("total_keys").asInt(0) : 0);
            result.put("connected_clients", stats != null ? stats.path("connected_clients").asInt(0) : 0);
            result.put("hit_ratio", stats != null ? stats.path("hit_ratio").asText("N/A") : "N/A");
            result.put("uptime", server != null ? server.path("uptime").asText("N/A") : "N/A");
            result.put("version", server != null ? server.path("version").asText("N/A") : "N/A");

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.error("获取 Redis 全量状态失败", e);
            return "{\"error\": \"获取 Redis 全量状态失败: " + e.getMessage() + "\"}";
        }
    }

    // ========== Mock Data Methods ==========

    private String readMockRedisInfo() {
        try {
            ObjectNode root = objectMapper.createObjectNode();

            // Server section
            ObjectNode server = root.putObject("server");
            server.put("version", "7.2.4");
            server.put("uptime", "15d 6h");
            server.put("uptime_in_seconds", 1324800);
            server.put("os", "Linux 5.15.0-105-generic x86_64");
            server.put("arch_bits", 64);
            server.put("multiplexing_api", "epoll");
            server.put("process_id", 2781);
            server.put("tcp_port", 6379);
            server.put("server_time_usec", System.currentTimeMillis() * 1000);

            // Memory section
            ObjectNode memory = root.putObject("memory");
            memory.put("used_memory_human", "10.5M");
            memory.put("used_memory", 11010048);
            memory.put("used_memory_rss", "22.3M");
            memory.put("used_memory_peak_human", "15.2M");
            memory.put("used_memory_peak", 15938355);
            memory.put("used_memory_lua", "37.00K");
            memory.put("used_memory_overhead", "4.1M");
            memory.put("used_memory_startup", "812.5K");
            memory.put("mem_fragmentation_ratio", 2.12);
            memory.put("maxmemory_human", "512M");
            memory.put("maxmemory_policy", "noeviction");

            // Stats section
            ObjectNode stats = root.putObject("stats");
            stats.put("total_connections_received", 1500);
            stats.put("total_commands_processed", 1200000);
            stats.put("total_net_input_bytes", 8589934592L);
            stats.put("total_net_output_bytes", 34359738368L);
            stats.put("instantaneous_ops_per_sec", 1250);
            stats.put("instantaneous_input_kbps", 12.5);
            stats.put("instantaneous_output_kbps", 48.2);
            stats.put("keyspace_hits", 985000);
            stats.put("keyspace_misses", 7935);
            stats.put("hit_ratio", "99.2%");
            stats.put("connected_clients", 47);
            stats.put("blocked_clients", 0);
            stats.put("rejected_connections", 3);
            stats.put("expired_keys", 12800);
            stats.put("evicted_keys", 0);

            // CPU section
            ObjectNode cpu = root.putObject("cpu");
            cpu.put("used_cpu_sys", "125.84");
            cpu.put("used_cpu_user", "342.15");
            cpu.put("used_cpu_sys_children", "0.02");
            cpu.put("used_cpu_user_children", "0.01");

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return "{\"error\": \"生成 Mock Redis INFO 失败: " + e.getMessage() + "\"}";
        }
    }

    private String readMockSlowLog() {
        try {
            ArrayNode array = objectMapper.createArrayNode();

            ObjectNode entry1 = array.addObject();
            entry1.put("id", 3);
            entry1.put("timestamp", System.currentTimeMillis() - 30000);
            entry1.put("duration_us", 12500);
            entry1.put("command_args", "KEYS user:*");

            ObjectNode entry2 = array.addObject();
            entry2.put("id", 2);
            entry2.put("timestamp", System.currentTimeMillis() - 120000);
            entry2.put("duration_us", 8700);
            entry2.put("command_args", "SMEMBERS online_users");

            ObjectNode entry3 = array.addObject();
            entry3.put("id", 1);
            entry3.put("timestamp", System.currentTimeMillis() - 300000);
            entry3.put("duration_us", 15200);
            entry3.put("command_args", "ZRANGE leaderboard 0 100 WITHSCORES");

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(array);
        } catch (Exception e) {
            return "{\"error\": \"生成 Mock 慢查询日志失败: " + e.getMessage() + "\"}";
        }
    }

    private String readMockKeySummary() {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode databases = root.putArray("databases");

            ObjectNode db0 = databases.addObject();
            db0.put("db", "db0");
            db0.put("keys", 15230);
            db0.put("expires", 3200);
            db0.put("avg_ttl", 86400);

            ObjectNode db1 = databases.addObject();
            db1.put("db", "db1");
            db1.put("keys", 890);
            db1.put("expires", 45);
            db1.put("avg_ttl", 7200);

            ObjectNode db2 = databases.addObject();
            db2.put("db", "db2");
            db2.put("keys", 15);
            db2.put("expires", 0);
            db2.put("avg_ttl", -1);

            ObjectNode db3 = databases.addObject();
            db3.put("db", "db3");
            db3.put("keys", 6700);
            db3.put("expires", 6700);
            db3.put("avg_ttl", 3600);

            // 其余 db4-db15 为空
            for (int i = 4; i <= 15; i++) {
                ObjectNode empty = databases.addObject();
                empty.put("db", "db" + i);
                empty.put("keys", 0);
                empty.put("expires", 0);
                empty.put("avg_ttl", -1);
            }

            // total_keys 汇总
            int total = 0;
            for (java.util.Iterator<com.fasterxml.jackson.databind.JsonNode> it = databases.elements(); it.hasNext(); ) {
                total += it.next().path("keys").asInt(0);
            }
            root.put("total_keys", total);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return "{\"error\": \"生成 Mock Key 汇总失败: " + e.getMessage() + "\"}";
        }
    }

    private String readMockClientList() {
        try {
            ArrayNode array = objectMapper.createArrayNode();

            ObjectNode c1 = array.addObject();
            c1.put("id", 47);
            c1.put("addr", "192.168.1.100:54321");
            c1.put("fd", 12);
            c1.put("age", 3600);
            c1.put("idle", 15);
            c1.put("db", 0);
            c1.put("sub", 0);
            c1.put("psub", 0);
            c1.put("multi", -1);
            c1.put("flags", "N");
            c1.put("qbuf", 0);
            c1.put("qbuf_free", 16384);
            c1.put("obl", 0);
            c1.put("oll", 0);
            c1.put("omem", 0);

            ObjectNode c2 = array.addObject();
            c2.put("id", 48);
            c2.put("addr", "192.168.1.101:54322");
            c2.put("fd", 13);
            c2.put("age", 1800);
            c2.put("idle", 2);
            c2.put("db", 1);
            c2.put("sub", 1);
            c2.put("psub", 0);
            c2.put("multi", -1);
            c2.put("flags", "N");
            c2.put("qbuf", 0);
            c2.put("qbuf_free", 16384);
            c2.put("obl", 0);
            c2.put("oll", 0);
            c2.put("omem", 0);

            ObjectNode c3 = array.addObject();
            c3.put("id", 49);
            c3.put("addr", "192.168.1.102:54323");
            c3.put("fd", 14);
            c3.put("age", 600);
            c3.put("idle", 120);
            c3.put("db", 0);
            c3.put("sub", 0);
            c3.put("psub", 0);
            c3.put("multi", -1);
            c3.put("flags", "N");
            c3.put("qbuf", 0);
            c3.put("qbuf_free", 16384);
            c3.put("obl", 0);
            c3.put("oll", 0);
            c3.put("omem", 0);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(array);
        } catch (Exception e) {
            return "{\"error\": \"生成 Mock 客户端列表失败: " + e.getMessage() + "\"}";
        }
    }
}
