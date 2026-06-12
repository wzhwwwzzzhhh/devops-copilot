package com.devboss.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InvestigationContextTest {

    private InvestigationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new InvestigationContext();
    }

    @Test
    void shouldStartAtStartStep() {
        assertEquals(AgentStep.START, ctx.getCurrentStep());
    }

    @Test
    void shouldStoreAndRetrieveSessionId() {
        ctx.setSessionId("test-session-001");
        assertEquals("test-session-001", ctx.getSessionId());
    }

    @Test
    void shouldStoreAndRetrieveUserMessage() {
        ctx.setUserMessage("order-service 报错了");
        assertEquals("order-service 报错了", ctx.getUserMessage());
    }

    @Test
    void shouldStoreAndRetrieveServiceName() {
        ctx.setServiceName("order-service");
        assertEquals("order-service", ctx.getServiceName());
    }

    @Test
    void shouldCollectAndRetrieveData() {
        ctx.addCollectedData("metrics", "{\"cpu\": 65.2}");
        ctx.addCollectedData("logs", "ERROR: connection timeout");

        assertEquals("{\"cpu\": 65.2}", ctx.getCollectedData("metrics"));
        assertEquals("ERROR: connection timeout", ctx.getCollectedData("logs"));
    }

    @Test
    void shouldReturnNullForMissingData() {
        assertNull(ctx.getCollectedData("nonexistent"));
    }

    @Test
    void shouldLogToolCalls() {
        ctx.logToolCall("query_metrics", "{\"error_rate\": 22.3}");
        ctx.logToolCall("query_logs", "ERROR found 15 times");

        String summary = ctx.getToolCallLogSummary();
        assertTrue(summary.contains("query_metrics"));
        assertTrue(summary.contains("query_logs"));
        assertTrue(summary.contains("22.3"));
    }

    @Test
    void shouldAddMessages() {
        ctx.addMessage("user", "帮我看看问题");
        ctx.addMessage("assistant", "正在排查...");

        assertEquals(2, ctx.getMessages().size());
        assertTrue(ctx.getMessages().get(0).contains("user"));
        assertTrue(ctx.getMessages().get(0).contains("帮我看看问题"));
    }

    @Test
    void shouldHandleApprovalFlow() {
        assertFalse(ctx.isAwaitingApproval());
        ctx.setAwaitingApproval(true);
        assertTrue(ctx.isAwaitingApproval());

        ctx.setPendingAction("scale");
        assertEquals("scale", ctx.getPendingAction());

        ctx.setAwaitingApproval(false);
        assertFalse(ctx.isAwaitingApproval());
    }

    @Test
    void shouldTrackAnalysisAndReport() {
        ctx.setAnalysisResult("根因是数据库连接池耗尽");
        ctx.setReport("## 故障排查报告\n根因: 连接池耗尽");

        assertTrue(ctx.getAnalysisResult().contains("连接池"));
        assertTrue(ctx.getReport().contains("故障排查报告"));
    }

    @Test
    void shouldTrackRetryCount() {
        assertFalse(ctx.isMaxRetriesExceeded());
        ctx.incrementRetry();
        ctx.incrementRetry();
        ctx.incrementRetry();
        assertTrue(ctx.isMaxRetriesExceeded());
    }

    @Test
    void shouldManageStepTransitions() {
        ctx.setCurrentStep(AgentStep.QUERY_METRICS);
        assertEquals(AgentStep.QUERY_METRICS, ctx.getCurrentStep());

        ctx.setCurrentStep(AgentStep.LLM_REASONING);
        assertEquals(AgentStep.LLM_REASONING, ctx.getCurrentStep());
    }

    @Test
    void shouldStoreActionParams() {
        ctx.getActionParams().put("replicas", "5");
        ctx.getActionParams().put("service", "order-service");

        assertEquals("5", ctx.getActionParams().get("replicas"));
        assertEquals(2, ctx.getActionParams().size());
    }
}
