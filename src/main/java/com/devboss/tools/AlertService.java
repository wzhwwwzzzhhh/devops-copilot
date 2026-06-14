package com.devboss.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 告警服务：采集聚合各监控指标的状态并生成告警 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);
    private final ObjectMapper objectMapper;

    public AlertService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String getAlerts() {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("configured", true);

            ArrayNode alerts = root.putArray("alerts");
            alerts.add(alert(1, "mysql", "connection_pool_high", "critical", "MySQL 连接池使用率 87%", "active"));
            alerts.add(alert(2, "redis", "memory_high", "warning", "Redis 内存使用率 78%", "active"));
            alerts.add(alert(3, "rabbitmq", "queue_backlog", "critical", "notification.queue 积压 305 条", "active"));
            alerts.add(alert(4, "system", "disk_high", "warning", "/data 磁盘使用率 82%", "active"));
            alerts.add(alert(5, "es", "cluster_yellow", "warning", "ES 集群状态 yellow, 3 个未分配分片", "active"));
            alerts.add(alert(6, "docker", "container_down", "critical", "容器 old-app 已退出", "active"));
            alerts.add(alert(7, "k8s", "pod_pending", "warning", "Pod user-service-8f3b2c1-ghi2 Pending (ImagePullBackOff)", "active"));
            alerts.add(alert(8, "mysql", "deadlock", "warning", "检测到死锁", "silenced"));
            alerts.add(alert(9, "ssl", "cert_expiring", "warning", "admin.example.com 证书 12 天后到期", "active"));
            alerts.add(alert(10, "nginx", "upstream_down", "info", "Nginx upstream api-server 不可用", "active"));
            alerts.add(alert(11, "system", "load_high", "info", "系统 15 分钟负载 4.5", "active"));
            alerts.add(alert(12, "k8s", "namespace_quota", "info", "Namespace production 资源配额使用率 85%", "silenced"));

            int total = alerts.size();
            long critical = countBySeverity(alerts, "critical");
            long warning = countBySeverity(alerts, "warning");
            long info = countBySeverity(alerts, "info");
            long silenced = 0;
            for (int i = 0; i < alerts.size(); i++) {
                if ("silenced".equals(alerts.get(i).path("status").asText())) silenced++;
            }

            ObjectNode summary = root.putObject("summary");
            summary.put("total", total);
            summary.put("critical", critical);
            summary.put("warning", warning);
            summary.put("info", info);
            summary.put("silenced", silenced);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            log.warn("获取告警数据失败", e);
            return "{\"error\": \"获取告警数据失败\"}";
        }
    }

    private ObjectNode alert(int id, String source, String type, String severity,
                             String message, String status) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("source", source);
        node.put("type", type);
        node.put("severity", severity);
        node.put("message", message);
        node.put("status", status);
        return node;
    }

    private long countBySeverity(ArrayNode alerts, String severity) {
        long count = 0;
        for (int i = 0; i < alerts.size(); i++) {
            if (severity.equals(alerts.get(i).path("severity").asText())) count++;
        }
        return count;
    }
}
