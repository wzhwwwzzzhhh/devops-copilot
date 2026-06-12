package com.devboss.agent;

/**
 * Agent 执行步骤枚举，定义运维助手在故障排查、健康检查等场景下的所有工作流节点。
 */
public enum AgentStep {
    /** 初始状态，Agent 启动 */
    START,
    /** 分析故障/事件，确定排查方向 */
    ANALYZE_INCIDENT,
    /** 查询监控指标（CPU、内存、QPS 等） */
    QUERY_METRICS,
    /** 查询服务日志 */
    QUERY_LOGS,
    /** 查询分布式链路追踪 */
    QUERY_TRACES,
    /** 查询数据库状态（慢查询、连接数等） */
    QUERY_DATABASE,
    /** 查询部署历史记录 */
    QUERY_DEPLOYMENTS,
    /** RAG 知识库检索，获取相关历史案例或文档 */
    RAG_KNOWLEDGE_RETRIEVAL,
    /** LLM 推理分析，综合多源信息给出诊断结论 */
    LLM_REASONING,
    /** 等待人工审批（执行高风险操作前） */
    AWAITING_APPROVAL,
    /** 执行具体修复/运维操作 */
    EXECUTE_ACTION,
    /** 生成排查报告 */
    GENERATE_REPORT,
    /** 流程完成 */
    COMPLETED,
    /** 健康检查：开始 */
    HEALTH_CHECK_START,
    /** 健康检查：指标采集 */
    HEALTH_CHECK_METRICS,
    /** 健康检查：日志检查 */
    HEALTH_CHECK_LOGS,
    /** 健康检查：数据库检查 */
    HEALTH_CHECK_DATABASE,
    /** 健康检查：部署状态检查 */
    HEALTH_CHECK_DEPLOYMENTS,
    /** 健康检查：综合分析 */
    HEALTH_CHECK_ANALYZE,
    /** 直接问答模式，跳过故障诊断流程 */
    DIRECT_QUERY,
    /** 流程失败/异常终止 */
    FAILED
}
