package com.devboss.llm;

/** 提示词模板：定义 AI 运维排查的场景化提示词 */
public class PromptTemplates {

    public static String investigationPrompt(String userMessage, String serviceName, String toolResults) {
        return investigationPrompt(userMessage, serviceName, toolResults, "");
    }

    public static String investigationPrompt(String userMessage, String serviceName, String toolResults, String conversationHistory) {
        String historySection = conversationHistory.isEmpty() ? "" :
                "\n【对话历史】\n" + conversationHistory + "\n---\n当前是新一轮排查，请基于已有信息继续。\n";
        return """
                你是一个专业的 DevOps 运维助手，负责排查生产环境故障。
                %s
                【用户报障】
                %s

                【涉及服务】
                %s

                【已采集数据】
                %s

                请根据以上数据进行分析：
                1. 定位根因（最可能的原因）
                2. 判断是否需要执行高危操作（扩容/回滚/重启等）
                3. 如果需要高危操作，请在回答中明确指出"扩容"、"回滚"或"重启"关键词

                注意：如果分析发现是慢 SQL 或资源瓶颈问题，建议执行扩容操作。

                ---
                最后，请将你分析中用到的主要指标数据以 ```json 代码块形式附在回答末尾，例如：
                ```json
                {"connection_pool": {"usage_percent": 3, "active": 6, "max": 151}, "status": "HEALTHY"}
                ```
                字段名用英文，不要包含上面示例之外多余内容。如果数据不完整可以省略部分字段。
                """.formatted(historySection, userMessage, serviceName, toolResults);
    }

    public static String reportPrompt(String userMessage, String toolResults, String analysis) {
        return reportPrompt(userMessage, toolResults, analysis, "");
    }

    public static String reportPrompt(String userMessage, String toolResults, String analysis, String conversationHistory) {
        String historySection = conversationHistory.isEmpty() ? "" :
                "\n【对话历史参考】\n" + conversationHistory + "\n";
        return """
                你是一个专业的 DevOps 运维助手，请根据以下排查过程生成一份简洁的排查报告。
                %s
                【报障信息】
                %s

                【排查过程】
                %s

                【分析结果】
                %s

                请组织成以下格式的报告：
                ## 故障排查报告
                ### 1. 故障现象
                ### 2. 排查过程
                ### 3. 根因分析
                ### 4. 处理措施
                ### 5. 后续建议
                ---
                最后，将报告中涉及的关键指标数据以 ```json 代码块附在末尾。
                """.formatted(historySection, userMessage, toolResults, analysis);
    }

    public static String healthCheckPrompt(String toolResults) {
        return healthCheckPrompt(toolResults, "");
    }

    public static String healthCheckPrompt(String toolResults, String conversationHistory) {
        String historySection = conversationHistory.isEmpty() ? "" :
                "\n【对话历史】\n" + conversationHistory + "\n---\n当前是新一轮健康巡检，请综合已有信息。\n";
        return """
                你是一个专业的 DevOps 运维助手，以下是健康巡检采集的数据：
                %s
                %s

                请分析各服务的健康状态，标记为 ✅ 健康 / ⚠️ 异常。
                对异常服务给出具体的异常说明和改进建议。
                ---
                最后，将关键指标数据以 ```json 代码块附在末尾。
                """.formatted(historySection, toolResults);
    }

    public static String directCommandPrompt(String userMessage) {
        return """
                你是一个 DevOps 运维助手，请直接回答以下指令：

                %s

                如果指令是查询信息，直接给出简洁的答案。
                如果指令是操作指令（扩容/回滚等），请确认后再执行。
                """.formatted(userMessage);
    }
}
