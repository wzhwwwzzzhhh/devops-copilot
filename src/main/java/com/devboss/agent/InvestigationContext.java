package com.devboss.agent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 调查上下文类
 * 用于存储和管理DevOps Copilot在处理用户请求过程中的状态和数据
 */
public class InvestigationContext {

    private String sessionId;
    private AgentStep currentStep;
    private String currentNodeId;
    private String userMessage;
    private String serviceName;
    private String analysisResult;
    private String report;
    private boolean awaitingApproval;
    private String pendingAction;
    private Map<String, String> actionParams;
    private final Map<String, Object> collectedData;
    private final List<String> toolCallLog;
    private final List<String> messages;
    private int retryCount;
    private static final int MAX_RETRIES = 3;

    public InvestigationContext() {
        this.currentStep = AgentStep.START;
        this.currentNodeId = null;
        this.collectedData = new HashMap<>();
        this.toolCallLog = new ArrayList<>();
        this.messages = new ArrayList<>();
        this.actionParams = new HashMap<>();
        this.retryCount = 0;
    }

    /**
     * 添加收集的数据
     * @param key 数据键
     * @param value 数据值
     */
    public void addCollectedData(String key, Object value) {
        collectedData.put(key, value);
    }

    /**
     * 获取收集的数据
     * @param key 数据键
     * @return 数据值
     */
    @SuppressWarnings("unchecked")
    public <T> T getCollectedData(String key) {
        return (T) collectedData.get(key);
    }

    /**
     * 记录工具调用
     * @param toolName 工具名称
     * @param result 调用结果
     */
    public void logToolCall(String toolName, String result) {
        toolCallLog.add(String.format("[%s] %s", toolName, result));
    }

    /**
     * 获取工具调用日志摘要
     * @return 工具调用日志字符串
     */
    public String getToolCallLogSummary() {
        return String.join("\n", toolCallLog);
    }

    /**
     * 添加消息到历史记录
     * @param role 角色（user/assistant）
     * @param content 消息内容
     */
    public void addMessage(String role, String content) {
        messages.add(String.format("%s: %s", role, content));
    }

    /**
     * 增加重试次数
     */
    public void incrementRetry() {
        this.retryCount++;
    }

    /**
     * 检查是否超过最大重试次数
     * @return 是否超过最大重试次数
     */
    public boolean isMaxRetriesExceeded() {
        return retryCount >= MAX_RETRIES;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void resetRetryCount() {
        this.retryCount = 0;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public AgentStep getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(AgentStep currentStep) {
        this.currentStep = currentStep;
    }

    public String getCurrentNodeId() {
        return currentNodeId;
    }

    public void setCurrentNodeId(String currentNodeId) {
        this.currentNodeId = currentNodeId;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public void setUserMessage(String userMessage) {
        this.userMessage = userMessage;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getAnalysisResult() {
        return analysisResult;
    }

    public void setAnalysisResult(String analysisResult) {
        this.analysisResult = analysisResult;
    }

    public String getReport() {
        return report;
    }

    public void setReport(String report) {
        this.report = report;
    }

    public boolean isAwaitingApproval() {
        return awaitingApproval;
    }

    public void setAwaitingApproval(boolean awaitingApproval) {
        this.awaitingApproval = awaitingApproval;
    }

    public String getPendingAction() {
        return pendingAction;
    }

    public void setPendingAction(String pendingAction) {
        this.pendingAction = pendingAction;
    }

    public Map<String, String> getActionParams() {
        return actionParams;
    }

    public void setActionParams(Map<String, String> actionParams) {
        this.actionParams = actionParams;
    }

    public List<String> getMessages() {
        return messages;
    }
}
