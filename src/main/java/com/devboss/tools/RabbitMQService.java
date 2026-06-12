package com.devboss.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RabbitMQService {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQService.class);
    private final ObjectMapper objectMapper;

    public RabbitMQService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 获取 RabbitMQ 集群概览（Mock）
     */
    public String getOverview() {
        try {
            ObjectNode result = objectMapper.createObjectNode();

            ObjectNode queueTotals = result.putObject("queue_totals");
            queueTotals.put("messages", 12530);
            queueTotals.put("messages_ready", 320);
            queueTotals.put("messages_unacknowledged", 45);

            ObjectNode messageStats = result.putObject("message_stats");
            ObjectNode publishDetails = messageStats.putObject("publish_details");
            publishDetails.put("rate", 120.0);
            ObjectNode deliverDetails = messageStats.putObject("deliver_details");
            deliverDetails.put("rate", 115.0);

            ObjectNode objectTotals = result.putObject("object_totals");
            objectTotals.put("connections", 8);
            objectTotals.put("channels", 24);
            objectTotals.put("consumers", 10);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.error("模拟 RabbitMQ 概览失败", e);
            return "{\"error\": \"模拟 RabbitMQ 概览失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取 RabbitMQ 队列列表（Mock）
     */
    public String getQueues() {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode queues = result.putArray("queues");

            // order.queue - 中等积压
            ObjectNode q1 = queues.addObject();
            q1.put("name", "order.queue");
            q1.put("ready", 150);
            q1.put("unacked", 10);
            q1.put("total", 160);
            q1.put("consumers", 3);
            q1.put("memory", 45.2);

            // payment.queue - 低积压，正常
            ObjectNode q2 = queues.addObject();
            q2.put("name", "payment.queue");
            q2.put("ready", 12);
            q2.put("unacked", 0);
            q2.put("total", 12);
            q2.put("consumers", 5);
            q2.put("memory", 12.8);

            // notification.queue - 高积压，消费者不足
            ObjectNode q3 = queues.addObject();
            q3.put("name", "notification.queue");
            q3.put("ready", 305);
            q3.put("unacked", 35);
            q3.put("total", 340);
            q3.put("consumers", 2);
            q3.put("memory", 78.5);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.error("模拟 RabbitMQ 队列列表失败", e);
            return "{\"error\": \"模拟 RabbitMQ 队列列表失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取 RabbitMQ 节点列表（Mock）
     */
    public String getNodes() {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            ArrayNode nodes = result.putArray("nodes");

            // node1 - 主节点
            ObjectNode n1 = nodes.addObject();
            n1.put("name", "rabbit@node1");
            n1.put("memory_used", 256);
            n1.put("memory_limit", 1024);
            n1.put("disk_free", 10240);
            n1.put("disk_limit", 50000);
            n1.put("fd_used", 64);
            n1.put("fd_total", 1024);
            n1.put("run_queue", 0);

            // node2 - 从节点
            ObjectNode n2 = nodes.addObject();
            n2.put("name", "rabbit@node2");
            n2.put("memory_used", 198);
            n2.put("memory_limit", 1024);
            n2.put("disk_free", 8192);
            n2.put("disk_limit", 50000);
            n2.put("fd_used", 48);
            n2.put("fd_total", 1024);
            n2.put("run_queue", 1);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.error("模拟 RabbitMQ 节点列表失败", e);
            return "{\"error\": \"模拟 RabbitMQ 节点列表失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取 RabbitMQ 全量状态（概览 + 队列 + 节点）
     * 组合 getOverview / getQueues / getNodes 的结果
     */
    public String getFullStatus() {
        try {
            ObjectNode result = objectMapper.createObjectNode();

            // 从各个方法组合数据
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> overview = objectMapper.readValue(getOverview(), java.util.Map.class);
            for (java.util.Map.Entry<String, Object> entry : overview.entrySet()) {
                result.set(entry.getKey(), objectMapper.valueToTree(entry.getValue()));
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> queues = objectMapper.readValue(getQueues(), java.util.Map.class);
            result.set("queues", objectMapper.valueToTree(queues.get("queues")));

            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> nodes = objectMapper.readValue(getNodes(), java.util.Map.class);
            result.set("nodes", objectMapper.valueToTree(nodes.get("nodes")));

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.error("模拟 RabbitMQ 全量状态失败", e);
            return "{\"error\": \"模拟 RabbitMQ 全量状态失败: " + e.getMessage() + "\"}";
        }
    }
}
