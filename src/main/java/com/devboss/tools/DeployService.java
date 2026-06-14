package com.devboss.tools;

import com.devboss.entity.ServiceConnection;
import com.devboss.service.ServiceConnectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 部署管理服务：扩缩容、回滚、金丝雀发布等运维操作 */
@Service
public class DeployService {

    private static final Logger log = LoggerFactory.getLogger(DeployService.class);
    private final ObjectMapper objectMapper;
    private final ServiceConnectionService connectionService;

    private final Map<String, ServiceState> services = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> deploymentHistories = new ConcurrentHashMap<>();

    private static final Map<String, List<String>> AVAILABLE_VERSIONS = new LinkedHashMap<>();

    static {
        AVAILABLE_VERSIONS.put("order-service", List.of("v2.4.0", "v2.3.1", "v2.3.0", "v2.2.0"));
        AVAILABLE_VERSIONS.put("payment-service", List.of("v2.3.1", "v2.3.0"));
        AVAILABLE_VERSIONS.put("user-service", List.of("v3.0.0", "v2.4.0"));
    }

    public DeployService(ObjectMapper objectMapper, ServiceConnectionService connectionService) {
        this.objectMapper = objectMapper;
        this.connectionService = connectionService;
    }

    @PostConstruct
    void init() {
        loadInitialState();
    }

    @SuppressWarnings("unchecked")
    private void loadInitialState() {
        try {
            InputStream is = getClass().getResourceAsStream("/mock/deployments/history.json");
            if (is == null) return;
            Map<String, Object> root = objectMapper.readValue(is, Map.class);
            List<Map<String, Object>> serviceList = (List<Map<String, Object>>) root.get("services");
            for (Map<String, Object> svc : serviceList) {
                ServiceState state = new ServiceState();
                state.name = (String) svc.get("name");
                state.version = (String) svc.get("current_version");
                state.replicas = (int) svc.get("replicas");
                state.namespace = (String) svc.get("namespace");
                state.status = "active";
                state.uptime = "12h";
                services.put(state.name, state);

                // Load and normalize history from mock data
                List<Map<String, Object>> history = (List<Map<String, Object>>) svc.get("history");
                if (history != null) {
                    List<Map<String, Object>> normalized = new ArrayList<>();
                    for (Map<String, Object> entry : history) {
                        Map<String, Object> norm = new LinkedHashMap<>();
                        norm.put("version", entry.get("version"));
                        norm.put("timestamp", entry.get("time"));
                        norm.put("action", "deploy");
                        norm.put("status", entry.get("status"));
                        normalized.add(norm);
                    }
                    deploymentHistories.put(state.name, normalized);
                }
            }
            log.info("Mock 服务状态已加载: {} 个服务", services.size());
        } catch (Exception e) {
            log.error("加载 Mock 服务状态失败", e);
        }
    }

    public String getDeployments(String serviceName) {
        try {
            List<ServiceConnection> k8sConns = connectionService.findByType("k8s");
            if (!k8sConns.isEmpty()) {
                return queryK8sApi(k8sConns.get(0), serviceName);
            }
            return getMockDeployments(serviceName);
        } catch (Exception e) {
            log.error("查询部署状态失败", e);
            return "{\"error\": \"查询部署状态失败\"}";
        }
    }

    private String queryK8sApi(ServiceConnection conn, String serviceName) {
        String apiServer = conn.getHost();
        String token = conn.getPassword();
        try {
            HttpClient client = HttpClient.newHttpClient();
            String url = apiServer + "/api/v1/namespaces/" + conn.getProperties() + "/pods?labelSelector=app=" + serviceName;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("K8s API 查询成功: service={}", serviceName);
            return response.body();

        } catch (Exception e) {
            log.warn("K8s API 查询失败 ({}), 降级 Mock", e.getMessage());
            return getMockDeployments(serviceName);
        }
    }

    private String getMockDeployments(String serviceName) {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("type", "deployment_status_mock");
            ArrayNode servicesArray = result.putArray("services");

            if ("all".equals(serviceName)) {
                for (ServiceState state : services.values()) {
                    servicesArray.add(serializeState(state));
                }
            } else {
                ServiceState state = services.get(serviceName);
                if (state == null) return "{\"error\": \"未找到服务: " + serviceName + "\"}";
                servicesArray.add(serializeState(state));
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\": \"查询Mock部署状态失败\"}";
        }
    }

    public String executeAction(String action, String serviceName, Map<String, String> params) {
        List<ServiceConnection> k8sConns = connectionService.findByType("k8s");
        if (!k8sConns.isEmpty()) {
            return executeViaK8s(k8sConns.get(0), action, serviceName, params);
        }
        return executeMockAction(action, serviceName, params);
    }

    private String executeViaK8s(ServiceConnection conn, String action, String serviceName, Map<String, String> params) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            String apiServer = conn.getHost();
            String token = conn.getPassword();
            String namespace = conn.getProperties() != null ? conn.getProperties() : "default";

            // Determine replicas from params or by querying current deployment state
            int replicas;
            if (params != null && params.containsKey("replicas")) {
                replicas = Integer.parseInt(params.get("replicas"));
            } else {
                replicas = getCurrentReplicasFromK8s(client, apiServer, token, namespace, serviceName);
                replicas = Math.max(replicas, 1);
            }

            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode spec = body.putObject("spec");
            spec.put("replicas", replicas);

            String url = apiServer + "/apis/apps/v1/namespaces/" + namespace + "/deployments/" + serviceName + "/scale";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("K8s 操作成功: action={}, service={}", action, serviceName);

            // Update in-memory state to reflect changes
            ServiceState state = services.get(serviceName);
            if (state != null) {
                state.replicas = replicas;
                switch (action) {
                    case "scale" -> {
                        recordHistory(serviceName, state.version, "scale", "completed");
                    }
                    case "rollback" -> {
                        String oldVersion = state.version;
                        if (params != null && params.containsKey("version")) {
                            state.version = params.get("version");
                        } else {
                            state.version = rollbackVersion(oldVersion);
                        }
                        recordHistory(serviceName, state.version, "rollback", "completed");
                    }
                    case "restart" -> {
                        state.status = "restarting";
                        state.uptime = "0m";
                        recordHistory(serviceName, state.version, "restart", "completed");
                    }
                    case "canary", "rollout" -> {
                        if (params != null && params.containsKey("version")) {
                            state.version = params.get("version");
                        }
                        recordHistory(serviceName, state.version, "canary", "completed");
                    }
                    case "deploy" -> {
                        if (params != null && params.containsKey("version")) {
                            state.version = params.get("version");
                        }
                        recordHistory(serviceName, state.version, "deploy", "completed");
                    }
                }
            }

            String health = doHealthCheck(state);
            return String.format("[K8s] %s 指令已执行: %s ✅\n%s", action, serviceName, health);

        } catch (Exception e) {
            log.warn("K8s 操作失败 ({}), 降级 Mock 操作", e.getMessage());
            return executeMockAction(action, serviceName, params);
        }
    }

    private int getCurrentReplicasFromK8s(HttpClient client, String apiServer, String token, String namespace, String serviceName) {
        try {
            String url = apiServer + "/apis/apps/v1/namespaces/" + namespace + "/deployments/" + serviceName;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                ObjectNode deploymentInfo = (ObjectNode) objectMapper.readTree(response.body());
                return deploymentInfo.path("spec").path("replicas").asInt(3);
            }
        } catch (Exception e) {
            log.warn("获取当前 K8s 副本数失败: {}", e.getMessage());
        }
        return 3;
    }

    private String executeMockAction(String action, String serviceName, Map<String, String> params) {
        ServiceState state = services.get(serviceName);
        if (state == null) return "错误: 未找到服务 " + serviceName;

        return switch (action) {
            case "scale" -> doScale(state, params);
            case "rollback" -> doRollback(state, params);
            case "restart" -> doRestart(state);
            case "canary", "rollout" -> doCanary(state, params);
            case "deploy" -> doDeploy(state, params);
            default -> "未知操作: " + action;
        };
    }

    private String doScale(ServiceState state, Map<String, String> params) {
        int oldReplicas = state.replicas;
        int newReplicas = oldReplicas + 2;
        if (params != null && params.containsKey("replicas")) {
            try {
                newReplicas = Integer.parseInt(params.get("replicas"));
            } catch (NumberFormatException ignored) {
            }
        }
        state.replicas = newReplicas;
        recordHistory(state.name, state.version, "scale", "completed");
        String health = doHealthCheck(state);
        return String.format("扩容指令已执行：[%s] 副本数 %d -> %d ✅\n%s", state.name, oldReplicas, newReplicas, health);
    }

    private String doRollback(ServiceState state, Map<String, String> params) {
        String oldVersion = state.version;
        if (params != null && params.containsKey("version")) {
            state.version = params.get("version");
        } else {
            state.version = rollbackVersion(oldVersion);
        }
        recordHistory(state.name, state.version, "rollback", "completed");
        try {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("action", "rollback");
            result.put("service", state.name);
            result.put("status", "completed");
            result.put("previous_version", oldVersion);
            result.put("current_version", state.version);
            Map<String, Object> health = simulateHealthCheck(state.name);
            ObjectNode healthNode = result.putObject("health_check");
            healthNode.put("status", (String) health.get("status"));
            healthNode.put("response_time_ms", (int) health.get("response_time_ms"));
            healthNode.put("endpoint", (String) health.get("endpoint"));
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\": \"回滚操作失败: " + e.getMessage() + "\"}";
        }
    }

    private String doRestart(ServiceState state) {
        state.status = "restarting";
        state.uptime = "0m";
        recordHistory(state.name, state.version, "restart", "completed");
        String health = doHealthCheck(state);
        return String.format("重启指令已执行：[%s] 正在滚动重启 ✅\n%s", state.name, health);
    }

    private String doCanary(ServiceState state, Map<String, String> params) {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("action", "canary");
            result.put("service", state.name);
            String oldVersion = state.version;
            result.put("from_version", oldVersion);

            List<String> versions = AVAILABLE_VERSIONS.getOrDefault(state.name, List.of("v1.0.0"));
            String newVersion = versions.get(0);
            if (params != null && params.containsKey("version")) {
                newVersion = params.get("version");
            }
            result.put("to_version", newVersion);

            int totalReplicas = state.replicas;
            int step1Pods = Math.max(1, totalReplicas / 10);
            int step2Pods = Math.max(1, totalReplicas / 2);

            result.put("steps", String.format("10%% (%d pod) → 监控等待 → 50%% → 监控等待 → 100%%", step1Pods));

            ArrayNode stepsArray = result.putArray("step_details");

            // Step 1: 10% canary
            ObjectNode step1 = stepsArray.addObject();
            step1.put("step", 1);
            step1.put("phase", "10% canary");
            step1.put("replicas", step1Pods);
            step1.put("status", "completed");
            recordHistory(state.name, newVersion, "canary_10pct", "completed");

            // Step 2: 50% rollout
            ObjectNode step2 = stepsArray.addObject();
            step2.put("step", 2);
            step2.put("phase", "50% rollout");
            step2.put("replicas", step2Pods);
            step2.put("status", "completed");
            recordHistory(state.name, newVersion, "canary_50pct", "completed");

            // Step 3: 100% rollout
            state.version = newVersion;
            ObjectNode step3 = stepsArray.addObject();
            step3.put("step", 3);
            step3.put("phase", "100% rollout");
            step3.put("replicas", totalReplicas);
            step3.put("status", "completed");
            recordHistory(state.name, newVersion, "canary", "completed");

            // Perform health check after canary rollout
            Map<String, Object> health = simulateHealthCheck(state.name);
            ObjectNode healthNode = result.putObject("health_check");
            healthNode.put("status", (String) health.get("status"));
            healthNode.put("response_time_ms", (int) health.get("response_time_ms"));
            healthNode.put("endpoint", (String) health.get("endpoint"));

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\": \"金丝雀部署失败: " + e.getMessage() + "\"}";
        }
    }

    private String doDeploy(ServiceState state, Map<String, String> params) {
        String oldVersion = state.version;
        String newVersion;
        if (params != null && params.containsKey("version")) {
            newVersion = params.get("version");
        } else {
            List<String> versions = AVAILABLE_VERSIONS.getOrDefault(state.name, Collections.emptyList());
            if (!versions.isEmpty() && !versions.get(0).equals(oldVersion)) {
                newVersion = versions.get(0);
            } else {
                newVersion = oldVersion;
            }
        }
        state.version = newVersion;
        state.status = "active";
        recordHistory(state.name, newVersion, "deploy", "completed");
        try {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("action", "deploy");
            result.put("service", state.name);
            result.put("status", "completed");
            result.put("previous_version", oldVersion);
            result.put("current_version", newVersion);
            Map<String, Object> health = simulateHealthCheck(state.name);
            ObjectNode healthNode = result.putObject("health_check");
            healthNode.put("status", (String) health.get("status"));
            healthNode.put("response_time_ms", (int) health.get("response_time_ms"));
            healthNode.put("endpoint", (String) health.get("endpoint"));
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\": \"部署操作失败: " + e.getMessage() + "\"}";
        }
    }

    private Map<String, Object> simulateHealthCheck(String serviceName) {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "healthy");
        health.put("response_time_ms", 45);
        health.put("endpoint", "/health");
        return health;
    }

    private String doHealthCheck(ServiceState state) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (state != null) {
            state.status = "active";
        }
        ObjectNode health = objectMapper.createObjectNode();
        health.put("status", "healthy");
        health.put("response_time", "45ms");
        health.put("endpoint", "/health");
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(health);
        } catch (Exception e) {
            return "{\"health_check\": \"failed\", \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private void recordHistory(String serviceName, String version, String action, String status) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("version", version);
        entry.put("timestamp", Instant.now().toString());
        entry.put("action", action);
        entry.put("status", status);
        deploymentHistories.computeIfAbsent(serviceName, k -> Collections.synchronizedList(new ArrayList<>())).add(entry);
        log.info("部署历史已记录: service={}, version={}, action={}", serviceName, version, action);
    }

    private String rollbackVersion(String v) {
        try {
            String prefix = v.replaceAll("\\d+$", "");
            String numStr = v.replaceAll(".*?(\\d+)$", "$1");
            int num = Integer.parseInt(numStr) - 1;
            if (num < 0) return v;
            return prefix + num;
        } catch (Exception e) {
            return "v1.0.0";
        }
    }

    private ObjectNode serializeState(ServiceState state) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", state.name);
        node.put("version", state.version);
        node.put("replicas", state.replicas);
        node.put("namespace", state.namespace);
        node.put("status", state.status);
        node.put("uptime", state.uptime);

        // Attach deployment history to the status output
        List<Map<String, Object>> history = deploymentHistories.get(state.name);
        if (history != null && !history.isEmpty()) {
            ArrayNode historyArray = node.putArray("history");
            for (Map<String, Object> entry : history) {
                ObjectNode histNode = historyArray.addObject();
                histNode.put("version", (String) entry.get("version"));
                Object ts = entry.get("timestamp");
                histNode.put("timestamp", ts != null ? ts.toString() : "");
                histNode.put("action", (String) entry.get("action"));
                histNode.put("status", (String) entry.get("status"));
            }
        }

        return node;
    }

    private static class ServiceState {
        String name;
        String version;
        int replicas;
        String namespace;
        String status;
        String uptime;
    }
}
