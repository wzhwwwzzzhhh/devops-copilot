package com.devboss.service;

import com.devboss.entity.ToolCallLog;
import com.devboss.repository.ToolCallLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolCallServiceTest {

    @Mock
    private ToolCallLogRepository repository;

    @Captor
    private ArgumentCaptor<ToolCallLog> logCaptor;

    private ToolCallService toolCallService;

    @BeforeEach
    void setUp() {
        toolCallService = new ToolCallService(repository);
    }

    @Test
    void shouldRecordSuccessfulToolCall() {
        toolCallService.recordToolCall(
                "session-001",
                "query_metrics",
                "service=order-service",
                "{\"error_rate\": 22.3}",
                150L,
                "SUCCESS",
                null);

        verify(repository, times(1)).save(logCaptor.capture());

        ToolCallLog saved = logCaptor.getValue();
        assertEquals("session-001", saved.getSessionId());
        assertEquals("query_metrics", saved.getToolName());
        assertEquals("service=order-service", saved.getInputSummary());
        assertEquals(150L, saved.getDurationMs());
        assertEquals("SUCCESS", saved.getStatus());
        assertNull(saved.getErrorMessage());
    }

    @Test
    void shouldRecordFailedToolCall() {
        toolCallService.recordToolCall(
                "session-001",
                "execute_action",
                "action=scale, service=order-service",
                "连接超时",
                5000L,
                "FAILED",
                "Connection timeout to Kubernetes API");

        verify(repository, times(1)).save(logCaptor.capture());

        ToolCallLog saved = logCaptor.getValue();
        assertEquals("FAILED", saved.getStatus());
        assertEquals("Connection timeout to Kubernetes API", saved.getErrorMessage());
        assertEquals(5000L, saved.getDurationMs());
    }

    @Test
    void shouldTruncateLongOutput() {
        String longOutput = "a".repeat(2000);

        toolCallService.recordToolCall(
                "session-001",
                "query_logs",
                "service=order-service",
                longOutput,
                100L,
                "SUCCESS",
                null);

        verify(repository).save(logCaptor.capture());
        ToolCallLog saved = logCaptor.getValue();

        assertTrue(saved.getOutputSummary().endsWith("..."));
        assertTrue(saved.getOutputSummary().length() <= 1003); // 1000 + "..."
    }

    @Test
    void shouldHandleRepositoryException() {
        doThrow(new RuntimeException("DB connection failed"))
                .when(repository).save(any(ToolCallLog.class));

        assertDoesNotThrow(() ->
                toolCallService.recordToolCall(
                        "session-001",
                        "query_metrics",
                        "service=order-service",
                        "result",
                        100L,
                        "SUCCESS",
                        null)
        );
    }

    @Test
    void shouldCalculateOutputLength() {
        String output = "{\"error_rate\": 22.3}";

        toolCallService.recordToolCall(
                "session-001",
                "query_metrics",
                "service=order-service",
                output,
                100L,
                "SUCCESS",
                null);

        verify(repository).save(logCaptor.capture());
        assertEquals(output.length(), logCaptor.getValue().getOutputLength());
    }

    @Test
    void shouldHandleNullOutput() {
        toolCallService.recordToolCall(
                "session-001",
                "query_metrics",
                "service=order-service",
                null,
                100L,
                "SUCCESS",
                null);

        verify(repository).save(logCaptor.capture());
        assertEquals(0, logCaptor.getValue().getOutputLength());
        assertNull(logCaptor.getValue().getOutputSummary());
    }
}
