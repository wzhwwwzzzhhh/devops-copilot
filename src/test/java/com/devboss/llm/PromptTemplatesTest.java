package com.devboss.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptTemplatesTest {

    @Test
    void shouldGenerateInvestigationPrompt() {
        String prompt = PromptTemplates.investigationPrompt(
                "order-service 报错了",
                "order-service",
                "错误率 22.3%");

        assertNotNull(prompt);
        assertTrue(prompt.contains("order-service 报错了"));
        assertTrue(prompt.contains("order-service"));
        assertTrue(prompt.contains("22.3%"));
        assertTrue(prompt.contains("运维助手"));
    }

    @Test
    void shouldGenerateInvestigationPromptWithHistory() {
        String prompt = PromptTemplates.investigationPrompt(
                "还在报错",
                "order-service",
                "错误率 25%",
                "user: 之前报错了\nassistant: 建议扩容");

        assertNotNull(prompt);
        assertTrue(prompt.contains("对话历史"));
        assertTrue(prompt.contains("之前报错了"));
        assertTrue(prompt.contains("新一轮排查"));
    }

    @Test
    void shouldGenerateInvestigationPromptWithoutHistory() {
        String prompt = PromptTemplates.investigationPrompt(
                "order-service 报错了",
                "order-service",
                "错误率 22.3%",
                "");

        assertNotNull(prompt);
        assertFalse(prompt.contains("对话历史"));
    }

    @Test
    void shouldGenerateReportPrompt() {
        String prompt = PromptTemplates.reportPrompt(
                "order-service 报错了",
                "query_metrics: 错误率22.3%",
                "根因为数据库连接池耗尽");

        assertNotNull(prompt);
        assertTrue(prompt.contains("故障排查报告"));
        assertTrue(prompt.contains("根因分析"));
        assertTrue(prompt.contains("处理措施"));
        assertTrue(prompt.contains("后续建议"));
    }

    @Test
    void shouldGenerateReportPromptWithHistory() {
        String prompt = PromptTemplates.reportPrompt(
                "还在报错",
                "query_metrics: 错误率25%",
                "根因相同",
                "user: 之前排查过");

        assertNotNull(prompt);
        assertTrue(prompt.contains("对话历史参考"));
    }

    @Test
    void shouldGenerateHealthCheckPrompt() {
        String prompt = PromptTemplates.healthCheckPrompt("order-service: 健康");

        assertNotNull(prompt);
        assertTrue(prompt.contains("健康巡检"));
        assertTrue(prompt.contains("健康"));
    }

    @Test
    void shouldGenerateHealthCheckPromptWithHistory() {
        String prompt = PromptTemplates.healthCheckPrompt("order-service: 异常", "user: 之前巡检过");

        assertNotNull(prompt);
        assertTrue(prompt.contains("对话历史"));
    }

    @Test
    void shouldGenerateDirectCommandPrompt() {
        String prompt = PromptTemplates.directCommandPrompt("查一下 payment-service 的版本");

        assertNotNull(prompt);
        assertTrue(prompt.contains("payment-service"));
    }
}
