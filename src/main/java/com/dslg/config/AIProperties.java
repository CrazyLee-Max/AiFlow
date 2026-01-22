package com.dslg.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI模型配置属性类
 * 
 * 用于绑定application.yml中的ai配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AIProperties {
    
    /**
     * 默认AI提供商
     */
    private String defaultProvider = "deepseek";
    
    /**
     * AI模型配置
     */
    private Models models = new Models();
    
    @Data
    public static class Models {
        /**
         * DeepSeek配置
         */
        private ModelConfig deepseek = new ModelConfig();
        
        /**
         * ChatGPT配置
         */
        private ModelConfig chatgpt = new ModelConfig();
        
        /**
         * Ollama配置
         */
        private ModelConfig ollama = new ModelConfig();
    }
    
    @Data
    public static class ModelConfig {
        /**
         * API密钥
         */
        private String apiKey = "";
        
        /**
         * 基础URL
         */
        private String baseUrl = "";
        
        /**
         * 模型名称
         */
        private String modelName = "";
        
        /**
         * 超时时间（毫秒）
         */
        private int timeout = 60000;
        
        /**
         * 最大令牌数
         */
        private int maxTokens = 4000;
        
        /**
         * 温度参数
         */
        private double temperature = 0.7;
    }
}