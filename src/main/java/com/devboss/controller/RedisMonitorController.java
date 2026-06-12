package com.devboss.controller;

import com.devboss.tools.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/redis")
public class RedisMonitorController {

    private static final Logger log = LoggerFactory.getLogger(RedisMonitorController.class);

    private final RedisService redisService;

    public RedisMonitorController(RedisService redisService) {
        this.redisService = redisService;
    }

    /**
     * Redis 全量状态
     * GET /api/redis/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        try {
            String json = redisService.getFullStatus();
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("查询 Redis 状态失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "configured", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Redis INFO 分段详情
     * GET /api/redis/info
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        try {
            String json = redisService.getRedisInfo();
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("查询 Redis INFO 失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Redis 慢查询日志
     * GET /api/redis/slowlog
     */
    @GetMapping("/slowlog")
    public ResponseEntity<Map<String, Object>> slowLog() {
        try {
            String json = redisService.getSlowLog();
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("查询 Redis 慢查询日志失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Redis Key 空间汇总
     * GET /api/redis/keys
     */
    @GetMapping("/keys")
    public ResponseEntity<Map<String, Object>> keys() {
        try {
            String json = redisService.getKeySummary();
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("查询 Redis Key 汇总失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
