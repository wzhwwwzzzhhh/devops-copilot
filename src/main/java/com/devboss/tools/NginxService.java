package com.devboss.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Nginx 访问日志分析：QPS、状态码分布、响应时间等 */
@Service
public class NginxService {

    private static final Logger log = LoggerFactory.getLogger(NginxService.class);
    private final ObjectMapper objectMapper;

    public NginxService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 获取 Nginx 访问日志分析
     * 返回包含 QPS / 状态码分布 / 响应时间 / Top URL / Top IP / 错误路径的 JSON
     */
    public String getAccessAnalysis() {
        try {
            return buildMockAccessAnalysis();
        } catch (Exception e) {
            log.warn("生成 Mock Nginx 访问分析失败", e);
            return "{\"error\": \"生成 Mock Nginx 访问分析失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取 QPS 趋势（24 小时）
     * 返回包含 24 个数据点的数组 JSON
     */
    public String getQpsTrend() {
        try {
            return buildMockQpsTrend();
        } catch (Exception e) {
            log.warn("生成 Mock QPS 趋势失败", e);
            return "{\"error\": \"生成 Mock QPS 趋势失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取 Nginx 全量状态
     * 组合 accessAnalysis + qpsTrend 到统一 JSON
     */
    public String getFullStatus() {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("configured", true);

            String analysisJson = getAccessAnalysis();
            result.set("analysis", objectMapper.readTree(analysisJson));

            String trendJson = getQpsTrend();
            result.set("qps_trend", objectMapper.readTree(trendJson));

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.warn("获取 Nginx 全量状态失败", e);
            return "{\"error\": \"获取 Nginx 全量状态失败: " + e.getMessage() + "\"}";
        }
    }

    // ========== Mock Data Methods ==========

    private String buildMockAccessAnalysis() {
        ObjectNode root = objectMapper.createObjectNode();

        // QPS
        root.put("qps", 1250);
        root.put("peak_qps", 3200);

        // Status codes distribution
        ObjectNode statusCodes = root.putObject("status_codes");
        statusCodes.put("2xx", 95.2);
        statusCodes.put("3xx", 2.1);
        statusCodes.put("4xx", 1.8);
        statusCodes.put("5xx", 0.9);

        // Response time percentiles
        ObjectNode responseTime = root.putObject("response_time");
        responseTime.put("p50", 45);
        responseTime.put("p95", 180);
        responseTime.put("p99", 520);
        responseTime.put("max", 3200);

        // Top 10 URLs
        ArrayNode topUrls = root.putArray("top_urls");
        addTopUrl(topUrls, "/api/order/list", 45210, 38);
        addTopUrl(topUrls, "/api/product/detail", 38920, 42);
        addTopUrl(topUrls, "/api/user/info", 35200, 28);
        addTopUrl(topUrls, "/api/payment/submit", 28450, 156);
        addTopUrl(topUrls, "/api/search/query", 26100, 65);
        addTopUrl(topUrls, "/api/cart/add", 22300, 32);
        addTopUrl(topUrls, "/api/order/detail", 19800, 41);
        addTopUrl(topUrls, "/api/product/list", 17600, 35);
        addTopUrl(topUrls, "/api/user/login", 15200, 22);
        addTopUrl(topUrls, "/api/address/list", 12800, 18);

        // Top 5 client IPs
        ArrayNode topIps = root.putArray("top_ips");
        addTopIp(topIps, "192.168.1.100", 45800);
        addTopIp(topIps, "10.0.0.55", 32100);
        addTopIp(topIps, "172.16.0.23", 28700);
        addTopIp(topIps, "192.168.2.200", 19500);
        addTopIp(topIps, "10.0.1.88", 14200);

        // Top 5 error paths
        ArrayNode errorPaths = root.putArray("error_paths");
        addErrorPath(errorPaths, "/api/payment/submit", 502, 128);
        addErrorPath(errorPaths, "/api/order/create", 500, 96);
        addErrorPath(errorPaths, "/api/search/query", 504, 72);
        addErrorPath(errorPaths, "/api/user/register", 503, 45);
        addErrorPath(errorPaths, "/api/upload/image", 413, 38);

        return writeJson(root);
    }

    private String buildMockQpsTrend() {
        ArrayNode array = objectMapper.createArrayNode();

        int[] qpsValues = {
            320, 280, 250, 210, 180, 230, 450, 780,
            1100, 1450, 1800, 2100, 2500, 2800, 3200, 2900,
            2600, 2300, 2000, 1700, 1400, 1100, 800, 500
        };

        for (int i = 0; i < 24; i++) {
            ObjectNode point = array.addObject();
            point.put("hour", i);
            point.put("qps", qpsValues[i]);
        }

        return writeJson(array);
    }

    private void addTopUrl(ArrayNode array, String url, int count, int avgTime) {
        ObjectNode entry = array.addObject();
        entry.put("url", url);
        entry.put("count", count);
        entry.put("avg_time", avgTime);
        entry.put("total_time", count * avgTime);
    }

    private void addTopIp(ArrayNode array, String ip, int count) {
        ObjectNode entry = array.addObject();
        entry.put("ip", ip);
        entry.put("count", count);
    }

    private void addErrorPath(ArrayNode array, String path, int statusCode, int count) {
        ObjectNode entry = array.addObject();
        entry.put("path", path);
        entry.put("status_code", statusCode);
        entry.put("count", count);
    }

    private String writeJson(Object node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            log.warn("序列化 JSON 失败", e);
            return "{\"error\": \"序列化 JSON 失败: " + e.getMessage() + "\"}";
        }
    }
}
