package com.devboss.controller;

import com.devboss.tools.DockerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Docker 监控接口：容器运行状态查询与管理
 */
@RestController
@RequestMapping("/api/docker")
public class DockerController {

    private static final Logger log = LoggerFactory.getLogger(DockerController.class);

    private final DockerService dockerService;

    public DockerController(DockerService dockerService) {
        this.dockerService = dockerService;
    }

    /**
     * Docker 全量状态（容器 + 镜像 + 统计 + 摘要）
     * GET /api/docker/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        try {
            String json = dockerService.getFullStatus();
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("查询 Docker 状态失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 获取所有容器（包含已停止）
     * GET /api/docker/containers
     */
    @GetMapping("/containers")
    public ResponseEntity<Map<String, Object>> containers() {
        try {
            String json = dockerService.getContainers();
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("查询容器列表失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 获取镜像列表
     * GET /api/docker/images
     */
    @GetMapping("/images")
    public ResponseEntity<Map<String, Object>> images() {
        try {
            String json = dockerService.getImages();
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("查询镜像列表失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 获取容器资源使用统计
     * GET /api/docker/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        try {
            String json = dockerService.getContainerStats();
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("查询容器资源统计失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }
}
