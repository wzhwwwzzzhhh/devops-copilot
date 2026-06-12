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

import java.util.Map;

@RestController
@RequestMapping("/api/database")
public class DatabaseMonitorController {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMonitorController.class);

    private final DatabaseService databaseService;
    private final ServiceConnectionService connectionService;

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
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("查询数据库状态失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "configured", false,
                    "error", e.getMessage()
            ));
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
