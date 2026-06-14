package com.devboss.tools;

import com.devboss.entity.ServiceConnection;
import com.devboss.service.ServiceConnectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 日志查询服务：支持真实文件与 Mock 两种来源的关键词检索 */
@Service
public class LogService {

    private static final Logger log = LoggerFactory.getLogger(LogService.class);
    private final ServiceConnectionService connectionService;

    public LogService(ServiceConnectionService connectionService) {
        this.connectionService = connectionService;
    }

    public String getLogs(String serviceName, String keyword) {
        try {
            List<ServiceConnection> logConns = connectionService.findByType("log");
            for (ServiceConnection conn : logConns) {
                if (conn.getTags() != null && conn.getTags().contains(serviceName)) {
                    return readRealLogFile(conn, keyword);
                }
            }
            return readMockLogs(serviceName, keyword);
        } catch (Exception e) {
            log.error("查询日志失败: service={}", serviceName, e);
            return "查询日志失败: " + e.getMessage();
        }
    }

    private static final int MAX_LOG_LENGTH = 5000;

    private String readRealLogFile(ServiceConnection conn, String keyword) {
        try {
            String logPath = conn.getHost();
            if (conn.getProperties() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> props = new ObjectMapper().readValue(conn.getProperties(), Map.class);
                if (props.containsKey("logPath")) {
                    logPath = (String) props.get("logPath");
                }
            }

            Path path = Paths.get(logPath);
            if (!Files.exists(path)) {
                log.warn("日志文件不存在: {}", logPath);
                return "日志文件不存在: " + logPath;
            }

            // 流式读取，避免大文件 OOM
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (keyword != null && !keyword.isEmpty()) {
                        if (line.contains(keyword)) {
                            content.append(line).append("\n");
                        }
                    } else {
                        content.append(line).append("\n");
                        // 仅保留末尾 MAX_LOG_LENGTH，控制内存
                        if (content.length() > MAX_LOG_LENGTH * 2) {
                            content.delete(0, content.length() - MAX_LOG_LENGTH);
                            // 保留截断标记
                            int truncateAt = content.indexOf("\n");
                            if (truncateAt > 0) {
                                content.replace(0, truncateAt + 1, "");
                            }
                        }
                    }
                }
            }

            String result = content.toString();
            if (result.length() > MAX_LOG_LENGTH) {
                result = "...(截取末尾" + MAX_LOG_LENGTH + "字符)\n"
                        + result.substring(result.length() - MAX_LOG_LENGTH);
            }
            log.info("读取真实日志: path={}, length={}", logPath, result.length());
            return result.isEmpty() ? "未找到匹配的日志。" : result;

        } catch (Exception e) {
            log.warn("读取日志文件失败, 降级Mock: {}", e.getMessage());
            return readMockLogs("unknown", keyword);
        }
    }

    private String readMockLogs(String serviceName, String keyword) {
        try {
            String path = getMockLogPath(serviceName);
            if (path == null) {
                return "未知服务，无法查询日志: " + serviceName;
            }
            InputStream is = getClass().getResourceAsStream(path);
            if (is == null) {
                return "未找到服务的日志: " + serviceName;
            }
            String content = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
            if (keyword != null && !keyword.isEmpty()) {
                content = filterByKeyword(content, keyword);
            }
            return content.isEmpty() ? "未找到匹配的日志。" : content;
        } catch (Exception e) {
            return "读取Mock日志失败: " + e.getMessage();
        }
    }

    private String getMockLogPath(String serviceName) {
        return switch (serviceName) {
            case "order-service" -> "/mock/logs/order-service.log";
            case "payment-service" -> "/mock/logs/payment-service.log";
            case "user-service" -> "/mock/logs/user-service.log";
            default -> null;
        };
    }

    private String filterByKeyword(String content, String keyword) {
        return content.lines()
                .filter(line -> line.contains(keyword))
                .collect(Collectors.joining("\n"));
    }
}
