package com.devboss.controller;

import com.devboss.tools.NginxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Nginx 监控接口：反向代理状态与连接数查询
 */
@RestController
@RequestMapping("/api/nginx")
public class NginxController {

    private static final Logger log = LoggerFactory.getLogger(NginxController.class);

    private final NginxService nginxService;

    public NginxController(NginxService nginxService) {
        this.nginxService = nginxService;
    }

    /**
     * Nginx 全量状态
     * GET /api/nginx/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        try {
            String json = nginxService.getFullStatus();
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("查询 Nginx 状态失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "configured", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * Nginx 访问日志分析
     * GET /api/nginx/analysis
     */
    @GetMapping("/analysis")
    public ResponseEntity<Map<String, Object>> analysis() {
        try {
            String json = nginxService.getAccessAnalysis();
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("查询 Nginx 访问分析失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
