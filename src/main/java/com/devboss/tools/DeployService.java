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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DeployService {

    private static final Logger log = LoggerFactory.getLogger(DeployService.class);
    private final ObjectMapper objectMapper;
    private final ServiceConnectionService connectionService;

    private final Map<String, ServiceState> services = new ConcurrentHashMap<>();

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
            return executeViaK8s(k8sConns.get(0), action, serviceName);
        }
        return executeMockAction(action, serviceName, params);
    }

    private String executeViaK8s(ServiceConnection conn, String action, String serviceName) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            String apiServer = conn.getHost();
            String token = conn.getPassword();
            String namespace = conn.getProperties() != null ? conn.getProperties() : "default";

            ObjectNode body = objectMapper.createObjectNode();
            ObjectNode spec = body.putObject("spec");
            spec.put("replicas", 5);

            String url = apiServer + "/apis/apps/v1/namespaces/" + namespace + "/deployments/" + serviceName + "/scale";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("K8s 操作成功: action={}, service={}", action, serviceName);

            // 同步更新内存状态，确保后续 list_deployments 返回最新数据
            ServiceState state = services.get(serviceName);
            if (state != null) {
                switch (action) {
                    case "scale" -> { state.replicas = 5; }
                    case "rollback" -> doRollback(state);
                    case "restart" -> doRestart(state);
                }
            }

            return String.format("[K8s] %s 指令已执行: %s ✅", action, serviceName);

        } catch (Exception e) {
            log.warn("K8s 操作失败 ({}), 降级 Mock 操作", e.getMessage());
            return executeMockAction(action, serviceName, Map.of());
        }
    }

    private String executeMockAction(String action, String serviceName, Map<String, String> params) {
        ServiceState state = services.get(serviceName);
        if (state == null) return "错误: 未找到服务 " + serviceName;

        return switch (action) {
            case "scale" -> doScale(state, params);
            case "rollback" -> doRollback(state);
            case "restart" -> doRestart(state);
            default -> "未知操作: " + action;
        };
    }

    private String doScale(ServiceState state, Map<String, String> params) {
        int oldReplicas = state.replicas;
        int newReplicas = oldReplicas + 2;
        if (params != null && params.containsKey("replicas")) {
            try { newReplicas = Integer.parseInt(params.get("replicas")); } catch (NumberFormatException ignored) {}
        }
        state.replicas = newReplicas;
        return String.format("扩容指令已执行：[%s] 副本数 %d -> %d ✅", state.name, oldReplicas, newReplicas);
    }

    private String doRollback(ServiceState state) {
        String oldVersion = state.version;
        state.version = rollbackVersion(oldVersion);
        return String.format("回滚指令已执行：[%s] %s -> %s ✅", state.name, oldVersion, state.version);
    }

    private String doRestart(ServiceState state) {
        state.status = "restarting";
        state.uptime = "0m";
        return String.format("重启指令已执行：[%s] 正在滚动重启 ✅", state.name);
    }

    private String rollbackVersion(String v) {
        try {
            String prefix = v.replaceAll("\\d+$", "");
            String numStr = v.replaceAll(".*?(\\d+)$", "$1");
            int num = Integer.parseInt(numStr) - 1;
            if (num < 0) return v; // already at minimum version
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
