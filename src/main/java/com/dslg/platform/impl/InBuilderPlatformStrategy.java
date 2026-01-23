package com.dslg.platform.impl;

import com.dslg.platform.AbstractPlatformStrategy;
import com.dslg.platform.PlatformType;
import com.dslg.platform.impl.inbuilder.InBuilderPromptBuilder;
import com.dslg.platform.impl.inbuilder.InBuilderResponseParser;
import com.dslg.platform.impl.inbuilder.InBuilderVariableReferenceFixer;
import com.dslg.service.McpServerService;
import com.dslg.service.NodeDefinitionService;
import com.dslg.service.model.Edge;
import com.dslg.service.model.Node;
import com.dslg.service.model.TaskStep;
import com.dslg.service.model.WorkflowStructure;
import com.dslg.validator.VariableIdValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * InBuilder 平台策略实现（重构版）
 * 
 * 职责：
 * - 平台类型标识
 * - MCP 响应处理
 * - 协调各个服务组件
 */
@Slf4j
@Component
public class InBuilderPlatformStrategy extends AbstractPlatformStrategy {

    @Value("${mcp.inbuilder.url:http://localhost:3001/mcp}")
    private String mcpServerUrl;
    
    private final NodeDefinitionService nodeDefinitionService;
    private final VariableIdValidator variableIdValidator;
    private final InBuilderPromptBuilder promptBuilder;
    private final InBuilderResponseParser responseParser;
    private final InBuilderVariableReferenceFixer variableFixer;
    
    public InBuilderPlatformStrategy(
            NodeDefinitionService nodeDefinitionService,
            VariableIdValidator variableIdValidator,
            InBuilderPromptBuilder promptBuilder,
            InBuilderResponseParser responseParser,
            InBuilderVariableReferenceFixer variableFixer) {
        this.nodeDefinitionService = nodeDefinitionService;
        this.variableIdValidator = variableIdValidator;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.variableFixer = variableFixer;
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.IN_BUILDER;
    }

    /**
     * 处理 InBuilder MCP Server 的响应格式
     */
    @Override
    public McpServerService.DslGenerationResult processMcpResponse(McpServerService.DslGenerationResponse response) {
        log.info("=== InBuilder平台MCP响应处理开始 ===");
        
        if (response == null) {
            log.error("MCP响应为空");
            return createErrorResult("MCP响应为空");
        }
        
        try {
            if (!response.isSuccess()) {
                log.error("MCP响应失败: {}", response.getError());
                return createErrorResult("MCP响应失败: " + response.getError());
            }
            
            // 从metadata中获取原始MCP响应
            Map<String, Object> metadata = response.getMetadata();
            if (metadata != null && metadata.containsKey("mcpResponse")) {
                Map<String, Object> mcpResponse = (Map<String, Object>) metadata.get("mcpResponse");
                
                // 解析 InBuilder Server 响应格式
                if (mcpResponse.containsKey("result")) {
                    Object resultObj = mcpResponse.get("result");
                    
                    if (resultObj instanceof Map) {
                        Map<String, Object> result = (Map<String, Object>) resultObj;
                        
                        // 检查是否有 content 字段 (MCP 规范)
                        if (result.containsKey("content")) {
                            List<Map<String, Object>> contentList = (List<Map<String, Object>>) result.get("content");
                            if (contentList != null && !contentList.isEmpty()) {
                                Map<String, Object> contentItem = contentList.get(0);
                                if (contentItem.containsKey("text")) {
                                    String jsonContent = (String) contentItem.get("text");
                                    
                                    log.info("成功提取 InBuilder JSON 内容，长度: {} 字符", jsonContent.length());
                                    
                                    Map<String, Object> resultMetadata = new HashMap<>();
                                    resultMetadata.put("source", "InBuilder");
                                    
                                    return McpServerService.DslGenerationResult.builder()
                                            .success(true)
                                            .dslContent(jsonContent)
                                            .dslFormat("json")
                                            .metadata(resultMetadata)
                                            .generationTime(LocalDateTime.now())
                                            .build();
                                }
                            }
                        }
                        
                        // 如果直接返回了对象
                        ObjectMapper mapper = new ObjectMapper();
                        String jsonContent = mapper.writeValueAsString(result);
                        return McpServerService.DslGenerationResult.builder()
                                .success(true)
                                .dslContent(jsonContent)
                                .dslFormat("json")
                                .metadata(new HashMap<>())
                                .generationTime(LocalDateTime.now())
                                .build();
                    }
                }
            }
            
            return createErrorResult("无法解析 MCP 响应格式");
            
        } catch (Exception e) {
            log.error("处理 InBuilder MCP 响应时发生异常", e);
            return createErrorResult("处理 InBuilder MCP 响应时发生异常: " + e.getMessage());
        }
    }
    
    private McpServerService.DslGenerationResult createErrorResult(String errorMessage) {
        return McpServerService.DslGenerationResult.builder()
                .success(false)
                .error(errorMessage)
                .generationTime(LocalDateTime.now())
                .build();
    }

    @Override
    public Map<String, Object> buildMcpRequestFromAiResponse(
            String userInput, 
            String aiResponse, 
            String knowledgeContext) {
        
        log.info("使用InBuilder特定的AI响应处理方法");
        
        try {
            // 提取JSON内容（使用父类方法）
            String jsonContent = extractJsonFromResponse(aiResponse);
            com.fasterxml.jackson.databind.JsonNode responseNode = objectMapper.readTree(jsonContent);
            
            // 1. 如果AI返回的是完整的MCP JSON-RPC 2.0格式
            if (responseNode.has("jsonrpc") && responseNode.has("method") && responseNode.has("params")) {
                log.info("检测到完整的MCP JSON-RPC 2.0格式，直接使用");
                return objectMapper.convertValue(responseNode, Map.class);
            }
            
            // 2. 如果AI只返回了arguments部分（包含nodes和edges）
            if (responseNode.has("nodes") || responseNode.has("edges")) {
                log.info("检测到简化的参数格式，自动包装为MCP请求");
                
                Map<String, Object> mcpRequest = new HashMap<>();
                mcpRequest.put("jsonrpc", "2.0");
                mcpRequest.put("id", System.currentTimeMillis());
                mcpRequest.put("method", "tools/call");
                
                Map<String, Object> params = new HashMap<>();
                params.put("name", "create_complete_workflow");
                
                // 将AI返回的内容作为arguments
                Map<String, Object> arguments = objectMapper.convertValue(responseNode, Map.class);
                
                // 确保必要字段存在
                if (!arguments.containsKey("name")) {
                    arguments.put("name", "Generated Workflow");
                }
                if (!arguments.containsKey("description")) {
                    arguments.put("description", "Generated from user input: " + userInput);
                }
                
                params.put("arguments", arguments);
                mcpRequest.put("params", params);
                
                return mcpRequest;
            }
            
            // 3. 降级：尝试使用通用解析
            return super.buildMcpRequestFromAiResponse(userInput, aiResponse, knowledgeContext);
            
        } catch (Exception e) {
            log.error("处理InBuilder AI响应失败: {}", e.getMessage(), e);
            return super.buildMcpRequestFromAiResponse(userInput, aiResponse, knowledgeContext);
        }
    }

    @Override
    public String buildMcpGenerationPrompt(
            String userInput, 
            List<TaskStep> taskSteps, 
            String knowledgeContext) {
        return promptBuilder.buildMcpGenerationPrompt(userInput, taskSteps, knowledgeContext);
    }

    /**
     * 解析节点和边，并自动修复变量引用
     */
    public WorkflowStructure parseNodesAndEdges(String aiResponse) {
        log.info("=== 开始解析和修复工作流 ===");
        
        // 1. 解析
        WorkflowStructure structure = responseParser.parseNodesAndEdges(aiResponse);
        
        if (!structure.isSuccess()) {
            return structure;
        }
        
        // 2. 验证
        Map<String, Object> validation = validateNodesAndEdges(structure.getNodes(), structure.getEdges());
        
        if (!(Boolean) validation.get("valid")) {
            structure.setSuccess(false);
            structure.setError("验证失败: " + validation.get("errors"));
            return structure;
        }
        
        // 3. 修复变量引用（关键步骤）
        try {
            log.info("开始修复变量引用");
            List<Node> fixedNodes = variableFixer.fixVariableReferences(
                structure.getNodes(), structure.getEdges());
            structure.setNodes(fixedNodes);
            log.info("变量引用修复完成");
        } catch (Exception e) {
            log.error("修复变量引用时发生异常", e);
            structure.setSuccess(false);
            structure.setError("修复变量引用失败: " + e.getMessage());
        }
        
        return structure;
    }
    
    /**
     * 验证节点和边的有效性
     */
    public Map<String, Object> validateNodesAndEdges(List<Node> nodes, List<Edge> edges) {
        log.info("=== 开始验证节点和边 ===");
        
        Map<String, Object> result = new HashMap<>();
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // 收集所有节点 ID
        Set<String> nodeIds = new HashSet<>();
        boolean hasStart = false;
        boolean hasEnd = false;
        
        // 验证节点
        for (Node node : nodes) {
            String nodeId = node.getId();
            String nodeKind = node.getKind();
            
            if (nodeId == null || nodeId.isEmpty()) {
                errors.add("发现节点缺少 id 字段");
                continue;
            }
            
            if (nodeIds.contains(nodeId)) {
                errors.add("节点 ID 重复: " + nodeId);
            }
            nodeIds.add(nodeId);
            
            if (nodeKind == null || nodeKind.isEmpty()) {
                errors.add("节点 " + nodeId + " 缺少 kind 字段");
            } else {
                if (!nodeDefinitionService.isNodeTypeSupported(nodeKind)) {
                    errors.add("节点 " + nodeId + " 使用了不支持的类型: " + nodeKind);
                }
                
                if ("start".equals(nodeKind) || "deviceEventListen".equals(nodeKind)) {
                    hasStart = true;
                }
                if ("end".equals(nodeKind)) {
                    hasEnd = true;
                }
            }
        }
        
        if (!hasStart) {
            errors.add("流程缺少开始节点 (start 或 deviceEventListen)");
        }
        if (!hasEnd) {
            errors.add("流程至少需要一个 end 节点");
        }
        
        // 验证边
        for (Edge edge : edges) {
            String sourceNodeId = edge.getSourceNodeId();
            String targetNodeId = edge.getTargetNodeId();
            
            if (sourceNodeId == null || sourceNodeId.isEmpty()) {
                errors.add("发现连线缺少 sourceNodeId 字段");
                continue;
            }
            if (targetNodeId == null || targetNodeId.isEmpty()) {
                errors.add("发现连线缺少 targetNodeId 字段");
                continue;
            }
            
            if (!nodeIds.contains(sourceNodeId)) {
                errors.add("连线引用了不存在的源节点: " + sourceNodeId);
            }
            if (!nodeIds.contains(targetNodeId)) {
                errors.add("连线引用了不存在的目标节点: " + targetNodeId);
            }
        }
        
        // 验证变量ID和variableId
        log.info("开始验证变量ID和variableId");
        VariableIdValidator.ValidationResult variableValidation = variableIdValidator.validateVariableIds(nodes);
        errors.addAll(variableValidation.getErrors());
        warnings.addAll(variableValidation.getWarnings());
        
        // 检查节点数量
        if (nodes.size() > 20) {
            warnings.add("节点数量 (" + nodes.size() + ") 超过建议的最大值 (20)");
        }
        
        boolean isValid = errors.isEmpty();
        result.put("valid", isValid);
        result.put("errors", errors);
        result.put("warnings", warnings);
        
        if (isValid) {
            log.info("验证通过: {} 个节点, {} 条连线", nodes.size(), edges.size());
        } else {
            log.warn("验证失败: 发现 {} 个错误", errors.size());
            errors.forEach(error -> log.warn("  - {}", error));
        }
        
        if (!warnings.isEmpty()) {
            log.info("发现 {} 个警告", warnings.size());
            warnings.forEach(warning -> log.info("  - {}", warning));
        }
        
        log.info("=== 节点和边验证完成 ===");
        
        return result;
    }

    @Override
    public String buildTaskDecompositionPrompt(String taskDescription, String context) {
        return promptBuilder.buildTaskDecompositionPrompt(taskDescription, context);
    }
    
    @Override
    public Map<String, Object> getPlatformMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("platform", "InBuilder");
        metadata.put("version", "1.0");
        metadata.put("specification", "InBuilder DSL");
        metadata.put("mcpServerPort", 3001);
        
        metadata.put("supportedElements", Arrays.asList(
            "start", "end", "variableDef", "batchAssignValue", "selector", "deviceEventListen"
        ));
        
        metadata.put("mcpTools", Arrays.asList(
            "create_workflow", "add_node", "add_edge", "get_workflow", "create_complete_workflow"
        ));
        
        metadata.put("capabilities", Arrays.asList(
            "自动化工作流生成", "AI 节点集成", "知识库检索",
            "意图识别", "复杂逻辑控制", "变量引用自动修复"
        ));
        
        return metadata;
    }
    
    @Override
    public List<String> getSupportedDslFormats() {
        return Arrays.asList("json");
    }

    @Override
    public String getDefaultDslFormat() {
        return "json";
    }
}
