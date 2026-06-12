package com.devboss.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentStepTest {

    @Test
    void shouldHaveAllRequiredSteps() {
        AgentStep[] steps = AgentStep.values();
        assertTrue(steps.length >= 20, "AgentStep should have at least 20 values");

        assertNotNull(AgentStep.valueOf("START"));
        assertNotNull(AgentStep.valueOf("ANALYZE_INCIDENT"));
        assertNotNull(AgentStep.valueOf("QUERY_METRICS"));
        assertNotNull(AgentStep.valueOf("QUERY_LOGS"));
        assertNotNull(AgentStep.valueOf("QUERY_TRACES"));
        assertNotNull(AgentStep.valueOf("QUERY_DATABASE"));
        assertNotNull(AgentStep.valueOf("QUERY_DEPLOYMENTS"));
        assertNotNull(AgentStep.valueOf("RAG_KNOWLEDGE_RETRIEVAL"));
        assertNotNull(AgentStep.valueOf("LLM_REASONING"));
        assertNotNull(AgentStep.valueOf("AWAITING_APPROVAL"));
        assertNotNull(AgentStep.valueOf("EXECUTE_ACTION"));
        assertNotNull(AgentStep.valueOf("GENERATE_REPORT"));
        assertNotNull(AgentStep.valueOf("COMPLETED"));
        assertNotNull(AgentStep.valueOf("FAILED"));
    }

    @Test
    void shouldHaveHealthCheckSteps() {
        assertNotNull(AgentStep.valueOf("HEALTH_CHECK_START"));
        assertNotNull(AgentStep.valueOf("HEALTH_CHECK_METRICS"));
        assertNotNull(AgentStep.valueOf("HEALTH_CHECK_LOGS"));
        assertNotNull(AgentStep.valueOf("HEALTH_CHECK_DATABASE"));
        assertNotNull(AgentStep.valueOf("HEALTH_CHECK_DEPLOYMENTS"));
        assertNotNull(AgentStep.valueOf("HEALTH_CHECK_ANALYZE"));
    }

    @Test
    void shouldHaveDirectQueryStep() {
        assertNotNull(AgentStep.valueOf("DIRECT_QUERY"));
    }

    @Test
    void stepOrderShouldBeCorrect() {
        AgentStep[] steps = AgentStep.values();
        assertEquals(AgentStep.START, steps[0]);
        assertEquals(AgentStep.FAILED, steps[steps.length - 1]);
    }
}
