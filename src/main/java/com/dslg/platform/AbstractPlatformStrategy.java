package com.dslg.platform;

import com.dslg.service.model.TaskDecompositionResult;
import com.dslg.service.model.TaskStep;
import com.dslg.service.model.ControlFlow;
import com.dslg.service.McpServerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 抽象平台策略类
 * 
 * 提供平台策略的通用实现，子类可以重写特定方法来定制行为
 */
@Slf4j
public abstract class AbstractPlatformStrategy implements PlatformStrategy {

    protected final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 构建基于确认步骤生成MCP请求的提示词（默认实现）
     */
    @Override
    public String buildMcpGenerationPrompt(
            String userInput, 
            List<TaskStep> taskSteps, 
            String knowledgeContext) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的工作流设计专家。请根据用户需求和确认的步骤，生成一个完整的MCP请求。\n\n");
        prompt.append("用户需求：").append(userInput).append("\n\n");
        
        if (knowledgeContext != null && !knowledgeContext.isEmpty()) {
            prompt.append("知识库上下文：\n").append(knowledgeContext).append("\n\n");
        }
        
        prompt.append("确认的任务步骤：\n");
        if (taskSteps != null) {
            for (TaskStep step : taskSteps) {
                prompt.append("- ").append(step.getName()).append(": ").append(step.getDescription()).append("\n");
            }
        }
        prompt.append("\n");
        
        prompt.append("请生成符合JSON-RPC 2.0标准的MCP请求JSON。\n");
        return prompt.toString();
    }

    @Override
    public TaskDecompositionResult processTaskDecompositionResponse(String rawResponse) {
        try {
            String jsonContent = extractJsonFromResponse(rawResponse);
            JsonNode rootNode = objectMapper.readTree(jsonContent);
            
            List<TaskStep> steps = new ArrayList<>();
            ControlFlow controlFlow = ControlFlow.builder()
                    .hasConditions(false)
                    .hasLoops(false)
                    .hasParallel(false)
                    .build();
            
            String summary = "";
            
            if (rootNode.has("steps") && rootNode.get("steps").isArray()) {
                for (JsonNode stepNode : rootNode.get("steps")) {
                    TaskStep step = parseTaskStep(stepNode);
                    if (step != null) {
                        steps.add(step);
                    }
                }
            }
            
            if (rootNode.has("summary")) {
                summary = rootNode.get("summary").asText();
            }
            
            if (rootNode.has("controlFlow")) {
                JsonNode controlFlowNode = rootNode.get("controlFlow");
                controlFlow = ControlFlow.builder()
                        .hasConditions(controlFlowNode.has("hasConditions") && controlFlowNode.get("hasConditions").asBoolean())
                        .hasLoops(controlFlowNode.has("hasLoops") && controlFlowNode.get("hasLoops").asBoolean())
                        .hasParallel(controlFlowNode.has("hasParallel") && controlFlowNode.get("hasParallel").asBoolean())
                        .build();
            }
            
            return TaskDecompositionResult.builder()
                    .success(true)
                    .steps(steps)
                    .controlFlow(controlFlow)
                    .summary(summary)
                    .build();
                    
        } catch (Exception e) {
            log.error("解析任务分解响应失败: {}", e.getMessage(), e);
            return TaskDecompositionResult.builder()
                    .success(false)
                    .errorMessage("解析任务分解响应失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 构建标准MCP JSON-RPC 2.0请求（默认实现）
     * 子类应该重写此方法以提供平台特定的MCP请求构建逻辑
     * 
     * @param userInput 用户输入
     * @param taskSteps 任务步骤
     * @param knowledgeContext 知识上下文
     * @return 标准MCP JSON-RPC 2.0请求Map
     */
    @Override
    public Map<String, Object> buildStandardMcpRequest(
            String userInput, 
            List<TaskStep> taskSteps, 
            String knowledgeContext) {
        
        // 默认实现 - 构建通用的MCP请求
        Map<String, Object> mcpRequest = new HashMap<>();
        mcpRequest.put("jsonrpc", "2.0");
        mcpRequest.put("id", System.currentTimeMillis());
        mcpRequest.put("method", "tools/call");
        
        Map<String, Object> params = new HashMap<>();
        params.put("name", "generate_workflow");
        
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("userInput", userInput);
        arguments.put("knowledgeContext", knowledgeContext);
        arguments.put("platform", getPlatformType().name());
        
        if (taskSteps != null && !taskSteps.isEmpty()) {
            List<Map<String, Object>> tasks = new ArrayList<>();
            for (TaskStep step : taskSteps) {
                Map<String, Object> task = new HashMap<>();
                task.put("name", step.getName());
                task.put("type", step.getType());
                task.put("description", step.getDescription());
                tasks.add(task);
            }
            arguments.put("tasks", tasks);
        }
        
        params.put("arguments", arguments);
        mcpRequest.put("params", params);
        
        return mcpRequest;
    }

    /**
     * 统一的AI响应处理方法的默认实现
     * 尝试直接使用AI响应作为MCP请求，如果失败则解析为TaskStep后使用标准方法
     */
    @Override
    public Map<String, Object> buildMcpRequestFromAiResponse(
            String userInput, 
            String aiResponse, 
            String knowledgeContext) {
        
        log.info("使用默认的AI响应处理方法");
        
        try {
            // 尝试解析AI响应为完整的MCP JSON-RPC 2.0格式
            String jsonContent = extractJsonFromResponse(aiResponse);
            JsonNode responseNode = objectMapper.readTree(jsonContent);
            
            // 检查是否已经是完整的MCP JSON-RPC 2.0格式
            if (responseNode.has("jsonrpc") && responseNode.has("method") && responseNode.has("params")) {
                log.info("检测到完整的MCP JSON-RPC 2.0格式，直接使用");
                Map<String, Object> mcpRequest = objectMapper.convertValue(responseNode, Map.class);
                
                // 添加知识上下文到arguments中（如果有）
                if (knowledgeContext != null && !knowledgeContext.trim().isEmpty()) {
                    Map<String, Object> params = (Map<String, Object>) mcpRequest.get("params");
                    if (params != null) {
                        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");
                        if (arguments != null) {
                            arguments.put("knowledgeContext", knowledgeContext);
                        }
                    }
                }
                
                return mcpRequest;
            }
            
            // 如果不是完整MCP格式，尝试解析为TaskStep格式
            log.info("AI响应不是完整MCP格式，尝试解析为TaskStep格式");
            TaskDecompositionResult taskResult = processTaskDecompositionResponse(aiResponse);
            
            if (taskResult != null && taskResult.getSteps() != null && !taskResult.getSteps().isEmpty()) {
                // 使用解析出的TaskStep构建标准MCP请求
                return buildStandardMcpRequest(userInput, taskResult.getSteps(), knowledgeContext);
            }
            
        } catch (Exception e) {
            log.error("处理AI响应失败: {}", e.getMessage(), e);
        }
        
        // 如果所有方法都失败，返回一个基本的MCP请求
        log.warn("无法解析AI响应，使用基本MCP请求格式");
        return buildStandardMcpRequest(userInput, new ArrayList<>(), knowledgeContext);
    }

    @Override
    public McpServerService.DslGenerationResult processMcpResponse(McpServerService.DslGenerationResponse response) {
        return McpServerService.DslGenerationResult.builder()
                .success(response.isSuccess())
                .dslContent(response.getDslContent())
                .dslFormat(response.getDslFormat())
                .metadata(response.getMetadata())
                .generationTime(LocalDateTime.now())
                .error(response.getError())
                .build();
    }

    @Override
    public String getMcpServerUrl() {
        return getPlatformType().getDefaultMcpServerUrl();
    }

    @Override
    public List<String> getSupportedDslFormats() {
        return Arrays.asList("json", "yaml", "xml");
    }

    @Override
    public String getDefaultDslFormat() {
        return "json";
    }

    @Override
    public boolean validatePlatformParameters(Map<String, Object> parameters) {
        // 默认实现：所有参数都有效
        return true;
    }

    @Override
    public Map<String, Object> getPlatformMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("platform", getPlatformType().getCode());
        metadata.put("platformName", getPlatformType().getDisplayName());
        metadata.put("supportedFormats", getSupportedDslFormats());
        metadata.put("defaultFormat", getDefaultDslFormat());
        return metadata;
    }

    /**
     * 从响应中提取JSON内容
     */
    protected String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "{}";
        }
        
        // 查找JSON开始和结束标记
        int jsonStart = response.indexOf('{');
        int jsonEnd = response.lastIndexOf('}');
        
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd + 1);
        }
        
        return response.trim();
    }

    /**
     * 解析任务步骤
     */
    protected TaskStep parseTaskStep(JsonNode stepNode) {
        try {
            TaskStep.TaskStepBuilder builder = TaskStep.builder();
            
            if (stepNode.has("id")) {
                builder.id(stepNode.get("id").asText());
            }
            if (stepNode.has("name")) {
                builder.name(stepNode.get("name").asText());
            }
            if (stepNode.has("description")) {
                builder.description(stepNode.get("description").asText());
            }
            if (stepNode.has("type")) {
                builder.type(stepNode.get("type").asText());
            }
            if (stepNode.has("dependencies") && stepNode.get("dependencies").isArray()) {
                List<String> dependencies = new ArrayList<>();
                for (JsonNode dep : stepNode.get("dependencies")) {
                    dependencies.add(dep.asText());
                }
                builder.dependencies(dependencies);
            }
            if (stepNode.has("isParallel")) {
                builder.isParallel(stepNode.get("isParallel").asBoolean());
            }
            if (stepNode.has("estimatedTime")) {
                builder.estimatedTime(stepNode.get("estimatedTime").asText());
            }
            if (stepNode.has("priority")) {
                builder.priority(stepNode.get("priority").asText());
            }
            
            return builder.build();
        } catch (Exception e) {
            log.error("解析任务步骤失败: {}", e.getMessage(), e);
            return null;
        }
    }
}