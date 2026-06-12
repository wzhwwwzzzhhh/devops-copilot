package com.devboss.controller;

import com.devboss.tools.SslService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ssl")
public class SslController {

    private static final Logger log = LoggerFactory.getLogger(SslController.class);

    private final SslService sslService;

    public SslController(SslService sslService) {
        this.sslService = sslService;
    }

    /**
     * SSL 全量状态
     * GET /api/ssl/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        try {
            String json = sslService.getFullStatus();
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("查询 SSL 状态失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "configured", false,
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 检查指定域名的 SSL 证书
     * GET /api/ssl/check/{domain}
     */
    @GetMapping("/check/{domain}")
    public ResponseEntity<Map<String, Object>> checkDomain(@PathVariable String domain) {
        try {
            String json = sslService.checkCert(domain);
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("检查 SSL 证书失败, domain: {}", domain, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
