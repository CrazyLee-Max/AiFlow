package com.dslg.platform.impl;

import com.dslg.platform.AbstractPlatformStrategy;
import com.dslg.platform.PlatformType;
import com.dslg.service.McpServerService;
import com.dslg.service.NodeDefinitionService;
import com.dslg.service.model.Edge;
import com.dslg.service.model.Node;
import com.dslg.service.model.TaskStep;
import com.dslg.service.model.WorkflowStructure;
import com.dslg.validator.VariableIdValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * InBuilder 平台策略实现
 * 
 * 支持 InBuilder 流程建模和 DSL 生成
 */
@Slf4j
@Component
public class InBuilderPlatformStrategy extends AbstractPlatformStrategy {

    @Value("${mcp.inbuilder.url:http://localhost:3001/mcp}")
    private String mcpServerUrl;
    
    private final NodeDefinitionService nodeDefinitionService;
    private final VariableIdValidator variableIdValidator;
    
    public InBuilderPlatformStrategy(
            NodeDefinitionService nodeDefinitionService,
            VariableIdValidator variableIdValidator) {
        this.nodeDefinitionService = nodeDefinitionService;
        this.variableIdValidator = variableIdValidator;
    }

    @Override
    public PlatformType getPlatformType() {
        return PlatformType.IN_BUILDER;
    }

    /**
     * 重写MCP响应处理方法，专门处理 InBuilder MCP Server 的响应格式
     */
    @Override
    public McpServerService.DslGenerationResult processMcpResponse(McpServerService.DslGenerationResponse response) {
        log.info("=== InBuilder平台MCP响应处理开始 ===");
        
        if (response == null) {
            log.error("MCP响应为空");
            return createErrorResult("MCP响应为空");
        }
        
        try {
            // 检查响应是否成功
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
                    
                    // 如果 result 是 Map，尝试提取 content
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
                                    
                                    // 构建成功的结果
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
                        
                        // 如果直接返回了对象 (非标准MCP，或者是直接结果)
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
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
            // 提取JSON内容
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
            // 降级到父类实现
            return super.buildMcpRequestFromAiResponse(userInput, aiResponse, knowledgeContext);
        }
    }

    @Override
    public String buildMcpGenerationPrompt(
            String userInput, 
            List<TaskStep> taskSteps, 
            String knowledgeContext) {
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("请根据以下业务流程需求和确认的步骤，生成 InBuilder 流程的节点列表和连线列表：\n\n");
        
        prompt.append("业务流程需求：").append(userInput).append("\n\n");
        
        if (knowledgeContext != null && !knowledgeContext.isEmpty()) {
            prompt.append("知识库参考：\n").append(knowledgeContext).append("\n\n");
        }
        
        prompt.append("用户确认的业务步骤（请严格实现这些步骤）：\n");
        if (taskSteps != null) {
            for (TaskStep step : taskSteps) {
                prompt.append("- [").append(step.getType()).append("] ")
                      .append(step.getName()).append(": ").append(step.getDescription());
                if (step.getDependencies() != null && !step.getDependencies().isEmpty()) {
                    prompt.append(" (依赖: ").append(String.join(", ", step.getDependencies())).append(")");
                }
                prompt.append("\n");
            }
        }
        prompt.append("\n");
        
        // 收集步骤中使用的节点类型
        Set<String> usedNodeTypes = new LinkedHashSet<>();
        usedNodeTypes.add("start"); // 始终包含开始节点
        usedNodeTypes.add("end");   // 始终包含结束节点
        
        if (taskSteps != null) {
            for (TaskStep step : taskSteps) {
                if (step.getType() != null && !step.getType().isEmpty()) {
                    usedNodeTypes.add(step.getType());
                }
            }
        }
        
        prompt.append("重要规范要求：\n");
        prompt.append("1. 必须包含一个开始节点(start)，结束节点(end)可以有多个\n");
        prompt.append("2. 节点数量限制：生成的流程节点总数不得超过20个，以保持流程简洁。\n");
        prompt.append("3. 节点类型(kind)必须严格从以下列表中选择，严禁创造不存在的节点类型：\n");
        prompt.append("   [start, end, variableDef, batchAssignValue, selector]\n");
        prompt.append("   注意：当前只支持这5种基础节点类型，不要使用其他类型！\n\n");

        // 动态加载并添加节点定义
        prompt.append("4. 节点详细参数规范 (请严格遵循)：\n");
        prompt.append("   根据您的业务步骤，以下是需要使用的节点类型及其详细定义：\n\n");
        
        for (String nodeType : usedNodeTypes) {
            if (nodeDefinitionService.isNodeTypeSupported(nodeType)) {
                String nodeDefStr = nodeDefinitionService.formatNodeDefinitionForPrompt(nodeType);
                if (!nodeDefStr.isEmpty()) {
                    prompt.append(nodeDefStr).append("\n");
                }
            } else {
                log.warn("节点类型 {} 没有对应的定义文件，将使用默认描述", nodeType);
                prompt.append("   - ").append(nodeType).append(" (暂无详细定义)\n\n");
            }
        }
        
        prompt.append("5. 变量定义和引用规范（极其重要）：\n");
        prompt.append("   a) 变量定义时必须添加id字段：\n");
        prompt.append("      在 inputParams 或 outputParams 中定义变量时，每个变量都必须有唯一的 id 字段\n");
        prompt.append("      id生成规则：节点ID + \"_\" + 变量名（注意是节点ID，不是节点kind）\n");
        prompt.append("      示例：\n");
        prompt.append("      {\n");
        prompt.append("        \"code\": \"age\",\n");
        prompt.append("        \"id\": \"start_age\",          // start节点的ID就是'start'\n");
        prompt.append("        \"type\": {...}\n");
        prompt.append("      }\n");
        prompt.append("      {\n");
        prompt.append("        \"code\": \"result\",\n");
        prompt.append("        \"id\": \"end_1_result\",       // end节点的ID是'end_1'\n");
        prompt.append("        \"type\": {...}\n");
        prompt.append("      }\n\n");
        prompt.append("   b) 变量引用时必须添加variableId和nodeCode字段：\n");
        prompt.append("      当通过 valueExpr 引用其他节点的变量时，必须包含 variableId 和 nodeCode 字段\n");
        prompt.append("      - variableId：被引用变量定义时的 id 字段值\n");
        prompt.append("      - nodeCode：格式必须是 被引用节点的kind_被引用节点的id\n");
        prompt.append("      \n");
        prompt.append("      nodeCode 格式详解：\n");
        prompt.append("      - start节点(id=\"start\"): nodeCode = \"start_start\"\n");
        prompt.append("      - variableDef节点(id=\"variableDef_1\"): nodeCode = \"variableDef_variableDef_1\"\n");
        prompt.append("      - variableDef节点(id=\"variableDef_2\"): nodeCode = \"variableDef_variableDef_2\"\n");
        prompt.append("      - end节点(id=\"end_1\"): nodeCode = \"end_end_1\"\n");
        prompt.append("      - batchAssignValue节点(id=\"batchAssignValue_1\"): nodeCode = \"batchAssignValue_batchAssignValue_1\"\n");
        prompt.append("      \n");
        prompt.append("      引用格式：\n");
        prompt.append("      {\n");
        prompt.append("        \"kind\": \"nodeVariable\",\n");
        prompt.append("        \"nodeCode\": \"被引用节点的kind_被引用节点的id\",  // 必须严格遵守此格式\n");
        prompt.append("        \"variable\": \"被引用节点的输出变量名\",\n");
        prompt.append("        \"variableId\": \"被引用变量的id(格式: 节点ID_变量名)\"\n");
        prompt.append("      }\n\n");
        prompt.append("   c) 完整示例说明：\n");
        prompt.append("      步骤1 - start节点定义变量：\n");
        prompt.append("      \"inputParams\": [{\n");
        prompt.append("        \"code\": \"age\",\n");
        prompt.append("        \"id\": \"start_age\",\n");
        prompt.append("        \"type\": {\"typeId\": \"number\"}\n");
        prompt.append("      }]\n\n");
        prompt.append("      步骤2 - variableDef节点引用start的变量：\n");
        prompt.append("      \"outputParams\": [{\n");
        prompt.append("        \"code\": \"userAge\",\n");
        prompt.append("        \"id\": \"variableDef_1_userAge\",  // 节点ID是variableDef_1\n");
        prompt.append("        \"type\": {\"typeId\": \"number\"},\n");
        prompt.append("        \"valueExpr\": {\n");
        prompt.append("          \"kind\": \"nodeVariable\",\n");
        prompt.append("          \"nodeCode\": \"start_start\",     // kind_id格式: start节点的kind是start, id是start\n");
        prompt.append("          \"variable\": \"age\",\n");
        prompt.append("          \"variableId\": \"start_age\"     // 引用start节点的age变量\n");
        prompt.append("        }\n");
        prompt.append("      }]\n\n");
        prompt.append("      步骤3 - batchAssignValue节点修改变量：\n");
        prompt.append("      \"expresses\": [{\n");
        prompt.append("        \"kind\": \"assignValue\",\n");
        prompt.append("        \"leftExpress\": {\n");
        prompt.append("          \"kind\": \"nodeVariable\",\n");
        prompt.append("          \"nodeCode\": \"variableDef_variableDef_1\",\n") ;
        prompt.append("          \"variable\": \"userAge\",\n");
        prompt.append("          \"variableId\": \"variableDef_1_userAge\"  // 引用variableDef_1的userAge变量\n");
        prompt.append("        },\n");
        prompt.append("        \"rightExpress\": {\"kind\": \"numberConst\", \"value\": 18}\n");
        prompt.append("      }]\n\n");
        prompt.append("      步骤4 - end节点输出变量：\n");
        prompt.append("      \"outputParams\": [{\n");
        prompt.append("        \"code\": \"result\",\n");
        prompt.append("        \"id\": \"end_1_result\",           // 节点ID是end_1\n");
        prompt.append("        \"type\": {\"typeId\": \"number\"},\n");
        prompt.append("        \"valueExpr\": {\n");
        prompt.append("          \"kind\": \"nodeVariable\",\n");
        prompt.append("          \"nodeCode\": \"variableDef_variableDef_1\",  // kind_id格式: variableDef_1节点的kind是variableDef, id是variableDef_1\n");
        prompt.append("          \"variable\": \"userAge\",\n");
        prompt.append("          \"variableId\": \"variableDef_1_userAge\"  // 引用variableDef_1的userAge\n");
        prompt.append("        }\n");
        prompt.append("      }]\n\n");
        prompt.append("   d) selector 节点的 branches 结构（重要）：\n");
        prompt.append("      每个分支必须包含conditionExpr 字段，且引用变量时必须包含variableId：\n");
        prompt.append("      {\n");
        prompt.append("        \"port\": \"分支端口名\",\n");
        prompt.append("        \"conditionExpr\": {\n");
        prompt.append("          \"kind\": \"logic\",\n");
        prompt.append("          \"operator\": \"AND\",\n");
        prompt.append("          \"expresses\": [\n");
        prompt.append("            {\n");
        prompt.append("              \"kind\": \"compare\",\n");
        prompt.append("              \"operator\": \"lessThan\",\n");
        prompt.append("              \"leftExpress\": {\n");
        prompt.append("                \"kind\": \"nodeVariable\",\n");
        prompt.append("                \"nodeCode\": \"variableDef_variableDef_1\",  // kind_id格式\n");
        prompt.append("                \"variable\": \"age\",\n");
        prompt.append("                \"variableId\": \"variableDef_1_age\"\n");
        prompt.append("              },\n");
        prompt.append("              \"rightExpress\": {\n");
        prompt.append("                \"kind\": \"numberConst\",\n");
        prompt.append("                \"value\": 18\n");
        prompt.append("              }\n");
        prompt.append("            }\n");
        prompt.append("          ]\n");
        prompt.append("        }\n");
        prompt.append("      }\n");
        prompt.append("      注意：\n");
        prompt.append("      - conditionExpr 必须是 logic 类型，即使只有一个条件\n");
        prompt.append("      - operator 必须大写：AND 或 OR\n");
        prompt.append("      - 条件放在 expresses 数组中\n");
        prompt.append("      - default 分支的 conditionExpr 为 null\n");
        prompt.append("      - leftExpress 和 rightExpress 如果是 nodeVariable 类型，必须包含 variableId\n\n");
        prompt.append("   e) 重要提示：\n");
        prompt.append("   - 所有定义变量的地方(inputParams/outputParams)都必须包含 id 字段\n");
        prompt.append("   - 所有引用变量的地方(valueExpr中的nodeVariable)都必须包含 variableId 字段\n");
        prompt.append("   - id生成规则：节点ID + \"_\" + 变量名（例如：start_age, variableDef_1_result, end_1_output）\n");
        prompt.append("   - 注意：是节点ID，不是节点kind！多个同类型节点会有不同的ID（如end_1, end_2）\n");
        prompt.append("   - variableId 的值必须与被引用变量定义时的 id 字段值完全一致\n");
        prompt.append("   - 变量作用域规则：一个节点只能访问其祖先节点定义的变量（流程图是树结构）\n");
        prompt.append("   - 根据任务步骤中的 dependencies 字段，确定节点间的数据依赖关系\n");
        prompt.append("   - 如果步骤 A 依赖步骤 B，则步骤 A 的节点应该通过 valueExpr 引用步骤 B 的输出\n");
        prompt.append("   - nodeCode 格式必须是: 被引用节点的kind_被引用节点的id（例如: start_start, variableDef_variableDef_1, end_end_1）\n");
        prompt.append("   - nodeCode 引用的节点必须是实际存在的节点，且必须是当前节点的祖先节点\n");
        prompt.append("   - variable 必须是被引用节点的 outputParams 中定义的变量名\n\n");
        
        prompt.append("6. 端口配置规则（重要）：\n");
        prompt.append("   - start 节点: inputPorts=[], outputPorts=[\"output\"]\n");
        prompt.append("   - end 节点: inputPorts=[\"input\"], outputPorts=[]\n");
        prompt.append("   - 其他节点: inputPorts=[\"input\"], outputPorts=[\"output\"]\n");
        prompt.append("   - selector 节点: inputPorts=[\"input\"], outputPorts=[分支端口列表]\n\n");
        
        prompt.append("7. 通用属性：\n");
        prompt.append("   - id: 节点唯一标识（建议使用节点类型_序号格式，如 variableDef_1）\n");
        prompt.append("   - name: 节点显示名称\n");
        prompt.append("   - kind: 节点类型（必须是上述支持的类型之一）\n\n");
        
        prompt.append("8. 连线(edges)规范：\n");
        prompt.append("   - sourceNodeId: 源节点ID\n");
        prompt.append("   - targetNodeId: 目标节点ID\n");
        prompt.append("   - sourcePort: 源端口 (start和普通节点用'output', selector节点用分支端口名)\n");
        prompt.append("   - targetPort: 目标端口 (普通节点和end节点用'input')\n\n");
        
        prompt.append("请返回严格的JSON格式，结构如下（注意id和variableId字段）：\n");
        prompt.append("{\n");
        prompt.append("  \"nodes\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"id\": \"start\",\n");
        prompt.append("      \"name\": \"开始\",\n");
        prompt.append("      \"kind\": \"start\",\n");
        prompt.append("      \"inputPorts\": [],\n");
        prompt.append("      \"outputPorts\": [\"output\"],\n");
        prompt.append("      \"inputParams\": [\n");
        prompt.append("        {\n");
        prompt.append("          \"code\": \"age\",\n");
        prompt.append("          \"id\": \"start_age\",\n");
        prompt.append("          \"type\": {\"source\": \"default\", \"typeId\": \"number\", \"typeName\": \"Number\"}\n");
        prompt.append("        }\n");
        prompt.append("      ],\n");
        prompt.append("      \"outputParams\": []\n");
        prompt.append("    },\n");
        prompt.append("    {\n");
        prompt.append("      \"id\": \"variableDef_1\",\n");
        prompt.append("      \"name\": \"变量定义\",\n");
        prompt.append("      \"kind\": \"variableDef\",\n");
        prompt.append("      \"inputPorts\": [\"input\"],\n");
        prompt.append("      \"outputPorts\": [\"output\"],\n");
        prompt.append("      \"inputParams\": [],\n");
        prompt.append("      \"outputParams\": [\n");
        prompt.append("        {\n");
        prompt.append("          \"code\": \"userAge\",\n");
        prompt.append("          \"id\": \"variableDef_1_userAge\",\n");
        prompt.append("          \"type\": {\"source\": \"default\", \"typeId\": \"number\", \"typeName\": \"Number\"},\n");
        prompt.append("          \"valueExpr\": {\n");
        prompt.append("            \"kind\": \"nodeVariable\",\n");
        prompt.append("            \"nodeCode\": \"start_start\",  // kind_id格式\n");
        prompt.append("            \"variable\": \"age\",\n");
        prompt.append("            \"variableId\": \"start_age\"\n");
        prompt.append("          }\n");
        prompt.append("        }\n");
        prompt.append("      ]\n");
        prompt.append("    },\n");
        prompt.append("    {\n");
        prompt.append("      \"id\": \"end\",\n");
        prompt.append("      \"name\": \"结束\",\n");
        prompt.append("      \"kind\": \"end\",\n");
        prompt.append("      \"inputPorts\": [\"input\"],\n");
        prompt.append("      \"outputPorts\": [],\n");
        prompt.append("      \"inputParams\": [],\n");
        prompt.append("      \"outputParams\": [\n");
        prompt.append("        {\n");
        prompt.append("          \"code\": \"result\",\n");
        prompt.append("          \"id\": \"end_result\",\n");
        prompt.append("          \"type\": {\"source\": \"default\", \"typeId\": \"number\", \"typeName\": \"Number\"},\n");
        prompt.append("          \"valueExpr\": {\n");
        prompt.append("            \"kind\": \"nodeVariable\",\n");
        prompt.append("            \"nodeCode\": \"variableDef_variableDef_1\",  // kind_id格式\n");
        prompt.append("            \"variable\": \"userAge\",\n");
        prompt.append("            \"variableId\": \"variableDef_1_userAge\"\n");
        prompt.append("          }\n");
        prompt.append("        }\n");
        prompt.append("      ]\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"edges\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"sourceNodeId\": \"start\",\n");
        prompt.append("      \"targetNodeId\": \"variableDef_1\",\n");
        prompt.append("      \"sourcePort\": \"output\",\n");
        prompt.append("      \"targetPort\": \"input\"\n");
        prompt.append("    },\n");
        prompt.append("    {\n");
        prompt.append("      \"sourceNodeId\": \"variableDef_1\",\n");
        prompt.append("      \"targetNodeId\": \"end\",\n");
        prompt.append("      \"sourcePort\": \"output\",\n");
        prompt.append("      \"targetPort\": \"input\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
        
        prompt.append("注意事项（必须严格遵守）：\n");
        prompt.append("1. **变量id和variableId规则（极其重要）**：\n");
        prompt.append("   - 所有在 inputParams 或 outputParams 中定义的变量都必须有 id 字段\n");
        prompt.append("   - id 格式：节点ID + \"_\" + 变量名（如 start_age, variableDef_1_result, end_1_output）\n");
        prompt.append("   - 重要：是节点ID（如end_1），不是节点kind（如end）！\n");
        prompt.append("   - 所有 valueExpr 中的 nodeVariable 类型表达式都必须包含 variableId 字段\n");
        prompt.append("   - variableId 的值必须与被引用变量的 id 完全一致\n");
        prompt.append("   - 这适用于所有节点类型：start, variableDef, batchAssignValue, selector, end\n");
        prompt.append("2. 严格遵守端口配置规则：start无输入端口，end无输出端口，其他节点都有输入输出端口\n");
        prompt.append("3. 节点之间的连接关系必须通过 edges 数组明确定义\n");
        prompt.append("4. 确保所有节点都按照上述节点定义规范填写完整的参数\n");
        prompt.append("5. **selector 节点的 branches 结构（极其重要）**：\n");
        prompt.append("   - conditionExpr 必须是 logic 类型（kind: \"logic\"），即使只有一个条件\n");
        prompt.append("   - operator 必须大写：AND 或 OR\n");
        prompt.append("   - 条件必须放在 expresses 数组中，每个条件是 compare 类型\n");
        prompt.append("   - leftExpress 和 rightExpress 如果引用变量，必须包含 variableId\n");
        prompt.append("   - 每个分支端口都需要在 edges 中有对应的连线\n");
        prompt.append("6. 流程必须是连通的，从 start 节点开始，到 end 节点结束（可以有多个 end 节点）\n");
        prompt.append("7. **变量作用域规则（极其重要）**：\n");
        prompt.append("   - 流程图是树结构，一个节点只能访问其祖先节点定义的变量\n");
        prompt.append("   - 如果需要在多个分支中使用某个变量，该变量必须在分支的公共祖先节点中定义\n");
        prompt.append("   - variableDef 节点必须放在主流程路径上，不能放在与主流程并行的分支上\n");
        prompt.append("   - 错误示例：variableDef 与 selector 并行，导致 selector 的子节点无法访问 variableDef 的变量\n");
        prompt.append("   - 正确示例：start → variableDef → selector → 各个分支，所有分支都能访问 variableDef 的变量\n");
        prompt.append("8. **batchAssignValue 节点的重要规则**：\n");
        prompt.append("   - batchAssignValue 只能修改已存在变量的值，不能创建新变量\n");
        prompt.append("   - leftExpress 引用的变量必须已经在其祖先节点的 variableDef 的 outputParams 中定义\n");
        prompt.append("   - leftExpress 和 rightExpress 如果是 nodeVariable 类型，必须包含 variableId\n");
        prompt.append("   - 如果需要在流程中输出新的变量，必须先在 variableDef 节点中定义该变量\n");
        prompt.append("   - 错误示例：variableDef 定义了 age、height、weight，但 batchAssignValue 试图给 result 赋值（result 未定义）\n");
        prompt.append("   - 正确示例：variableDef 定义 age、height、weight、result，batchAssignValue 才能给 result 赋值\n");
        prompt.append("9. end 节点的 outputParams 必须通过 valueExpr 引用其祖先节点的输出变量，且必须包含 variableId\n");
        prompt.append("10. 不要添加 description 字段\n");
        prompt.append("11. 可以有多个结束节点，结果不必都聚合在同一个结束节点上，不然会出现参数指代不清\n");
        
        return prompt.toString();
    }

    /**
     * 解析 AI 返回的节点和边的 JSON，构建节点列表和边列表
     * 
     * @param aiResponse AI 返回的 JSON 字符串
     * @return WorkflowStructure 包含节点列表和边列表
     */
    public WorkflowStructure parseNodesAndEdges(String aiResponse) {
        log.info("=== 开始解析节点和边 ===");
        
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        
        try {
            // 提取 JSON 内容
            String jsonContent = extractJsonFromResponse(aiResponse);
            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(jsonContent);
            
            // 解析 nodes 数组
            if (rootNode.has("nodes") && rootNode.get("nodes").isArray()) {
                com.fasterxml.jackson.databind.JsonNode nodesArray = rootNode.get("nodes");
                for (com.fasterxml.jackson.databind.JsonNode nodeJson : nodesArray) {
                    Node node = parseNode(nodeJson);
                    nodes.add(node);
                    log.debug("解析节点: id={}, kind={}, name={}", 
                            node.getId(), node.getKind(), node.getName());
                }
                log.info("成功解析 {} 个节点", nodes.size());
            } else {
                log.warn("AI 返回的 JSON 中没有 nodes 数组");
            }
            
            // 解析 edges 数组
            if (rootNode.has("edges") && rootNode.get("edges").isArray()) {
                com.fasterxml.jackson.databind.JsonNode edgesArray = rootNode.get("edges");
                for (com.fasterxml.jackson.databind.JsonNode edgeJson : edgesArray) {
                    Edge edge = parseEdge(edgeJson);
                    edges.add(edge);
                    log.debug("解析连线: {} -> {}", 
                            edge.getSourceNodeId(), edge.getTargetNodeId());
                }
                log.info("成功解析 {} 条连线", edges.size());
            } else {
                log.warn("AI 返回的 JSON 中没有 edges 数组");
            }
            
            log.info("=== 节点和边解析完成 ===");
            
            return WorkflowStructure.builder()
                    .nodes(nodes)
                    .edges(edges)
                    .success(true)
                    .build();
            
        } catch (Exception e) {
            log.error("解析节点和边时发生异常", e);
            return WorkflowStructure.builder()
                    .nodes(nodes)
                    .edges(edges)
                    .success(false)
                    .error("解析失败: " + e.getMessage())
                    .build();
        }
    }
    
    /**
     * 解析单个节点
     */
    private Node parseNode(com.fasterxml.jackson.databind.JsonNode nodeJson) {
        Node.NodeBuilder builder = Node.builder();
        
        // 基本属性
        if (nodeJson.has("id")) {
            builder.id(nodeJson.get("id").asText());
        }
        if (nodeJson.has("name")) {
            builder.name(nodeJson.get("name").asText());
        }
        if (nodeJson.has("kind")) {
            builder.kind(nodeJson.get("kind").asText());
        }
        
        // 端口
        if (nodeJson.has("inputPorts")) {
            List<String> inputPorts = objectMapper.convertValue(
                    nodeJson.get("inputPorts"), List.class);
            builder.inputPorts(inputPorts);
        }
        if (nodeJson.has("outputPorts")) {
            List<String> outputPorts = objectMapper.convertValue(
                    nodeJson.get("outputPorts"), List.class);
            builder.outputPorts(outputPorts);
        }
        
        // 参数
        if (nodeJson.has("inputParams")) {
            List<Map<String, Object>> inputParams = objectMapper.convertValue(
                    nodeJson.get("inputParams"), List.class);
            builder.inputParams(inputParams);
        }
        if (nodeJson.has("outputParams")) {
            List<Map<String, Object>> outputParams = objectMapper.convertValue(
                    nodeJson.get("outputParams"), List.class);
            builder.outputParams(outputParams);
        }
        
        // 其他动态属性 (如 branches, expresses 等)
        Map<String, Object> additionalProperties = new HashMap<>();
        nodeJson.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            // 跳过已经处理的基本属性
            if (!Arrays.asList("id", "name", "kind", 
                    "inputPorts", "outputPorts", "inputParams", "outputParams").contains(key)) {
                additionalProperties.put(key, objectMapper.convertValue(entry.getValue(), Object.class));
            }
        });
        builder.additionalProperties(additionalProperties);
        
        return builder.build();
    }
    
    /**
     * 解析单个边
     */
    private Edge parseEdge(com.fasterxml.jackson.databind.JsonNode edgeJson) {
        Edge.EdgeBuilder builder = Edge.builder();
        
        if (edgeJson.has("sourceNodeId")) {
            builder.sourceNodeId(edgeJson.get("sourceNodeId").asText());
        }
        if (edgeJson.has("targetNodeId")) {
            builder.targetNodeId(edgeJson.get("targetNodeId").asText());
        }
        if (edgeJson.has("sourcePort")) {
            builder.sourcePort(edgeJson.get("sourcePort").asText());
        } else {
            builder.sourcePort("output"); // 默认值
        }
        if (edgeJson.has("targetPort")) {
            builder.targetPort(edgeJson.get("targetPort").asText());
        } else {
            builder.targetPort("input"); // 默认值
        }
        
        return builder.build();
    }
    
    /**
     * 验证节点和边的有效性
     * 
     * @param nodes 节点列表
     * @param edges 边列表
     * @return 验证结果，包含是否有效和错误信息
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
                // 检查节点类型是否支持
                if (!nodeDefinitionService.isNodeTypeSupported(nodeKind)) {
                    errors.add("节点 " + nodeId + " 使用了不支持的类型: " + nodeKind);
                }
                
                // 检查是否有 start 和 end 节点
                if ("start".equals(nodeKind)) {
                    hasStart = true;
                }
                if ("end".equals(nodeKind)) {
                    hasEnd = true;
                }
            }
        }
        
        // 验证必须有 start 节点，至少有一个 end 节点
        if (!hasStart) {
            errors.add("流程缺少 start 节点");
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
            
            // 检查源节点和目标节点是否存在
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
        if (nodes.size() > 10) {
            warnings.add("节点数量 (" + nodes.size() + ") 超过建议的最大值 (10)");
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
        log.info("=== InBuilder平台任务分解开始 ===");
        
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个业务流程分析专家。请将用户描述的复杂任务分解为具体的、可执行的业务步骤。\n");
        prompt.append("注意：当前阶段仅进行业务逻辑拆解，不需要生成具体的代码或技术参数。\n\n");
        
        prompt.append("业务流程描述：").append(taskDescription).append("\n");
        
        if (context != null && !context.trim().isEmpty()) {
            prompt.append("上下文信息：").append(context).append("\n");
        }
        
        prompt.append("\n节点类型选择建议：\n");
        prompt.append("  - 流程开始 -> start\n");
        prompt.append("  - 流程结束 -> end\n");
        prompt.append("  - 定义变量/初始化数据 -> variableDef\n");
        prompt.append("  - 变量赋值/数据处理 -> batchAssignValue\n");
        prompt.append("  - 条件判断/分支选择 -> selector\n");
        
        prompt.append("\n请返回严格的JSON格式，结构如下：\n");
        prompt.append("{\n");
        prompt.append("  \"steps\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"id\": \"step1\",\n");
        prompt.append("      \"name\": \"步骤名称\",\n");
        prompt.append("      \"description\": \"描述（不用太具体 30字以内）\",\n");
        prompt.append("      \"type\": \"节点类型（必须从上述支持的类型中选择）\",\n");
        prompt.append("      \"dependencies\": [\"依赖的步骤ID（可选）\"]\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"summary\": \"整体流程总结\"\n");
        prompt.append("}\n");
        
        prompt.append("\n重要提示：\n");
        prompt.append("1. 必须包含 start 和 end 节点\n");
        prompt.append("2. type 字段必须使用上述列出的节点类型，不要自创类型\n");
        prompt.append("3. dependencies 数组中填写该步骤依赖的前置步骤的 id\n");
        prompt.append("4. 步骤数量建议控制在8个以内，保持流程简洁\n");
        
        return prompt.toString();
    }
    
    @Override
    public Map<String, Object> getPlatformMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("platform", "InBuilder");
        metadata.put("version", "1.0");
        metadata.put("specification", "InBuilder DSL");
        metadata.put("mcpServerPort", 3001);
        
        metadata.put("supportedElements", Arrays.asList(
            "start", "end", "variableDef", "batchAssignValue", "selector"
        ));
        
        metadata.put("mcpTools", Arrays.asList(
            "create_workflow", "add_node", "add_edge", "get_workflow", "create_complete_workflow"
        ));
        
        metadata.put("capabilities", Arrays.asList(
            "自动化工作流生成", "AI 节点集成", "知识库检索",
            "意图识别", "复杂逻辑控制"
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
