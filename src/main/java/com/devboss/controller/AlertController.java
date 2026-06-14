package com.devboss.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 告警管理接口：告警规则与历史记录查询
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private static final Logger log = LoggerFactory.getLogger(AlertController.class);
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));

    private final ObjectMapper objectMapper;

    /** In-memory mock alerts */
    private final List<Map<String, Object>> alerts;

    public AlertController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.alerts = buildMockAlerts();
    }

    // ---------------------------------------------------------------
    //  GET /api/alerts/status  —  Combined alert status
    // ---------------------------------------------------------------
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        try {
            List<Map<String, Object>> activeAlerts = new ArrayList<>(alerts);
            long total = activeAlerts.size();
            long critical = activeAlerts.stream().filter(a -> "critical".equals(a.get("severity"))).count();
            long warning  = activeAlerts.stream().filter(a -> "warning".equals(a.get("severity"))).count();
            long info     = activeAlerts.stream().filter(a -> "info".equals(a.get("severity"))).count();
            long silenced = activeAlerts.stream().filter(a -> "silenced".equals(a.get("status"))).count();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("total_alerts", total);
            body.put("critical_count", critical);
            body.put("warning_count", warning);
            body.put("info_count", info);
            body.put("silenced_count", silenced);
            body.put("alerts", activeAlerts);

            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("查询告警状态失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    //  POST /api/alerts/silence/{alertId}
    // ---------------------------------------------------------------
    @PostMapping("/silence/{alertId}")
    public ResponseEntity<Map<String, Object>> silence(@PathVariable Integer alertId) {
        try {
            Optional<Map<String, Object>> target = alerts.stream()
                    .filter(a -> alertId.equals(a.get("id"))).findFirst();
            if (target.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "告警不存在: " + alertId));
            }
            target.get().put("status", "silenced");
            log.info("告警 {} 已静音", alertId);
            return ResponseEntity.ok(Map.of("success", true, "alert_id", alertId, "status", "silenced"));
        } catch (Exception e) {
            log.error("静音告警失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    //  POST /api/alerts/unsilence/{alertId}
    // ---------------------------------------------------------------
    @PostMapping("/unsilence/{alertId}")
    public ResponseEntity<Map<String, Object>> unsilence(@PathVariable Integer alertId) {
        try {
            Optional<Map<String, Object>> target = alerts.stream()
                    .filter(a -> alertId.equals(a.get("id"))).findFirst();
            if (target.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "告警不存在: " + alertId));
            }
            target.get().put("status", "active");
            log.info("告警 {} 已取消静音", alertId);
            return ResponseEntity.ok(Map.of("success", true, "alert_id", alertId, "status", "active"));
        } catch (Exception e) {
            log.error("取消静音失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    //  GET /api/alerts/stats  —  Aggregated stats by source & severity
    // ---------------------------------------------------------------
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        try {
            // Group by source
            Map<String, Long> bySource = alerts.stream()
                    .collect(Collectors.groupingBy(
                            a -> (String) a.get("source"), Collectors.counting()));

            // Group by severity
            Map<String, Long> bySeverity = alerts.stream()
                    .collect(Collectors.groupingBy(
                            a -> (String) a.get("severity"), Collectors.counting()));

            // Per-source breakdown
            List<Map<String, Object>> sourceBreakdown = bySource.entrySet().stream()
                    .map(e -> {
                        String src = e.getKey();
                        long critical = alerts.stream()
                                .filter(a -> src.equals(a.get("source")) && "critical".equals(a.get("severity"))).count();
                        long warning  = alerts.stream()
                                .filter(a -> src.equals(a.get("source")) && "warning".equals(a.get("severity"))).count();
                        long info     = alerts.stream()
                                .filter(a -> src.equals(a.get("source")) && "info".equals(a.get("severity"))).count();
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("source", src);
                        item.put("total", e.getValue());
                        item.put("critical", critical);
                        item.put("warning", warning);
                        item.put("info", info);
                        return item;
                    })
                    .sorted(Comparator.comparingLong(a -> - (Long) a.get("total")))
                    .collect(Collectors.toList());

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("total_alerts", alerts.size());
            body.put("by_source", bySource);
            body.put("by_severity", bySeverity);
            body.put("source_breakdown", sourceBreakdown);

            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("查询告警统计失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    //  Mock data builder
    // ---------------------------------------------------------------
    private List<Map<String, Object>> buildMockAlerts() {
        String now = FORMATTER.format(Instant.now());

        List<Map<String, Object>> list = new ArrayList<>();

        list.add(alert(1,  "mysql",    "connection_pool_high", "critical", "MySQL 连接池使用率 87%",                         now, "active"));
        list.add(alert(2,  "redis",    "memory_high",          "warning",  "Redis 内存使用率 78%",                           now, "active"));
        list.add(alert(3,  "rabbitmq", "queue_backlog",        "critical", "notification.queue 积压 305 条",                 now, "active"));
        list.add(alert(4,  "system",   "disk_high",            "warning",  "/data 磁盘使用率 82%",                           now, "active"));
        list.add(alert(5,  "es",       "cluster_yellow",       "warning",  "ES 集群状态 yellow, 3 个未分配分片",             now, "active"));
        list.add(alert(6,  "docker",   "container_down",       "critical", "容器 old-app 已退出",                            now, "active"));
        list.add(alert(7,  "k8s",      "pod_pending",          "warning",  "Pod user-service-8f3b2c1-ghi2 Pending (ImagePullBackOff)", now, "active"));
        list.add(alert(8,  "mysql",    "deadlock",             "warning",  "检测到死锁",                                     now, "silenced"));
        list.add(alert(9,  "ssl",      "cert_expiring",        "warning",  "admin.example.com 证书 12 天后到期",            now, "active"));
        list.add(alert(10, "nginx",    "upstream_down",        "info",     "Nginx upstream api-server 不可用",               now, "active"));
        list.add(alert(11, "system",   "load_high",            "info",     "系统 15 分钟负载 4.5",                           now, "active"));
        list.add(alert(12, "k8s",      "namespace_quota",      "info",     "Namespace production 资源配额使用率 85%",        now, "silenced"));

        return list;
    }

    private Map<String, Object> alert(int id, String source, String type, String severity,
                                       String message, String timestamp, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("source", source);
        m.put("type", type);
        m.put("severity", severity);
        m.put("message", message);
        m.put("timestamp", timestamp);
        m.put("status", status);
        return m;
    }
}
