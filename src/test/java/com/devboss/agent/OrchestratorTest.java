package com.devboss.agent;

import com.devboss.graph.NodeResult;
import com.devboss.graph.ToolCallHelper;
import com.devboss.graph.ToolRegistry;
import com.devboss.graph.impl.AwaitingApprovalNode;
import com.devboss.graph.impl.CallToolNode;
import com.devboss.graph.impl.DirectQueryNode;
import com.devboss.graph.impl.EndNode;
import com.devboss.graph.impl.ExecuteActionNode;
import com.devboss.graph.impl.GenerateReportNode;
import com.devboss.graph.impl.HealthCheckAnalyzeNode;
import com.devboss.graph.impl.HealthCheckDatabaseNode;
import com.devboss.graph.impl.HealthCheckDeploymentsNode;
import com.devboss.graph.impl.HealthCheckLogsNode;
import com.devboss.graph.impl.HealthCheckMetricsNode;
import com.devboss.graph.impl.HealthCheckStartNode;
import com.devboss.graph.impl.ReActNode;
import com.devboss.graph.impl.StartNode;
import com.devboss.llm.ChatService;
import com.devboss.memory.StateManager;
import com.devboss.service.ConversationService;
import com.devboss.service.MessageService;
import com.devboss.service.ToolCallService;
import com.devboss.knowledge.ExperienceMemoryService;
import com.devboss.tools.DatabaseService;
import com.devboss.tools.DeployService;
import com.devboss.tools.DockerService;
import com.devboss.tools.K8sService;
import com.devboss.tools.ESMonitorService;
import com.devboss.tools.LogService;
import com.devboss.tools.MetricsService;
import com.devboss.tools.NginxService;
import com.devboss.tools.RabbitMQService;
import com.devboss.tools.RedisService;
import com.devboss.tools.SystemMonitorService;
import com.devboss.tools.SslService;
import com.devboss.tools.AlertService;
import com.devboss.tools.TraceService;
import com.devboss.knowledge.RagService;
import com.devboss.service.ServiceConnectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestratorTest {

    @Mock private MetricsService metricsService;
    @Mock private LogService logService;
    @Mock private TraceService traceService;
    @Mock private DatabaseService databaseService;
    @Mock private DeployService deployService;
    @Mock private RagService ragService;
    @Mock private RedisService redisService;
    @Mock private RabbitMQService rabbitMQService;
    @Mock private SystemMonitorService systemMonitorService;
    @Mock private ESMonitorService esMonitorService;
    @Mock private DockerService dockerService;
    @Mock private K8sService k8sService;
    @Mock private NginxService nginxService;
    @Mock private SslService sslService;
    @Mock private AlertService alertService;
    @Mock private ExperienceMemoryService experienceMemoryService;
    @Mock private ServiceConnectionService connectionService;
    @Mock private ChatService chatService;
    @Mock private StateManager stateManager;
    @Mock private ToolCallService toolCallService;
    @Mock private MessageService messageService;
    @Mock private ConversationService conversationService;

    private Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        ToolCallHelper toolCallHelper = new ToolCallHelper(toolCallService);
        ToolRegistry toolRegistry = new ToolRegistry(metricsService, logService, traceService,
                databaseService, deployService, toolCallHelper, ragService,
                experienceMemoryService, redisService, rabbitMQService, systemMonitorService,
                esMonitorService, dockerService, k8sService, nginxService, sslService, alertService);

        when(connectionService.getServiceNames()).thenReturn(java.util.List.of("order-service", "payment-service", "user-service"));

        StartNode startNode = new StartNode();
        ReActNode reActNode = new ReActNode(chatService, toolRegistry, messageService, experienceMemoryService);
        CallToolNode callToolNode = new CallToolNode(toolRegistry);
        AwaitingApprovalNode awaitingApprovalNode = new AwaitingApprovalNode();
        ExecuteActionNode executeActionNode = new ExecuteActionNode(deployService, toolCallHelper);
        GenerateReportNode generateReportNode = new GenerateReportNode(chatService, messageService, stateManager, experienceMemoryService);
        HealthCheckStartNode healthCheckStartNode = new HealthCheckStartNode();
        HealthCheckMetricsNode healthCheckMetricsNode = new HealthCheckMetricsNode(metricsService, toolCallHelper, connectionService);
        HealthCheckLogsNode healthCheckLogsNode = new HealthCheckLogsNode(logService, toolCallHelper, connectionService);
        HealthCheckDatabaseNode healthCheckDatabaseNode = new HealthCheckDatabaseNode(databaseService, toolCallHelper, connectionService);
        HealthCheckDeploymentsNode healthCheckDeploymentsNode = new HealthCheckDeploymentsNode(deployService, toolCallHelper, connectionService);
        HealthCheckAnalyzeNode healthCheckAnalyzeNode = new HealthCheckAnalyzeNode(chatService, messageService, connectionService);
        DirectQueryNode directQueryNode = new DirectQueryNode(metricsService, deployService, toolCallHelper);
        EndNode endNode = new EndNode();

        orchestrator = new Orchestrator(
                stateManager, messageService, conversationService,
                startNode, reActNode, callToolNode,
                awaitingApprovalNode, executeActionNode, generateReportNode,
                healthCheckStartNode, healthCheckMetricsNode, healthCheckLogsNode,
                healthCheckDatabaseNode, healthCheckDeploymentsNode, healthCheckAnalyzeNode,
                directQueryNode, endNode);
    }

    /* ========== 路由测试 ========== */

    @Test
    void shouldRouteToHealthCheckOnHealthKeyword() {
        InvestigationContext ctx = orchestrator.createContext("test-session", "跑一次健康巡检");

        NodeResult result = orchestrator.executeStep(ctx);

        assertEquals("HEALTH_CHECK_START", ctx.getCurrentNodeId());
        assertTrue(result.output().contains("巡检"));
    }

    @Test
    void shouldRouteToReActOnNormalInput() {
        InvestigationContext ctx = orchestrator.createContext("test-session", "order-service 报错了");

        NodeResult result = orchestrator.executeStep(ctx);

        assertEquals("REACT_DECIDE", ctx.getCurrentNodeId());
        assertTrue(result.output().contains("自主决策"));
    }

    @Test
    void shouldRouteToDirectQueryOnVersionCheck() {
        InvestigationContext ctx = orchestrator.createContext("test-session", "查一下 payment-service 的版本");

        NodeResult result = orchestrator.executeStep(ctx);

        assertEquals("DIRECT_QUERY", ctx.getCurrentNodeId());
    }

    /* ========== ReAct 决策 + 工具调用 ========== */

    @Test
    void shouldCallToolThenDecideAgain() {
        when(chatService.chat(anyString())).thenReturn("TOOL: query_metrics");
        when(metricsService.getMetrics(anyString())).thenReturn("{\"cpu\": 65}");
        when(messageService.getHistoryContext(anyString())).thenReturn("");

        InvestigationContext ctx = orchestrator.createContext("test-session", "order-service error");

        orchestrator.executeStep(ctx);
        assertEquals("REACT_DECIDE", ctx.getCurrentNodeId());

        NodeResult toolResult = orchestrator.executeStep(ctx);
        assertEquals("CALL_TOOL", ctx.getCurrentNodeId());
        assertTrue(toolResult.output().contains("query_metrics"));
    }

    @Test
    void shouldFinishWhenLlmSaysFinalAnswer() {
        when(chatService.chat(anyString())).thenReturn("FINAL_ANSWER: 根因是数据库连接池耗尽，建议优化慢SQL。");
        when(messageService.getHistoryContext(anyString())).thenReturn("");

        InvestigationContext ctx = orchestrator.createContext("test-session", "order-service error");

        orchestrator.executeStep(ctx);
        assertEquals("REACT_DECIDE", ctx.getCurrentNodeId());

        NodeResult result = orchestrator.executeStep(ctx);
        assertEquals("GENERATE_REPORT", ctx.getCurrentNodeId());
        assertTrue(result.output().contains("根因"));
    }

    @Test
    void shouldTriggerApprovalOnScaleKeyword() {
        when(chatService.chat(anyString())).thenReturn("FINAL_ANSWER: 根因为数据库连接池耗尽，建议扩容连接池。");
        when(messageService.getHistoryContext(anyString())).thenReturn("");

        InvestigationContext ctx = orchestrator.createContext("test-session", "order-service error");

        orchestrator.executeStep(ctx);
        orchestrator.executeStep(ctx);

        assertEquals("AWAITING_APPROVAL", ctx.getCurrentNodeId());
        assertTrue(ctx.isAwaitingApproval());
    }

    @Test
    void shouldUseFallbackWhenLlmUnavailable() {
        when(chatService.chat(anyString())).thenReturn("无法连接 LLM 服务");
        when(messageService.getHistoryContext(anyString())).thenReturn("");

        InvestigationContext ctx = orchestrator.createContext("test-session", "order-service error");

        orchestrator.executeStep(ctx);
        assertEquals("REACT_DECIDE", ctx.getCurrentNodeId());

        NodeResult result = orchestrator.executeStep(ctx);
        assertNotNull(result.output());
        assertTrue(result.output().contains("根因") || result.output().contains("分析"));
    }

    /* ========== 审批流程 ========== */

    @Test
    void shouldExecuteActionWhenApproved() {
        when(deployService.executeAction(anyString(), anyString(), anyMap()))
                .thenReturn("扩容指令已执行");

        InvestigationContext ctx = new InvestigationContext();
        ctx.setSessionId("test-session");
        ctx.setServiceName("order-service");
        ctx.setCurrentNodeId("AWAITING_APPROVAL");
        ctx.setAwaitingApproval(true);
        ctx.setPendingAction("scale");
        ctx.setUserMessage("Y");

        NodeResult result = orchestrator.executeStep(ctx);

        assertEquals("EXECUTE_ACTION", ctx.getCurrentNodeId());
        assertFalse(ctx.isAwaitingApproval());
    }

    @Test
    void shouldCancelActionWhenRejected() {
        InvestigationContext ctx = new InvestigationContext();
        ctx.setSessionId("test-session");
        ctx.setCurrentNodeId("AWAITING_APPROVAL");
        ctx.setAwaitingApproval(true);
        ctx.setUserMessage("N");

        NodeResult result = orchestrator.executeStep(ctx);

        assertEquals("GENERATE_REPORT", ctx.getCurrentNodeId());
        assertTrue(result.output().contains("取消"));
    }

    @Test
    void shouldGenerateReportThenEnd() {
        when(chatService.chat(anyString())).thenReturn("## 排查报告\n完成");
        when(messageService.getHistoryContext(anyString())).thenReturn("");

        InvestigationContext ctx = new InvestigationContext();
        ctx.setSessionId("test-session");
        ctx.setServiceName("order-service");
        ctx.setCurrentNodeId("GENERATE_REPORT");
        ctx.setAnalysisResult("连接池耗尽，执行扩容");
        ctx.setUserMessage("order-service error");

        NodeResult result = orchestrator.executeStep(ctx);

        assertTrue(orchestrator.isFinished(ctx));
        assertNotNull(result.output());
        assertTrue(result.output().contains("排查报告") || result.output().contains("完成"));
    }

    /* ========== 健康巡检全流程 ========== */

    @Test
    void shouldCompleteHealthCheckFlow() {
        when(metricsService.getMetrics("all")).thenReturn("{\"type\":\"health_check\",\"services\":[]}");
        when(logService.getLogs("order-service", "ERROR")).thenReturn("");
        when(logService.getLogs("user-service", "ERROR")).thenReturn("");
        when(logService.getLogs("payment-service", "ERROR")).thenReturn("");
        when(databaseService.getDbStatus("all")).thenReturn("{}");
        when(deployService.getDeployments("all")).thenReturn("{}");
        when(chatService.chat(anyString())).thenReturn("无法连接 LLM 服务");
        when(messageService.getHistoryContext(anyString())).thenReturn("");

        InvestigationContext ctx = orchestrator.createContext("test-session", "跑一次健康巡检");

        orchestrator.executeStep(ctx);
        assertEquals("HEALTH_CHECK_START", ctx.getCurrentNodeId());

        orchestrator.executeStep(ctx);
        assertEquals("HEALTH_CHECK_METRICS", ctx.getCurrentNodeId());

        orchestrator.executeStep(ctx);
        assertEquals("HEALTH_CHECK_LOGS", ctx.getCurrentNodeId());

        orchestrator.executeStep(ctx);
        assertEquals("HEALTH_CHECK_DATABASE", ctx.getCurrentNodeId());

        orchestrator.executeStep(ctx);
        assertEquals("HEALTH_CHECK_DEPLOYMENTS", ctx.getCurrentNodeId());

        NodeResult result = orchestrator.executeStep(ctx);
        assertEquals("GENERATE_REPORT", ctx.getCurrentNodeId());
        assertNotNull(result.output());
    }

    /* ========== 上下文测试 ========== */

    @Test
    void shouldCreateContextWithStartNode() {
        InvestigationContext ctx = orchestrator.createContext("test-session", "你好");

        assertNotNull(ctx);
        assertEquals("test-session", ctx.getSessionId());
        assertEquals("START", ctx.getCurrentNodeId());
    }

    @Test
    void shouldLoadExistingContext() {
        when(stateManager.getContext("existing-session"))
                .thenReturn(new InvestigationContext());

        InvestigationContext ctx = orchestrator.loadContext("existing-session");

        assertNotNull(ctx);
    }

    @Test
    void shouldReturnNullForMissingContext() {
        when(stateManager.getContext("unknown-session")).thenReturn(null);

        InvestigationContext ctx = orchestrator.loadContext("unknown-session");

        assertNull(ctx);
    }

    @Test
    void isFinishedShouldReturnTrueForEnd() {
        InvestigationContext ctx = new InvestigationContext();
        ctx.setCurrentNodeId("END");
        assertTrue(orchestrator.isFinished(ctx));
    }

    @Test
    void isFinishedShouldReturnFalseForActiveNode() {
        InvestigationContext ctx = new InvestigationContext();
        ctx.setCurrentNodeId("QUERY_METRICS");
        assertFalse(orchestrator.isFinished(ctx));
    }

    /* ========== 单步指令 ========== */

    @Test
    void shouldCompleteDirectQueryFlow() {
        when(deployService.getDeployments(anyString())).thenReturn("{\"services\": []}");

        InvestigationContext ctx = orchestrator.createContext("test-session", "查一下 order-service 的版本");

        orchestrator.executeStep(ctx);
        assertEquals("DIRECT_QUERY", ctx.getCurrentNodeId());

        NodeResult result = orchestrator.executeStep(ctx);
        assertTrue(orchestrator.isFinished(ctx));
        assertNotNull(result.output());
    }

}

