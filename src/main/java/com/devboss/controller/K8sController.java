package com.devboss.controller;

import com.devboss.tools.K8sService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Kubernetes 监控接口：集群资源与 Pod 状态查询
 */
@RestController
@RequestMapping("/api/k8s")
public class K8sController {

    private static final Logger log = LoggerFactory.getLogger(K8sController.class);

    private final K8sService k8sService;

    public K8sController(K8sService k8sService) {
        this.k8sService = k8sService;
    }

    /**
     * K8s 全量集群状态
     * GET /api/k8s/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        try {
            String json = k8sService.getFullStatus();
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("查询 K8s 集群状态失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "configured", false,
                    "error", e.getMessage()
            ));
        }
    }
}
