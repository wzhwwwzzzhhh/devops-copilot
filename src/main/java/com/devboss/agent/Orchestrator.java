package com.devboss.agent;

import com.devboss.graph.Graph;
import com.devboss.graph.Node;
import com.devboss.graph.NodeResult;
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
import com.devboss.memory.StateManager;
import com.devboss.service.ConversationService;
import com.devboss.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 编排器核心类：组装图编排引擎并根据用户意图选择执行链路 */
@Component
public class Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);

    private final Graph graph;
    private final StateManager stateManager;
    private final MessageService messageService;
    private final ConversationService conversationService;

    public Orchestrator(StateManager stateManager, MessageService messageService,
                        ConversationService conversationService,
                        StartNode startNode, ReActNode reActNode, CallToolNode callToolNode,
                        AwaitingApprovalNode awaitingApprovalNode, ExecuteActionNode executeActionNode,
                        GenerateReportNode generateReportNode, HealthCheckStartNode healthCheckStartNode,
                        HealthCheckMetricsNode healthCheckMetricsNode, HealthCheckLogsNode healthCheckLogsNode,
                        HealthCheckDatabaseNode healthCheckDatabaseNode,
                        HealthCheckDeploymentsNode healthCheckDeploymentsNode,
                        HealthCheckAnalyzeNode healthCheckAnalyzeNode,
                        DirectQueryNode directQueryNode, EndNode endNode) {
        this.stateManager = stateManager;
        this.messageService = messageService;
        this.conversationService = conversationService;
        this.graph = new Graph();
        buildGraph(startNode, reActNode, callToolNode,
                awaitingApprovalNode, executeActionNode, generateReportNode,
                healthCheckStartNode, healthCheckMetricsNode, healthCheckLogsNode,
                healthCheckDatabaseNode, healthCheckDeploymentsNode, healthCheckAnalyzeNode,
                directQueryNode, endNode);
    }

    private void buildGraph(
            StartNode startNode, ReActNode reActNode, CallToolNode callToolNode,
            AwaitingApprovalNode awaitingApprovalNode, ExecuteActionNode executeActionNode,
            GenerateReportNode generateReportNode, HealthCheckStartNode healthCheckStartNode,
            HealthCheckMetricsNode healthCheckMetricsNode, HealthCheckLogsNode healthCheckLogsNode,
            HealthCheckDatabaseNode healthCheckDatabaseNode,
            HealthCheckDeploymentsNode healthCheckDeploymentsNode,
            HealthCheckAnalyzeNode healthCheckAnalyzeNode,
            DirectQueryNode directQueryNode, EndNode endNode) {
        graph.addNode("START", startNode);
        graph.addNode("REACT_DECIDE", reActNode);
        graph.addNode("CALL_TOOL", callToolNode);
        graph.addNode("AWAITING_APPROVAL", awaitingApprovalNode);
        graph.addNode("EXECUTE_ACTION", executeActionNode);
        graph.addNode("GENERATE_REPORT", generateReportNode);
        graph.addNode("HEALTH_CHECK_START", healthCheckStartNode);
        graph.addNode("HEALTH_CHECK_METRICS", healthCheckMetricsNode);
        graph.addNode("HEALTH_CHECK_LOGS", healthCheckLogsNode);
        graph.addNode("HEALTH_CHECK_DATABASE", healthCheckDatabaseNode);
        graph.addNode("HEALTH_CHECK_DEPLOYMENTS", healthCheckDeploymentsNode);
        graph.addNode("HEALTH_CHECK_ANALYZE", healthCheckAnalyzeNode);
        graph.addNode("DIRECT_QUERY", directQueryNode);
        graph.addNode("END", endNode);
        graph.setStartNode("START");
    }

    public Graph getGraph() {
        return graph;
    }

    public InvestigationContext createContext(String sessionId, String userMessage) {
        InvestigationContext ctx = new InvestigationContext();
        ctx.setSessionId(sessionId);
        ctx.setUserMessage(userMessage);
        ctx.setCurrentNodeId(graph.getStartNodeId());
        ctx.addMessage("user", userMessage);
        messageService.saveMessage(sessionId, "user", userMessage);
        conversationService.createOrUpdate(sessionId, userMessage);
        stateManager.saveContext(sessionId, ctx);
        return ctx;
    }

    public InvestigationContext loadContext(String sessionId) {
        return stateManager.getContext(sessionId);
    }

    public NodeResult executeStep(InvestigationContext ctx) {
        String nodeId = ctx.getCurrentNodeId();
        if (nodeId == null || !graph.hasNode(nodeId)) {
            log.warn("无效的节点ID: {}, 重置为START", nodeId);
            ctx.setCurrentNodeId("START");
            nodeId = "START";
        }
        Node node = graph.getNode(nodeId);
        NodeResult result = node.execute(ctx);
        ctx.setCurrentNodeId(result.nextNodeId());
        log.info("节点执行: {} → {}", nodeId, result.nextNodeId());
        return result;
    }

    public boolean isFinished(InvestigationContext ctx) {
        return Graph.isEnd(ctx.getCurrentNodeId());
    }
}
