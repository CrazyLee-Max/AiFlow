package com.dslg.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * MCP Server配置属性类
 * 
 * 用于绑定application.yml中的mcp配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "mcp")
public class McpProperties {
    
    /**
     * MCP Server配置
     */
    private Server server = new Server();
    
    @Data
    public static class Server {
        /**
         * MCP Server URL
         */
        private String url = "http://localhost:3000";
        
        /**
         * 连接超时时间（毫秒）
         */
        private int timeout = 30000;
        
        /**
         * API密钥
         */
        private String apiKey = "";
    }
}