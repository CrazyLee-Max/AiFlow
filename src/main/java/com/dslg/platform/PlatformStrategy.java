package com.dslg.platform;

import com.dslg.service.McpServerService;
import com.dslg.service.model.TaskDecompositionResult;
import com.dslg.service.model.TaskStep;

/**
 * 平台策略接口
 * 
 * 定义不同低代码平台的任务分解和MCP调用策略
 * 每个平台可以有自己的prompt模板、DSL格式和MCP Server配置
 */
public interface PlatformStrategy {

    /**
     * 获取平台类型
     * 
     * @return 平台类型
     */
    PlatformType getPlatformType();

    /**
     * 构建任务分解的提示词
     * 
     * @param taskDescription 任务描述
     * @param context 上下文信息
     * @return 针对该平台优化的提示词
     */
    String buildTaskDecompositionPrompt(String taskDescription, String context);

    /**
     * 构建基于确认步骤生成MCP请求的提示词
     * 
     * @param userInput 用户输入
     * @param taskSteps 确认的任务步骤
     * @param knowledgeContext 知识上下文
     * @return 针对该平台优化的提示词
     */
    String buildMcpGenerationPrompt(
            String userInput, 
            java.util.List<TaskStep> taskSteps, 
            String knowledgeContext);

    /**
     * 处理任务分解结果
     * 
     * @param rawResponse AI模型的原始响应
     * @return 处理后的任务分解结果
     */
    TaskDecompositionResult processTaskDecompositionResponse(String rawResponse);

    /**
     * 构建标准MCP JSON-RPC 2.0请求
     * 
     * @param userInput 用户输入
     * @param taskSteps 任务步骤
     * @param knowledgeContext 知识上下文
     * @return 标准MCP JSON-RPC 2.0请求Map
     */
    java.util.Map<String, Object> buildStandardMcpRequest(
            String userInput, 
            java.util.List<TaskStep> taskSteps, 
            String knowledgeContext);

    /**
     * 直接使用AI响应构建MCP JSON-RPC 2.0请求（统一方法）
     * 
     * @param userInput 用户输入
     * @param aiResponse AI模型的原始响应
     * @param knowledgeContext 知识上下文
     * @return 标准MCP JSON-RPC 2.0请求Map
     */
    java.util.Map<String, Object> buildMcpRequestFromAiResponse(
            String userInput, 
            String aiResponse, 
            String knowledgeContext);

    /**
     * 处理MCP Server响应
     * 
     * @param response MCP Server的原始响应
     * @return 处理后的DSL生成结果
     */
    McpServerService.DslGenerationResult processMcpResponse(McpServerService.DslGenerationResponse response);

    /**
     * 获取MCP Server URL
     * 
     * @return MCP Server的URL地址
     */
    String getMcpServerUrl();

    /**
     * 获取支持的DSL格式
     * 
     * @return DSL格式列表
     */
    java.util.List<String> getSupportedDslFormats();

    /**
     * 获取默认DSL格式
     * 
     * @return 默认的DSL格式
     */
    String getDefaultDslFormat();

    /**
     * 验证平台特定的参数
     * 
     * @param parameters 参数映射
     * @return 验证结果，true表示有效
     */
    boolean validatePlatformParameters(java.util.Map<String, Object> parameters);

    /**
     * 获取平台特定的元数据
     * 
     * @return 平台元数据
     */
    java.util.Map<String, Object> getPlatformMetadata();
}