package com.dslg.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Map;

/**
 * MCP Server调用服务
 * 
 * 负责与MCP Server通信，生成工作流DSL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerService {

    private final RestTemplate restTemplate;

    @Value("${mcp.server.timeout:30000}")
    private int timeout;

    @Value("${mcp.server.api-key:}")
    private String apiKey;

    /**
     * 发送标准MCP请求
     * 
     * @param mcpRequest MCP JSON-RPC 2.0请求
     * @param mcpServerUrl MCP服务器URL
     * @param platformStrategy 平台策略，用于处理特定平台的响应格式
     * @return DSL生成结果
     */
    public DslGenerationResult sendStandardMcpRequest(Map<String, Object> mcpRequest, String mcpServerUrl, com.dslg.platform.PlatformStrategy platformStrategy) {
        log.info("发送标准MCP请求到: {}", mcpServerUrl);
        
        try {
            // 验证参数
            if (mcpRequest == null || mcpRequest.isEmpty()) {
                return createErrorResult("MCP请求不能为空");
            }
            
            if (mcpServerUrl == null || mcpServerUrl.trim().isEmpty()) {
                return createErrorResult("MCP服务器URL不能为空");
            }
            
            // 创建HTTP请求
            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(mcpRequest, headers);
            
            log.debug("发送MCP请求: {}", mcpRequest);
            
            // 发送请求，接收JSON-RPC 2.0响应
            ResponseEntity<Map> response = restTemplate.exchange(
                mcpServerUrl,
                HttpMethod.POST,
                requestEntity,
                Map.class
            );
            
            // 处理JSON-RPC 2.0响应
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                // 检查是否有错误
                Object error = responseBody.get("error");
                if (error != null) {
                    log.error("MCP服务器返回错误: {}", error);
                    return createErrorResult("MCP服务器错误: " + error.toString());
                }
                
                // 创建DslGenerationResponse对象，包含原始响应数据
                DslGenerationResponse dslResponse = new DslGenerationResponse();
                dslResponse.setSuccess(true);
                // 不在这里设置dslContent，让平台策略从原始响应中提取
                dslResponse.setMetadata(Map.of("mcpResponse", responseBody));
                
                // 使用平台策略处理响应
                if (platformStrategy != null) {
                    log.info("使用平台策略处理MCP响应: {}", platformStrategy.getPlatformType());
                    return platformStrategy.processMcpResponse(dslResponse);
                } else {
                    // 如果没有平台策略，返回错误（现在要求必须提供平台策略）
                    log.error("未提供平台策略，无法处理MCP响应");
                    return createErrorResult("未提供平台策略，无法处理MCP响应");
                }
                
            } else {
                log.error("MCP请求失败，HTTP状态码: {}", response.getStatusCode());
                return createErrorResult("HTTP请求失败，状态码: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("调用MCP服务器失败: {}", e.getMessage(), e);
            return createErrorResult("调用MCP服务器失败: " + e.getMessage());
        }
    }


    /**
     * 创建HTTP请求头
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("User-Agent", "DSLG-Client/1.0");
        
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.set("Authorization", "Bearer " + apiKey);
        }
        
        return headers;
    }

    /**
     * 创建错误结果
     */
    private DslGenerationResult createErrorResult(String errorMessage) {
        return DslGenerationResult.builder()
                .success(false)
                .error(errorMessage)
                .generationTime(LocalDateTime.now())
                .build();
    }

    // DTO类定义

    @Data
    public static class DslGenerationRequest {
        private String userInput;
        private List<TaskStep> taskSteps;
        private String knowledgeContext;
        private String preferredFormat;
        private Map<String, Object> parameters;
        private Map<String, Object> metadata;
    }

    @Data
    public static class TaskStep {
        private String id;
        private String name;
        private String description;
        private String type;
        private Map<String, Object> parameters;
        private List<String> dependencies;
        private Map<String, Object> metadata;
    }

    @Data
    public static class DslGenerationResponse {
        private String dslContent;
        private String dslFormat;
        private Map<String, Object> metadata;
        private boolean success;
        private String error;
    }

    @Data
    @lombok.Builder
    public static class DslGenerationResult {
        private boolean success;
        private String dslContent;
        private String dslFormat;
        private Map<String, Object> metadata;
        private LocalDateTime generationTime;
        private String error;
    }

    @Data
    public static class DslValidationResponse {
        private boolean valid;
        private List<String> errors;
        private List<String> warnings;
        private List<String> suggestions;
    }

    @Data
    @lombok.Builder
    public static class DslValidationResult {
        private boolean valid;
        private List<String> errors;
        private List<String> warnings;
        private List<String> suggestions;
        private LocalDateTime validationTime;
    }

    @Data
    public static class DslFormatsResponse {
        private List<String> formats;
    }
}