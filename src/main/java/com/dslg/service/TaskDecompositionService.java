package com.dslg.service;

import com.dslg.ai.factory.AIModelFactory;
import com.dslg.ai.service.AIModelService;
import com.dslg.platform.PlatformType;
import com.dslg.service.model.TaskDecompositionResult;
import com.dslg.service.model.TaskStep;
import com.dslg.service.model.ControlFlow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务分解服务
 * 
 * 使用AI模型将用户描述的复杂任务分解为具体的、可执行的步骤
 * 支持识别控制结构、依赖关系和并行处理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDecompositionService {

    private final AIModelFactory aiModelFactory;
    private final ObjectMapper objectMapper;

    /**
     * 分解任务为具体步骤（使用自定义提示和指定平台）
     * 
     * @param customPrompt 自定义提示模板
     * @param taskDescription 任务描述
     * @param context 额外的上下文信息
     * @param platformType 目标平台类型
     * @return 分解后的任务步骤列表
     */
    public TaskDecompositionResult decomposeTaskWithPrompt(String customPrompt, String taskDescription, String context, PlatformType platformType) {
        if (taskDescription == null || taskDescription.trim().isEmpty()) {
            return TaskDecompositionResult.builder()
                    .success(false)
                    .errorMessage("任务描述不能为空")
                    .build();
        }

        try {
            log.info("开始任务分解，使用自定义提示，目标平台: {}", platformType != null ? platformType.getCode() : "AUTO");
            
            // 使用自定义提示进行任务分解
            AIModelService aiService = aiModelFactory.getAvailableModelService();
            String response = aiService.generateText(customPrompt);
            
            log.info("AI模型响应长度: {}", response.length());
            
            // 解析响应
            TaskDecompositionResult result = parseDecompositionResponse(response);
            result.setAiProvider(aiService.getProvider().getCode());
            result.setRawResponse(response);
            
            return result;
            
        } catch (Exception e) {
            log.error("任务分解失败", e);
            return TaskDecompositionResult.builder()
                    .success(false)
                    .errorMessage("任务分解失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 分解任务为具体步骤（使用自定义提示）
     * 
     * @param customPrompt 自定义提示模板
     * @param taskDescription 任务描述
     * @param context 额外的上下文信息
     * @return 分解后的任务步骤列表
     */
    public TaskDecompositionResult decomposeTaskWithPrompt(String customPrompt, String taskDescription, String context) {
        return decomposeTaskWithPrompt(customPrompt, taskDescription, context, null);
    }

    /**
     * 分解任务为具体步骤
     * 
     * @param taskDescription 任务描述
     * @return 分解后的任务步骤列表
     */
    public TaskDecompositionResult decomposeTask(String taskDescription) {
        return decomposeTask(taskDescription, null, null);
    }

    /**
     * 分解任务为具体步骤（带上下文）
     * 
     * @param taskDescription 任务描述
     * @param context 额外的上下文信息
     * @return 分解后的任务步骤列表
     */
    public TaskDecompositionResult decomposeTask(String taskDescription, String context) {
        return decomposeTask(taskDescription, context, null);
    }

    /**
     * 分解任务为具体步骤（带平台类型）
     * 
     * @param taskDescription 任务描述
     * @param platformType 目标平台类型
     * @return 分解后的任务步骤列表
     */
    public TaskDecompositionResult decomposeTask(String taskDescription, PlatformType platformType) {
        return decomposeTask(taskDescription, null, platformType);
    }

    /**
     * 分解任务为具体步骤（完整版本）
     * 
     * @param taskDescription 任务描述
     * @param context 额外的上下文信息
     * @param platformType 目标平台类型
     * @return 分解后的任务步骤列表
     */
    public TaskDecompositionResult decomposeTask(String taskDescription, String context, PlatformType platformType) {
        if (taskDescription == null || taskDescription.trim().isEmpty()) {
            return TaskDecompositionResult.builder()
                    .success(false)
                    .errorMessage("任务描述不能为空")
                    .build();
        }

        try {
            AIModelService aiService = aiModelFactory.getAvailableModelService();
            
            // 构建详细的任务分解提示
            String prompt = buildDecompositionPrompt(taskDescription, context);
            
            // 调用AI模型进行任务分解
            String response = aiService.generateText(prompt);
            
            // 解析AI响应
            TaskDecompositionResult result = parseDecompositionResponse(response);
            result.setAiProvider(aiService.getProvider().getCode());
            result.setRawResponse(response);
            
            log.info("Task decomposition completed for: '{}', {} steps generated", 
                    taskDescription.substring(0, Math.min(50, taskDescription.length())), 
                    result.getSteps().size());
            
            return result;
            
        } catch (Exception e) {
            log.error("任务分解失败", e);
            return TaskDecompositionResult.builder()
                    .success(false)
                    .errorMessage("任务分解失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 解析AI响应
     * 
     * @param response AI响应内容
     * @return 解析结果
     */
    private TaskDecompositionResult parseDecompositionResponse(String response) {
        try {
            // 提取JSON部分
            String jsonContent = extractJsonFromResponse(response);
            JsonNode rootNode = objectMapper.readTree(jsonContent);
            
            // 解析步骤列表
            List<TaskStep> steps = new ArrayList<>();
            JsonNode stepsNode = rootNode.get("steps");
            if (stepsNode != null && stepsNode.isArray()) {
                for (JsonNode stepNode : stepsNode) {
                    TaskStep step = parseTaskStep(stepNode);
                    if (step != null) {
                        steps.add(step);
                    }
                }
            }
            
            // 解析控制流信息
            ControlFlow controlFlow = parseControlFlow(rootNode.get("controlFlow"));
            
            // 获取摘要
            String summary = rootNode.has("summary") ? rootNode.get("summary").asText() : "";
            
            return TaskDecompositionResult.builder()
                    .success(true)
                    .steps(steps)
                    .controlFlow(controlFlow)
                    .summary(summary)
                    .build();
                    
        } catch (Exception e) {
            log.error("解析AI响应失败", e);
            return TaskDecompositionResult.builder()
                    .success(false)
                    .errorMessage("解析AI响应失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 解析单个任务步骤
     */
    private TaskStep parseTaskStep(JsonNode stepNode) {
        try {
            TaskStep.TaskStepBuilder builder = TaskStep.builder()
                    .id(stepNode.has("id") ? stepNode.get("id").asText() : "")
                    .name(stepNode.has("name") ? stepNode.get("name").asText() : "")
                    .description(stepNode.has("description") ? stepNode.get("description").asText() : "")
                    .type(stepNode.has("type") ? stepNode.get("type").asText() : "other");
            
            // 解析依赖关系
            if (stepNode.has("dependencies")) {
                List<String> dependencies = new ArrayList<>();
                JsonNode depsNode = stepNode.get("dependencies");
                if (depsNode.isArray()) {
                    for (JsonNode dep : depsNode) {
                        dependencies.add(dep.asText());
                    }
                }
                builder.dependencies(dependencies);
            }
            
            // 解析输入
            if (stepNode.has("inputs")) {
                List<String> inputs = new ArrayList<>();
                JsonNode inputsNode = stepNode.get("inputs");
                if (inputsNode.isArray()) {
                    for (JsonNode input : inputsNode) {
                        inputs.add(input.asText());
                    }
                }
                builder.inputs(inputs);
            }
            
            // 解析输出
            if (stepNode.has("outputs")) {
                List<String> outputs = new ArrayList<>();
                JsonNode outputsNode = stepNode.get("outputs");
                if (outputsNode.isArray()) {
                    for (JsonNode output : outputsNode) {
                        outputs.add(output.asText());
                    }
                }
                builder.outputs(outputs);
            }
            
            return builder.build();
            
        } catch (Exception e) {
            log.warn("解析任务步骤失败", e);
            return null;
        }
    }

    /**
     * 解析控制流信息
     */
    private ControlFlow parseControlFlow(JsonNode controlFlowNode) {
        if (controlFlowNode == null) {
            return ControlFlow.builder().build();
        }
        
        return ControlFlow.builder()
                .hasConditions(controlFlowNode.has("hasConditions") && controlFlowNode.get("hasConditions").asBoolean())
                .hasLoops(controlFlowNode.has("hasLoops") && controlFlowNode.get("hasLoops").asBoolean())
                .hasParallel(controlFlowNode.has("hasParallel") && controlFlowNode.get("hasParallel").asBoolean())
                .build();
    }

    /**
     * 从响应中提取JSON部分
     */
    private String extractJsonFromResponse(String response) {
        // 移除markdown代码块标记
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();
        
        // 查找JSON开始和结束位置
        int jsonStart = cleaned.indexOf("{");
        int jsonEnd = cleaned.lastIndexOf("}");
        
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return cleaned.substring(jsonStart, jsonEnd + 1);
        }
        
        // 如果没有找到完整的JSON，返回清理后的响应
        return cleaned;
    }

    /**
     * 构建任务分解提示词
     */
    private String buildDecompositionPrompt(String taskDescription, String context) {
        StringBuilder prompt = new StringBuilder();
        
        prompt.append("""
                你是一个专业的任务分解专家。请将用户描述的复杂任务分解为具体的、可执行的步骤。
                
                分解要求：
                1. 每个步骤都应该是具体的、可操作的
                2. 步骤之间应该有清晰的逻辑关系和依赖关系
                3. 识别出条件判断、循环、并行等控制结构
                4. 考虑异常处理和错误恢复
                5. 标识出需要人工干预的步骤
                
                请返回严格的JSON格式，结构如下：
                {
                  "success": true,
                  "steps": [
                    {
                      "id": "step1",
                      "name": "步骤名称",
                      "description": "详细描述",
                      "type": "步骤类型",
                      "dependencies": ["依赖的步骤ID"],
                      "inputs": ["输入参数"],
                      "outputs": ["输出结果"],
                      "conditions": "执行条件（可选）",
                      "isParallel": false,
                      "estimatedTime": "预估时间",
                      "priority": "优先级"
                    }
                  ],
                  "controlFlow": {
                    "hasConditions": false,
                    "hasLoops": false,
                    "hasParallel": false
                  },
                  "summary": "分解总结"
                }
                
                步骤类型包括：
                - data_input: 数据输入
                - data_processing: 数据处理
                - data_output: 数据输出
                - condition: 条件判断
                - loop: 循环处理
                - api_call: API调用
                - human_review: 人工审核
                - notification: 通知
                - other: 其他
                
                """);
        
        prompt.append("任务描述：").append(taskDescription).append("\n");
        
        if (context != null && !context.trim().isEmpty()) {
            prompt.append("上下文信息：").append(context).append("\n");
        }
        
        prompt.append("\n请开始分解任务：");
        
        return prompt.toString();
    }
}
