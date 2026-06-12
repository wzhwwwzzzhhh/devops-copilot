package com.devboss.llm;

import com.devboss.entity.ModelConfig;
import com.devboss.service.ModelConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 聊天服务类
 * 从数据库获取当前激活的模型配置，支持 Ollama 和 OpenAI 兼容接口（DeepSeek / 通义千问等）。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ModelConfigService modelConfigService;

    public ChatService(RestTemplate restTemplate, ObjectMapper objectMapper,
                       ModelConfigService modelConfigService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.modelConfigService = modelConfigService;
    }

    /**
     * 同步调用 LLM
     * @param prompt 用户提示词
     * @return LLM 响应文本，LLM 不可用时返回 __FALLBACK__
     */
    public String chat(String prompt) {
        ModelConfig current = modelConfigService.getCurrentChatModel();
        if (current == null) {
            log.warn("未配置当前模型，请在前端设置 → 系统设置 → 模型配置 中添加并激活一个模型");
            return "__FALLBACK__";
        }

        try {
            String response;
            if ("ollama".equalsIgnoreCase(current.getProvider())) {
                response = callOllama(current, prompt);
            } else {
                response = callOpenAICompatible(current, prompt);
            }

            JsonNode root = objectMapper.readTree(response);
            String content = extractContent(current.getProvider(), root);
            log.info("LLM 调用成功: provider={}, model={}, 响应长度={}",
                    current.getProvider(), current.getModelName(), content.length());
            return content;

        } catch (Exception e) {
            log.warn("LLM 调用失败: provider={}, model={}, error={}",
                    current.getProvider(), current.getModelName(), e.getMessage());
            return "__FALLBACK__";
        }
    }

    /**
     * 调用 Ollama /api/chat 接口
     */
    private String callOllama(ModelConfig config, String prompt) {
        String apiUrl = config.getBaseUrl().replaceAll("/$", "") + "/api/chat";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getModelName());
        body.put("stream", false);
        body.putObject("options").put("temperature", 0.7);

        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", prompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<ObjectNode> request = new HttpEntity<>(body, headers);
        return restTemplate.postForObject(apiUrl, request, String.class);
    }

    /**
     * 调用 OpenAI 兼容接口 /v1/chat/completions
     * 适用于 DeepSeek、通义千问、OpenAI 等
     */
    private String callOpenAICompatible(ModelConfig config, String prompt) {
        String apiUrl = config.getBaseUrl().replaceAll("/$", "") + "/v1/chat/completions";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", config.getModelName());
        body.put("stream", false);
        body.put("temperature", 0.7);

        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "user").put("content", prompt);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            headers.setBearerAuth(config.getApiKey());
        }

        HttpEntity<ObjectNode> request = new HttpEntity<>(body, headers);
        return restTemplate.postForObject(apiUrl, request, String.class);
    }

    /**
     * 从响应中提取文本内容
     */
    private String extractContent(String provider, JsonNode root) {
        if ("ollama".equalsIgnoreCase(provider)) {
            // Ollama 响应结构: {"message":{"role":"assistant","content":"..."}}
            return root.path("message").path("content").asText("");
        } else {
            // OpenAI 兼容响应结构: {"choices":[{"message":{"content":"..."}}]}
            JsonNode choice = root.path("choices").get(0);
            if (choice != null) {
                return choice.path("message").path("content").asText("");
            }
            return "";
        }
    }
}
