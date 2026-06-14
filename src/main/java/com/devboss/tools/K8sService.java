package com.devboss.tools;

import com.devboss.entity.ServiceConnection;
import com.devboss.service.ServiceConnectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** Kubernetes 集群状态监控：节点、Pod、部署、事件等 */
@Service
public class K8sService {

    private static final Logger log = LoggerFactory.getLogger(K8sService.class);

    private final ObjectMapper objectMapper;
    private final ServiceConnectionService serviceConnectionService;

    public K8sService(ObjectMapper objectMapper, ServiceConnectionService serviceConnectionService) {
        this.objectMapper = objectMapper;
        this.serviceConnectionService = serviceConnectionService;
    }

    /**
     * 查找已激活的 K8s 连接
     */
    private ServiceConnection getK8sConnection() {
        try {
            List<ServiceConnection> connections = serviceConnectionService.findByType("k8s");
            return connections.stream()
                    .filter(conn -> "ACTIVE".equals(conn.getStatus()))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("查找 K8s 连接失败", e);
            return null;
        }
    }

    /**
     * 获取 K8s 全量集群状态
     * 返回包含 cluster / nodes / pods / events / deployments 的 JSON
     */
    public String getFullStatus() {
        try {
            ServiceConnection conn = getK8sConnection();
            if (conn == null) {
                log.warn("未配置 K8s 连接，使用 Mock 数据");
                return readMockFullStatus();
            }
            // TODO: 通过 conn 连接真实 K8s API 获取数据
            log.info("使用 K8s 连接: {} ({}:{})", conn.getName(), conn.getHost(), conn.getPort());
            return readMockFullStatus();
        } catch (Exception e) {
            log.warn("获取 K8s 集群状态失败", e);
            return "{\"error\": \"获取 K8s 集群状态失败: " + e.getMessage() + "\"}";
        }
    }

    // ========== Mock Data Methods ==========

    private String readMockFullStatus() {
        try {
            ObjectNode root = objectMapper.createObjectNode();

            // Cluster overview
            ObjectNode cluster = root.putObject("cluster");
            cluster.put("node_count", 3);
            cluster.put("total_pods", 18);
            cluster.put("running_pods", 15);
            cluster.put("pending_pods", 2);
            cluster.put("failed_pods", 1);
            cluster.put("namespace", "production");

            // Nodes
            ArrayNode nodes = root.putArray("nodes");

            ObjectNode node1 = nodes.addObject();
            node1.put("name", "node-01");
            node1.put("role", "master");
            node1.put("status", "Ready");
            node1.put("cpu_percent", 65);
            node1.put("mem_percent", 58);
            node1.put("kubelet_version", "v1.28.5");

            ObjectNode node2 = nodes.addObject();
            node2.put("name", "node-02");
            node2.put("role", "worker");
            node2.put("status", "Ready");
            node2.put("cpu_percent", 42);
            node2.put("mem_percent", 38);
            node2.put("kubelet_version", "v1.28.5");

            ObjectNode node3 = nodes.addObject();
            node3.put("name", "node-03");
            node3.put("role", "worker");
            node3.put("status", "Ready");
            node3.put("cpu_percent", 78);
            node3.put("mem_percent", 82);
            node3.put("kubelet_version", "v1.28.5");
            node3.put("warning", "内存使用率超过 80%");

            // Pods (18 pods total: 15 Running, 2 Pending, 1 Failed)
            ArrayNode pods = root.putArray("pods");

            // order-service (3 pods)
            ObjectNode p1 = pods.addObject();
            p1.put("name", "order-service-7d4f8c9-abc1");
            p1.put("namespace", "production");
            p1.put("status", "Running");
            p1.put("image", "order-service:v2.3.1");
            p1.put("node", "node-02");
            p1.put("restarts", 0);
            p1.put("age", "2d");

            ObjectNode p2 = pods.addObject();
            p2.put("name", "order-service-7d4f8c9-abc2");
            p2.put("namespace", "production");
            p2.put("status", "Running");
            p2.put("image", "order-service:v2.3.1");
            p2.put("node", "node-02");
            p2.put("restarts", 1);
            p2.put("age", "2d");

            ObjectNode p3 = pods.addObject();
            p3.put("name", "order-service-7d4f8c9-abc3");
            p3.put("namespace", "production");
            p3.put("status", "Running");
            p3.put("image", "order-service:v2.3.1");
            p3.put("node", "node-02");
            p3.put("restarts", 0);
            p3.put("age", "2d");

            // payment-service (2 pods)
            ObjectNode p4 = pods.addObject();
            p4.put("name", "payment-service-6e2a1b3-def1");
            p4.put("namespace", "production");
            p4.put("status", "Running");
            p4.put("image", "payment-service:v1.8.2");
            p4.put("node", "node-01");
            p4.put("restarts", 0);
            p4.put("age", "5d");

            ObjectNode p5 = pods.addObject();
            p5.put("name", "payment-service-6e2a1b3-def2");
            p5.put("namespace", "production");
            p5.put("status", "Running");
            p5.put("image", "payment-service:v1.8.2");
            p5.put("node", "node-03");
            p5.put("restarts", 0);
            p5.put("age", "5d");

            // user-service (3 pods)
            ObjectNode p6 = pods.addObject();
            p6.put("name", "user-service-8f3b2c1-ghi1");
            p6.put("namespace", "production");
            p6.put("status", "Running");
            p6.put("image", "user-service:v3.1.0");
            p6.put("node", "node-01");
            p6.put("restarts", 2);
            p6.put("age", "12h");

            ObjectNode p7 = pods.addObject();
            p7.put("name", "user-service-8f3b2c1-ghi2");
            p7.put("namespace", "production");
            p7.put("status", "Pending");
            p7.put("reason", "ImagePullBackOff");
            p7.put("image", "user-service:v3.1.0");
            p7.put("node", "node-01");
            p7.put("restarts", 0);
            p7.put("age", "12h");

            ObjectNode p8 = pods.addObject();
            p8.put("name", "user-service-8f3b2c1-ghi3");
            p8.put("namespace", "production");
            p8.put("status", "Running");
            p8.put("image", "user-service:v3.1.0");
            p8.put("node", "node-02");
            p8.put("restarts", 0);
            p8.put("age", "12h");

            // notification-service (1 pod)
            ObjectNode p9 = pods.addObject();
            p9.put("name", "notification-service-1a2b3c4-jkl1");
            p9.put("namespace", "production");
            p9.put("status", "Running");
            p9.put("image", "notification-service:v1.2.0");
            p9.put("node", "node-03");
            p9.put("restarts", 0);
            p9.put("age", "10d");

            // redis (1 pod)
            ObjectNode p10 = pods.addObject();
            p10.put("name", "redis-5d8f9a0-mno1");
            p10.put("namespace", "production");
            p10.put("status", "Running");
            p10.put("image", "redis:7.2.4");
            p10.put("node", "node-01");
            p10.put("restarts", 0);
            p10.put("age", "30d");

            // mysql (1 pod)
            ObjectNode p11 = pods.addObject();
            p11.put("name", "mysql-9e7f6d5-pqr1");
            p11.put("namespace", "production");
            p11.put("status", "Running");
            p11.put("image", "mysql:8.0.35");
            p11.put("node", "node-03");
            p11.put("restarts", 0);
            p11.put("age", "30d");

            // 7 more mock pods for other services (total 18)
            ObjectNode p12 = pods.addObject();
            p12.put("name", "gateway-service-3c4d5e6-fgh1");
            p12.put("namespace", "production");
            p12.put("status", "Running");
            p12.put("image", "gateway-service:v2.0.1");
            p12.put("node", "node-01");
            p12.put("restarts", 0);
            p12.put("age", "7d");

            ObjectNode p13 = pods.addObject();
            p13.put("name", "gateway-service-3c4d5e6-fgh2");
            p13.put("namespace", "production");
            p13.put("status", "Running");
            p13.put("image", "gateway-service:v2.0.1");
            p13.put("node", "node-02");
            p13.put("restarts", 1);
            p13.put("age", "7d");

            ObjectNode p14 = pods.addObject();
            p14.put("name", "auth-service-2b3c4d5-ijk1");
            p14.put("namespace", "production");
            p14.put("status", "Running");
            p14.put("image", "auth-service:v1.5.3");
            p14.put("node", "node-01");
            p14.put("restarts", 0);
            p14.put("age", "15d");

            ObjectNode p15 = pods.addObject();
            p15.put("name", "config-service-4d5e6f7-lmn1");
            p15.put("namespace", "production");
            p15.put("status", "Running");
            p15.put("image", "config-service:v1.1.0");
            p15.put("node", "node-02");
            p15.put("restarts", 0);
            p15.put("age", "30d");

            ObjectNode p16 = pods.addObject();
            p16.put("name", "monitoring-service-5e6f7g8-opq1");
            p16.put("namespace", "production");
            p16.put("status", "Running");
            p16.put("image", "monitoring-service:v1.0.2");
            p16.put("node", "node-03");
            p16.put("restarts", 0);
            p16.put("age", "60d");

            ObjectNode p17 = pods.addObject();
            p17.put("name", "logstash-6f7g8h9-rst1");
            p17.put("namespace", "production");
            p17.put("status", "Pending");
            p17.put("reason", "Pending");
            p17.put("image", "logstash:8.11.0");
            p17.put("node", "node-03");
            p17.put("restarts", 0);
            p17.put("age", "1h");

            ObjectNode p18 = pods.addObject();
            p18.put("name", "prometheus-7g8h9i0-uvw1");
            p18.put("namespace", "monitoring");
            p18.put("status", "Failed");
            p18.put("reason", "CrashLoopBackOff");
            p18.put("image", "prometheus:v2.48.0");
            p18.put("node", "node-02");
            p18.put("restarts", 5);
            p18.put("age", "1d");

            // Warning Events
            ArrayNode events = root.putArray("events");

            ObjectNode e1 = events.addObject();
            e1.put("type", "Warning");
            e1.put("reason", "ImagePullBackOff");
            e1.put("source", "kubelet, node-01");
            e1.put("message", "Back-off pulling image \"user-service:v3.1.0\"");
            e1.put("count", 12);
            e1.put("last_seen", "2m ago");

            ObjectNode e2 = events.addObject();
            e2.put("type", "Warning");
            e2.put("reason", "OOMKill");
            e2.put("source", "kubelet, node-03");
            e2.put("message", "Pod order-service-7d4f8c9-abc3 was OOM killed");
            e2.put("count", 3);
            e2.put("last_seen", "15m ago");

            ObjectNode e3 = events.addObject();
            e3.put("type", "Warning");
            e3.put("reason", "NodeNotReady");
            e3.put("source", "node-controller");
            e3.put("message", "Node node-03 was not ready for 2m");
            e3.put("count", 1);
            e3.put("last_seen", "1h ago");

            ObjectNode e4 = events.addObject();
            e4.put("type", "Warning");
            e4.put("reason", "BackOff");
            e4.put("source", "kubelet, node-02");
            e4.put("message", "Back-off restarting failed container prometheus in pod prometheus-7g8h9i0-uvw1");
            e4.put("count", 8);
            e4.put("last_seen", "30s ago");

            // Deployments
            ArrayNode deployments = root.putArray("deployments");

            ObjectNode d1 = deployments.addObject();
            d1.put("name", "order-service");
            d1.put("desired", 3);
            d1.put("current", 3);
            d1.put("up_to_date", 3);
            d1.put("available", 3);
            d1.put("age", "30d");

            ObjectNode d2 = deployments.addObject();
            d2.put("name", "payment-service");
            d2.put("desired", 2);
            d2.put("current", 2);
            d2.put("up_to_date", 2);
            d2.put("available", 2);
            d2.put("age", "30d");

            ObjectNode d3 = deployments.addObject();
            d3.put("name", "user-service");
            d3.put("desired", 3);
            d3.put("current", 3);
            d3.put("up_to_date", 3);
            d3.put("available", 2);
            d3.put("age", "30d");

            ObjectNode d4 = deployments.addObject();
            d4.put("name", "notification-service");
            d4.put("desired", 1);
            d4.put("current", 1);
            d4.put("up_to_date", 1);
            d4.put("available", 1);
            d4.put("age", "30d");

            ObjectNode d5 = deployments.addObject();
            d5.put("name", "redis");
            d5.put("desired", 1);
            d5.put("current", 1);
            d5.put("up_to_date", 1);
            d5.put("available", 1);
            d5.put("age", "30d");

            ObjectNode d6 = deployments.addObject();
            d6.put("name", "mysql");
            d6.put("desired", 1);
            d6.put("current", 1);
            d6.put("up_to_date", 1);
            d6.put("available", 1);
            d6.put("age", "30d");

            ObjectNode d7 = deployments.addObject();
            d7.put("name", "gateway-service");
            d7.put("desired", 2);
            d7.put("current", 2);
            d7.put("up_to_date", 2);
            d7.put("available", 2);
            d7.put("age", "30d");

            ObjectNode d8 = deployments.addObject();
            d8.put("name", "auth-service");
            d8.put("desired", 1);
            d8.put("current", 1);
            d8.put("up_to_date", 1);
            d8.put("available", 1);
            d8.put("age", "30d");

            ObjectNode d9 = deployments.addObject();
            d9.put("name", "config-service");
            d9.put("desired", 1);
            d9.put("current", 1);
            d9.put("up_to_date", 1);
            d9.put("available", 1);
            d9.put("age", "30d");

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return "{\"error\": \"生成 Mock K8s 集群状态失败: " + e.getMessage() + "\"}";
        }
    }
}
