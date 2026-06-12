package com.devboss.llm;

import com.devboss.agent.InvestigationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FallbackAnalyzerTest {

    private InvestigationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new InvestigationContext();
        ctx.setSessionId("test-session");
        ctx.setServiceName("order-service");
    }

    @Test
    void shouldDetectFallbackResponse() {
        assertTrue(FallbackAnalyzer.isFallback("无法连接 LLM 服务"));
        assertTrue(FallbackAnalyzer.isFallback("根据采集到的数据分析，根因为"));
        assertTrue(FallbackAnalyzer.isFallback("健康巡检结果："));
        assertTrue(FallbackAnalyzer.isFallback(null));
        assertFalse(FallbackAnalyzer.isFallback("正常分析结果"));
    }

    @Test
    void shouldAnalyzeFromMetricsAndDbData() {
        String metrics = """
                {
                  "service": "order-service",
                  "metrics": {
                    "http": { "error_rate_percent": 22.3, "p99_latency_ms": 3200 },
                    "cpu": { "usage_percent": 65.2 },
                    "memory": { "usage_percent": 68.0 }
                  }
                }
                """;
        String dbStatus = """
                {
                  "instances": [{
                    "name": "order-db-primary",
                    "status": "DEGRADED",
                    "connection_pool": { "usage_percent": 100, "active": 50, "idle": 0 },
                    "slow_queries": [{
                      "avg_duration_ms": 4850,
                      "count_5min": 28,
                      "sql": "SELECT * FROM orders"
                    }]
                  }]
                }
                """;

        ctx.addCollectedData("metrics", metrics);
        ctx.addCollectedData("database", dbStatus);

        String result = FallbackAnalyzer.analyzeFromData(ctx);

        assertNotNull(result);
        assertTrue(result.contains("order-service"), "Should contain service name");
        assertTrue(result.contains("根因分析"), "Should contain root cause analysis");
        assertTrue(result.contains("处理建议"), "Should contain recommendations");
        assertTrue(result.contains("扩容"), "Should suggest scaling");
    }

    @Test
    void shouldAnalyzeFromMetricsDataOnly() {
        String metrics = """
                {
                  "service": "order-service",
                  "metrics": {
                    "http": { "error_rate_percent": 5.1, "p99_latency_ms": 2800 },
                    "cpu": { "usage_percent": 28.5 },
                    "memory": { "usage_percent": 52.0 }
                  }
                }
                """;

        ctx.addCollectedData("metrics", metrics);

        String result = FallbackAnalyzer.analyzeFromData(ctx);

        assertNotNull(result);
        assertTrue(result.contains("5.1"), "Should extract error rate");
        assertTrue(result.contains("28.5"), "Should extract CPU");
    }

    @Test
    void shouldAnalyzeLogsForNpe() {
        String logs = """
                2026-05-29 ERROR NullPointerException: role is null
                \tat RoleService.checkPermission(RoleService.java:45)
                """;

        ctx.addCollectedData("metrics", "{\"service\":\"user-service\",\"metrics\":{\"http\":{\"error_rate_percent\":5.1}}}");
        ctx.addCollectedData("logs", logs);

        String result = FallbackAnalyzer.analyzeFromData(ctx);

        assertNotNull(result);
        assertTrue(result.contains("NPE") || result.contains("NullPointer"), "Should detect NPE");
    }

    @Test
    void shouldGenerateHealthCheckReport() {
        String allMetrics = """
                {
                  "type": "health_check",
                  "services": [
                    { "service": "order-service", "metrics": { "http": { "error_rate_percent": 22.3 } } },
                    { "service": "payment-service", "metrics": { "http": { "error_rate_percent": 0.8 } } },
                    { "service": "user-service", "metrics": { "http": { "error_rate_percent": 5.1 } } }
                  ]
                }
                """;

        ctx.addCollectedData("health_metrics", allMetrics);

        String result = FallbackAnalyzer.analyzeHealthCheckData(ctx);

        assertNotNull(result);
        assertTrue(result.contains("健康巡检"));
        assertTrue(result.contains("order-service"));
        assertTrue(result.contains("payment-service"));
        assertTrue(result.contains("user-service"));
    }

    @Test
    void shouldHandleEmptyContext() {
        String result = FallbackAnalyzer.analyzeFromData(ctx);

        assertNotNull(result);
        assertTrue(result.contains("order-service") || result.length() > 0);
    }

    @Test
    void shouldHandleNullMetricsGracefully() {
        ctx.addCollectedData("metrics", null);

        String result = FallbackAnalyzer.analyzeFromData(ctx);

        assertNotNull(result);
    }
}
