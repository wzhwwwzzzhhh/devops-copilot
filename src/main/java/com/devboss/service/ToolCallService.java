package com.devboss.service;

import com.devboss.entity.ToolCallLog;
import com.devboss.repository.ToolCallLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ToolCallService {

    private static final Logger log = LoggerFactory.getLogger(ToolCallService.class);

    private final ToolCallLogRepository repository;

    public ToolCallService(ToolCallLogRepository repository) {
        this.repository = repository;
    }

    public void recordToolCall(String sessionId, String toolName, String inputSummary,
                               String outputSummary, long durationMs, String status,
                               String errorMessage) {
        try {
            ToolCallLog record = new ToolCallLog(sessionId, toolName, truncate(inputSummary, 500));
            record.setOutputSummary(truncate(outputSummary, 1000));
            record.setOutputLength(outputSummary != null ? outputSummary.length() : 0);
            record.setDurationMs(durationMs);
            record.setStatus(status);
            record.setErrorMessage(errorMessage);
            repository.save(record);
            log.debug("工具调用日志已记录: tool={}, session={}, status={}, duration={}ms",
                    toolName, sessionId, status, durationMs);
        } catch (Exception e) {
            log.error("工具调用日志记录失败: tool={}, session={}", toolName, sessionId, e);
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
