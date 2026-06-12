package com.devboss.controller;

import com.devboss.service.ServiceConnectionService;
import com.devboss.tools.DatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/database")
public class DatabaseMonitorController {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMonitorController.class);

    private final DatabaseService databaseService;
    private final ServiceConnectionService connectionService;

    /** 连接池使用率趋势缓存 - 最多保留 20 条 */
    private final List<Map<String, Object>> poolTrendCache = new ArrayList<>();

    public DatabaseMonitorController(DatabaseService databaseService,
                                     ServiceConnectionService connectionService) {
        this.databaseService = databaseService;
        this.connectionService = connectionService;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        try {
            long count = connectionService.findByType("mysql").size();
            if (count == 0) {
                return ResponseEntity.ok(Map.of(
                        "configured", false,
                        "message", "未配置 MySQL 服务连接"
                ));
            }
            String json = databaseService.getDbStatus(null);
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            data.put("configured", true);

            // 记录连接池使用率趋势
            try {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> instances = (List<Map<String, Object>>) data.get("instances");
                if (instances != null && !instances.isEmpty()) {
                    Map<String, Object> inst = instances.get(0);
                    Map<String, Object> pool = (Map<String, Object>) inst.get("connection_pool");
                    if (pool != null) {
                        Map<String, Object> point = new LinkedHashMap<>();
                        point.put("time", java.time.LocalTime.now().format(
                                java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
                        point.put("usage", pool.get("usage_percent"));
                        point.put("active", pool.get("active"));
                        poolTrendCache.add(point);
                        if (poolTrendCache.size() > 20) {
                            poolTrendCache.remove(0);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("记录趋势数据失败: {}", e.getMessage());
            }

            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("查询数据库状态失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "configured", false,
                    "error", e.getMessage()
            ));
        }
    }

    /** 获取连接池使用率趋势数据 */
    @GetMapping("/trend")
    public ResponseEntity<List<Map<String, Object>>> trend() {
        return ResponseEntity.ok(poolTrendCache);
    }

    /** EXPLAIN 执行计划分析 */
    @PostMapping("/explain")
    public ResponseEntity<Map<String, Object>> explain(@RequestBody Map<String, String> body) {
        String sql = body.get("sql");
        if (sql == null || sql.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "SQL 不能为空"));
        }
        log.info("EXPLAIN 分析: {}", sql);
        try {
            String result = databaseService.explainQuery(sql);
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(result, Map.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** 告警静默 */
    @PostMapping("/silence")
    public ResponseEntity<Map<String, Object>> silenceAlert(@RequestBody Map<String, Object> body) {
        String alertType = (String) body.get("alertType");
        int duration = body.get("durationMinutes") instanceof Number
                ? ((Number) body.get("durationMinutes")).intValue() : 60;
        if (alertType == null || alertType.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "alertType 不能为空"));
        }
        log.info("静默告警: type={}, duration={}min", alertType, duration);
        // 静默告警存储在 InvestigationContext 中，由 Agent 处理
        // 这里返回成功，前端通过 SilencedAlerts 组件管理
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "告警已静默 " + duration + " 分钟",
                "alertType", alertType,
                "durationMinutes", duration
        ));
    }

    /** 死锁检测 */
    @GetMapping("/deadlocks")
    public ResponseEntity<Map<String, Object>> deadlocks() {
        try {
            String result = databaseService.detectDeadlocks();
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(result, Map.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/kill/{connectionId}")
    public ResponseEntity<Map<String, Object>> killQuery(@PathVariable Long connectionId) {
        log.info("KILL 请求: connectionId={}", connectionId);
        return executeResponse(databaseService.killQuery(connectionId));
    }

    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> executeSQL(@RequestBody Map<String, String> body) {
        String sql = body.get("sql");
        if (sql == null || sql.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "SQL 不能为空"));
        }
        log.info("执行 SQL: {}", sql);
        return executeResponse(databaseService.executeSafeSQL(sql));
    }

    private ResponseEntity<Map<String, Object>> executeResponse(String json) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            if ((Boolean) data.getOrDefault("success", false)) {
                return ResponseEntity.ok(data);
            }
            return ResponseEntity.badRequest().body(data);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false, "message", e.getMessage()));
        }
    }
}
