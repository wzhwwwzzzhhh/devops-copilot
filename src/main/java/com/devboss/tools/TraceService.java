package com.devboss.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Map;

/**
 * 链路追踪服务类
 * 提供查询服务链路追踪数据的功能
 */
@Service
public class TraceService {

    private static final Logger log = LoggerFactory.getLogger(TraceService.class);
    /** JSON对象映射器 */
    private final ObjectMapper objectMapper;

    /**
     * 构造函数，注入ObjectMapper
     * @param objectMapper JSON对象映射器
     */
    public TraceService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 获取指定服务的链路追踪数据
     * @param serviceName 服务名称
     * @return 链路追踪数据（JSON格式）
     */
    @SuppressWarnings("unchecked")
    public String getTraces(String serviceName) {
        try {
            String path = switch (serviceName) {
                case "order-service" -> "/mock/traces/order-service.json";
                default -> null;
            };
            if (path == null) {
                return "{\"error\": \"未知服务，无法查询链路追踪: " + serviceName + "\"}";
            }
            InputStream is = getClass().getResourceAsStream(path);
            if (is == null) {
                return "{\"error\": \"未找到服务的链路追踪数据: " + serviceName + "\"}";
            }
            Map<String, Object> data = objectMapper.readValue(is, Map.class);
            log.info("查询链路追踪: service={}", serviceName);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        } catch (Exception e) {
            log.error("查询链路追踪失败: service={}", serviceName, e);
            return "{\"error\": \"查询链路追踪失败: " + e.getMessage() + "\"}";
        }
    }
}
