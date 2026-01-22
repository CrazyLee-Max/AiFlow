package com.dslg.ai.model;

/**
 * AI模型提供者枚举
 * 
 * 定义系统支持的三种大模型：
 * - DeepSeek: 深度求索的大语言模型
 * - ChatGPT: OpenAI的GPT模型
 * - Ollama: 本地部署的开源模型
 */
public enum AIModelProvider {
    
    DEEPSEEK("deepseek", "DeepSeek Chat Model"),
    CHATGPT("chatgpt", "OpenAI ChatGPT Model"),
    OLLAMA("ollama", "Ollama Local Model");

    private final String code;
    private final String description;

    AIModelProvider(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据代码获取提供者
     */
    public static AIModelProvider fromCode(String code) {
        for (AIModelProvider provider : values()) {
            if (provider.code.equals(code)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown AI model provider: " + code);
    }
}