package com.devboss.tools;

import com.devboss.entity.ServiceConnection;
import com.devboss.service.ServiceConnectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);
    private final ObjectMapper objectMapper;
    private final ServiceConnectionService connectionService;
    private final RestTemplate restTemplate;

    public MetricsService(ObjectMapper objectMapper, ServiceConnectionService connectionService,
                          RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.connectionService = connectionService;
        this.restTemplate = restTemplate;
    }

    @SuppressWarnings("unchecked")
    public String getMetrics(String serviceName) {
        try {
            List<ServiceConnection> prometheusConns = connectionService.findByType("prometheus");
            if (!prometheusConns.isEmpty()) {
                return queryPrometheus(prometheusConns.get(0), serviceName);
            }
            if ("all".equals(serviceName)) {
                return getAllMockMetrics();
            }
            return readMockMetrics(serviceName);
        } catch (Exception e) {
            log.error("查询监控数据失败: service={}", serviceName, e);
            return "{\"error\": \"查询监控数据失败: " + e.getMessage() + "\"}";
        }
    }

    private String queryPrometheus(ServiceConnection conn, String serviceName) {
        String host = conn.getHost();
        String query = "rate(error_count[5m]) / rate(request_count[5m]) * 100";
        try {
            String url = host + "/api/v1/query?query=" + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
            String response = restTemplate.getForObject(url, String.class);
            log.info("Prometheus 查询成功: service={}", serviceName);
            return response;
        } catch (Exception e) {
            log.warn("Prometheus 查询失败 ({}), 降级读 Mock", e.getMessage());
            return readMockMetrics(serviceName);
        }
    }

    private String readMockMetrics(String serviceName) {
        try {
            String path = switch (serviceName == null ? "" : serviceName) {
                case "order-service" -> "/mock/metrics/order-service.json";
                case "payment-service" -> "/mock/metrics/payment-service.json";
                case "user-service" -> "/mock/metrics/user-service.json";
                default -> null;
            };
            if (path == null) {
                return "{\"error\": \"未知服务，无法查询监控数据: " + serviceName + "\"}";
            }
            InputStream is = getClass().getResourceAsStream(path);
            if (is == null) {
                return "{\"error\": \"未找到服务的监控数据: " + serviceName + "\"}";
            }
            Map<String, Object> data = objectMapper.readValue(is, Map.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (Exception e) {
            log.warn("读取Mock监控数据失败: service={}", serviceName, e);
            return "{\"error\": \"读取Mock监控数据失败: " + e.getMessage() + "\"}";
        }
    }

    private String getAllMockMetrics() {
        try {
            String[] services = {"order-service", "payment-service", "user-service"};
            ObjectNode result = objectMapper.createObjectNode();
            result.put("type", "health_check");
            ArrayNode metricsList = result.putArray("services");
            for (String service : services) {
                String path = "/mock/metrics/" + service + ".json";
                InputStream is = getClass().getResourceAsStream(path);
                if (is != null) {
                    Map<String, Object> data = objectMapper.readValue(is, Map.class);
                    metricsList.add(objectMapper.valueToTree(data));
                }
            }
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.warn("读取所有Mock监控数据失败", e);
            return "{\"error\": \"读取所有Mock监控数据失败\"}";
        }
    }
}
