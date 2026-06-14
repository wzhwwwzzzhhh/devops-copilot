package com.devboss.graph;

import com.devboss.service.ToolCallService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/** 工具调用辅助类：提供统一日志记录与异常包装的调用模板 */
@Component
public class ToolCallHelper {

    private static final Logger log = LoggerFactory.getLogger(ToolCallHelper.class);

    private final ToolCallService toolCallService;

    public ToolCallHelper(ToolCallService toolCallService) {
        this.toolCallService = toolCallService;
    }

    public String logAndCall(String sessionId, String toolName, String inputSummary,
                             Supplier<String> call) {
        long start = System.currentTimeMillis();
        String status = "SUCCESS";
        String errorMessage = null;
        String result;
        try {
            result = call.get();
        } catch (Exception e) {
            status = "FAILED";
            errorMessage = e.getMessage();
            result = "错误: " + e.getMessage();
            log.error("工具调用失败: tool={}, session={}", toolName, sessionId, e);
        }
        long duration = System.currentTimeMillis() - start;
        toolCallService.recordToolCall(sessionId, toolName, inputSummary, result, duration, status, errorMessage);
        return result;
    }
}
