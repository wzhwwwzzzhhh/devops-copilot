package com.devboss.controller;

import com.devboss.tools.RabbitMQService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * RabbitMQ 监控接口：消息队列状态监控与管理
 */
@RestController
@RequestMapping("/api/rabbitmq")
public class RabbitMQController {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQController.class);

    private final RabbitMQService rabbitMQService;

    public RabbitMQController(RabbitMQService rabbitMQService) {
        this.rabbitMQService = rabbitMQService;
    }

    /**
     * 获取 RabbitMQ 全量状态（概览 + 队列 + 节点）
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        try {
            String json = rabbitMQService.getFullStatus();
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("查询 RabbitMQ 状态失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }

    /**
     * 获取 RabbitMQ 队列列表
     */
    @GetMapping("/queues")
    public ResponseEntity<Map<String, Object>> queues() {
        try {
            String json = rabbitMQService.getQueues();
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(json, Map.class);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("查询 RabbitMQ 队列失败", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", e.getMessage()
            ));
        }
    }
}
