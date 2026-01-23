package com.dslg.platform.impl.inbuilder;

import com.dslg.service.DeviceModelService;
import com.dslg.service.NodeDefinitionService;
import com.dslg.service.model.TaskStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * InBuilder Prompt 构建服务
 * 
 * InBuilder 平台特定的 Prompt 构建逻辑
 */
@Slf4j
@Component
public class InBuilderPromptBuilder {
    
    private final NodeDefinitionService nodeDefinitionService;
    private final DeviceModelService deviceModelService;
    
    public InBuilderPromptBuilder(NodeDefinitionService nodeDefinitionService, DeviceModelService deviceModelService) {
        this.nodeDefinitionService = nodeDefinitionService;
        this.deviceModelService = deviceModelService;
    }
    
    /**
     * 构建任务分解 Prompt
     */
    public String buildTaskDecompositionPrompt(String taskDescription, String context) {
        log.info("=== 构建 InBuilder 任务分解 Prompt ===");
        
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
        
        String deviceTypesDesc = deviceModelService.getDeviceTypesDescription();
        if (deviceTypesDesc != null && !deviceTypesDesc.isEmpty()) {
            prompt.append("\n  [设备节点类型]\n");
            // 去掉开头的"可用的设备节点类型：\n"
            String cleanDesc = deviceTypesDesc.replace("可用的设备节点类型：\n", "");
            prompt.append(cleanDesc);
        }
        
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
    
    /**
     * 构建 MCP 生成 Prompt（简化版）
     */
    public String buildMcpGenerationPrompt(
            String userInput, 
            List<TaskStep> taskSteps, 
            String knowledgeContext) {
        
        log.info("=== 构建 InBuilder MCP 生成 Prompt ===");
        
        StringBuilder prompt = new StringBuilder();
        
        // 1. 基本说明
        appendBasicInstructions(prompt, userInput, knowledgeContext);
        
        // 2. 任务步骤
        appendTaskSteps(prompt, taskSteps);
        
        // 3. 节点类型和定义
        appendNodeDefinitions(prompt, taskSteps);
        
        // 4. 变量规范（核心）
        appendVariableRules(prompt);
        
        // 5. 端口和连线规范
        appendPortAndEdgeRules(prompt);
        
        // 6. JSON 示例
        appendJsonExample(prompt);
        
        // 7. 注意事项
        appendImportantNotes(prompt);
        
        return prompt.toString();
    }
    
    private void appendBasicInstructions(StringBuilder prompt, String userInput, String knowledgeContext) {
        prompt.append("请根据以下业务流程需求和确认的步骤，生成 InBuilder 流程的节点列表和连线列表：\n\n");
        prompt.append("业务流程需求：").append(userInput).append("\n\n");
        
        if (knowledgeContext != null && !knowledgeContext.isEmpty()) {
            prompt.append("知识库参考：\n").append(knowledgeContext).append("\n\n");
        }
    }
    
    private void appendTaskSteps(StringBuilder prompt, List<TaskStep> taskSteps) {
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
    }
    
    private void appendNodeDefinitions(StringBuilder prompt, List<TaskStep> taskSteps) {
        // 收集使用的节点类型
        Set<String> usedNodeTypes = new LinkedHashSet<>();
        usedNodeTypes.add("start");
        usedNodeTypes.add("end");
        
        if (taskSteps != null) {
            for (TaskStep step : taskSteps) {
                if (step.getType() != null && !step.getType().isEmpty()) {
                    usedNodeTypes.add(step.getType());
                }
            }
        }
        
        prompt.append("重要规范要求：\n");
        prompt.append("1. 必须包含一个开始节点(start)，结束节点(end)可以有多个\n");
        prompt.append("2. 节点数量限制：生成的流程节点总数不得超过20个\n");
        prompt.append("3. 节点类型(kind)必须严格从以下列表中选择：\n");
        prompt.append("   基础节点: [start, end, variableDef, batchAssignValue, selector]\n");
        
        Set<String> deviceCategories = deviceModelService.getAllDeviceCategories();
        if (!deviceCategories.isEmpty()) {
            prompt.append("   设备节点: ").append(deviceCategories).append("\n");
        }
        prompt.append("\n");
        
        prompt.append("4. 节点详细参数规范：\n");
        for (String nodeType : usedNodeTypes) {
            if (nodeDefinitionService.isNodeTypeSupported(nodeType)) {
                String nodeDefStr = nodeDefinitionService.formatNodeDefinitionForPrompt(nodeType);
                if (!nodeDefStr.isEmpty()) {
                    prompt.append(nodeDefStr).append("\n");
                }
            } else if (deviceModelService.isDeviceTypeSupported(nodeType)) {
                String deviceDefStr = deviceModelService.buildDeviceNodeDefinition(nodeType);
                if (!deviceDefStr.isEmpty()) {
                    prompt.append(deviceDefStr).append("\n");
                    // 追加 inputParams 结构说明
                    appendDeviceInputParamsGuide(prompt);
                }
            }
        }
    }

    private void appendDeviceInputParamsGuide(StringBuilder prompt) {
        prompt.append("    [重要] inputParams 填写规范示例：\n");
        prompt.append("    \"inputParams\": [\n");
        prompt.append("      {\n");
        prompt.append("        \"id\": \"参数名(如 coffeeType)\",\n");
        prompt.append("        \"code\": \"参数名(同上)\",\n");
        prompt.append("        \"type\": { \"source\": \"default\", \"typeId\": \"string\" },\n");
        prompt.append("        \"valueExpr\": {\n");
        prompt.append("          \"kind\": \"nodeVariable\", // 引用变量\n");
        prompt.append("          \"nodeCode\": \"来源节点Code(如 start)\",\n");
        prompt.append("          \"variable\": \"变量名(如 keyword)\",\n");
        prompt.append("          \"variableId\": \"变量ID\"\n");
        prompt.append("        }\n");
        prompt.append("      },\n");
        prompt.append("      // 或者直接值\n");
        prompt.append("      {\n");
        prompt.append("        \"id\": \"count\",\n");
        prompt.append("        \"code\": \"count\",\n");
        prompt.append("        \"type\": { \"source\": \"default\", \"typeId\": \"number\" },\n");
        prompt.append("        \"valueExpr\": { \"kind\": \"literal\", \"value\": 1 }\n");
        prompt.append("      }\n");
        prompt.append("    ]\n\n");
    }
    
    private void appendVariableRules(StringBuilder prompt) {
        prompt.append("5. 变量定义和引用规范（极其重要）：\n");
        prompt.append("   a) 变量ID规则：\n");
        prompt.append("      - 所有变量必须有唯一的 id 字段\n");
        prompt.append("      - id格式：节点ID + \"_\" + 变量名\n");
        prompt.append("      - 示例：start节点的age变量 -> \"start_age\"\n\n");
        
        prompt.append("   b) 变量引用规则：\n");
        prompt.append("      - 引用变量时必须包含 variableId 和 nodeCode\n");
        prompt.append("      - variableId：被引用变量的 id（保持不变）\n");
        prompt.append("      - nodeCode：格式为 kind_nodeId\n");
        prompt.append("      - 示例：引用 variableDef_1 的变量 -> nodeCode=\"variableDef_variableDef_1\"\n\n");
        
        prompt.append("   c) 变量作用域：\n");
        prompt.append("      - 节点只能访问其祖先节点定义的变量\n");
        prompt.append("      - variableDef 必须放在主流程路径上\n");
        prompt.append("      - batchAssignValue 只能修改已定义的变量\n\n");
    }
    
    private void appendPortAndEdgeRules(StringBuilder prompt) {
        prompt.append("6. 端口配置规则：\n");
        prompt.append("   - start: inputPorts=[], outputPorts=[\"output\"]\n");
        prompt.append("   - end: inputPorts=[\"input\"], outputPorts=[]\n");
        prompt.append("   - 其他节点: inputPorts=[\"input\"], outputPorts=[\"output\"]\n");
        prompt.append("   - selector: inputPorts=[\"input\"], outputPorts=[分支端口列表]\n\n");
        
        prompt.append("7. 连线规范：\n");
        prompt.append("   - sourceNodeId: 源节点ID\n");
        prompt.append("   - targetNodeId: 目标节点ID\n");
        prompt.append("   - sourcePort: 源端口\n");
        prompt.append("   - targetPort: 目标端口\n\n");
    }
    
    private void appendJsonExample(StringBuilder prompt) {
        prompt.append("请返回严格的JSON格式：\n");
        prompt.append("{\n");
        prompt.append("  \"nodes\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"id\": \"start\",\n");
        prompt.append("      \"name\": \"开始\",\n");
        prompt.append("      \"kind\": \"start\",\n");
        prompt.append("      \"inputPorts\": [],\n");
        prompt.append("      \"outputPorts\": [\"output\"],\n");
        prompt.append("      \"inputParams\": [{\"code\": \"age\", \"id\": \"start_age\", \"type\": {...}}],\n");
        prompt.append("      \"outputParams\": []\n");
        prompt.append("    }\n");
        prompt.append("  ],\n");
        prompt.append("  \"edges\": [\n");
        prompt.append("    {\"sourceNodeId\": \"start\", \"targetNodeId\": \"variableDef_1\", \"sourcePort\": \"output\", \"targetPort\": \"input\"}\n");
        prompt.append("  ]\n");
        prompt.append("}\n\n");
    }
    
    private void appendImportantNotes(StringBuilder prompt) {
        prompt.append("注意事项：\n");
        prompt.append("1. 所有变量定义必须包含 id 字段\n");
        prompt.append("2. 所有变量引用必须包含 variableId 字段\n");
        prompt.append("3. nodeCode 格式：kind_nodeId\n");
        prompt.append("4. 流程必须连通，从 start 到 end\n");
        prompt.append("5. 可以有多个 end 节点\n");
        prompt.append("6. 不要添加 description 字段\n");
    }
}
