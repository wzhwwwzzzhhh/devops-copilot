package com.devboss.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Ollama配置类
 * 用于读取和管理与Ollama服务相关的配置信息
 */
@Configuration
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {

    /** Ollama服务基础URL */
    private String baseUrl;
    /** 聊天模型配置 */
    private ModelConfig chat;
    /** 嵌入模型配置 */
    private ModelConfig embedding;
    /** 推理模型配置 */
    private ModelConfig reasoning;

    /**
     * 获取Ollama服务基础URL
     * @return 基础URL
     */
    public String baseUrl() {
        return baseUrl;
    }

    /**
     * 设置Ollama服务基础URL
     * @param baseUrl 基础URL
     */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 获取聊天模型配置
     * @return 聊天模型配置
     */
    public ModelConfig chat() {
        return chat;
    }

    /**
     * 设置聊天模型配置
     * @param chat 聊天模型配置
     */
    public void setChat(ModelConfig chat) {
        this.chat = chat;
    }

    /**
     * 获取嵌入模型配置
     * @return 嵌入模型配置
     */
    public ModelConfig embedding() {
        return embedding;
    }

    /**
     * 设置嵌入模型配置
     * @param embedding 嵌入模型配置
     */
    public void setEmbedding(ModelConfig embedding) {
        this.embedding = embedding;
    }

    /**
     * 获取推理模型配置
     * @return 推理模型配置
     */
    public ModelConfig reasoning() {
        return reasoning;
    }

    /**
     * 设置推理模型配置
     * @param reasoning 推理模型配置
     */
    public void setReasoning(ModelConfig reasoning) {
        this.reasoning = reasoning;
    }

    /**
     * 模型配置内部类
     * 包含模型的名称、温度和最大token数等配置
     */
    public static class ModelConfig {
        /** 模型名称 */
        private String model;
        /** 温度参数，控制生成文本的随机性 */
        private double temperature;
        /** 最大token数 */
        private int maxTokens;

        public String model() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double temperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int maxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }
    }
}
