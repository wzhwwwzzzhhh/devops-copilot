package com.devboss.tools;

import com.devboss.service.ServiceConnectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsServiceTest {

    @Mock private ServiceConnectionService connectionService;
    @Mock private RestTemplate restTemplate;

    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        when(connectionService.findByType("prometheus")).thenReturn(java.util.Collections.emptyList());
        metricsService = new MetricsService(new ObjectMapper(), connectionService, restTemplate);
    }

    @Test
    void shouldReturnMetricsForOrderService() {
        String result = metricsService.getMetrics("order-service");

        assertNotNull(result);
        assertTrue(result.contains("order-service"));
        assertTrue(result.contains("error_rate_percent"));
        assertTrue(result.contains("22.3"));
    }

    @Test
    void shouldReturnMetricsForPaymentService() {
        String result = metricsService.getMetrics("payment-service");

        assertNotNull(result);
        assertTrue(result.contains("payment-service"));
        assertTrue(result.contains("error_rate_percent"));
        assertTrue(result.contains("0.8"));
    }

    @Test
    void shouldReturnMetricsForUserService() {
        String result = metricsService.getMetrics("user-service");

        assertNotNull(result);
        assertTrue(result.contains("user-service"));
        assertTrue(result.contains("error_rate_percent"));
        assertTrue(result.contains("5.1"));
    }

    @Test
    void shouldReturnDefaultMetricsForUnknownService() {
        String result = metricsService.getMetrics("unknown-service");

        assertNotNull(result);
        assertTrue(result.contains("order-service"));
    }

    @Test
    void shouldReturnAllMetricsForHealthCheck() {
        String result = metricsService.getMetrics("all");

        assertNotNull(result);
        assertTrue(result.contains("health_check"));
        assertTrue(result.contains("order-service"));
        assertTrue(result.contains("payment-service"));
        assertTrue(result.contains("user-service"));
    }

    @Test
    void shouldParseAsValidJson() {
        String result = metricsService.getMetrics("order-service");

        assertNotNull(result);
        assertTrue(result.startsWith("{") || result.startsWith("["),
                "Response should be valid JSON starting with { or [");
        assertTrue(result.endsWith("}") || result.endsWith("]"),
                "Response should be valid JSON ending with } or ]");
    }
}
