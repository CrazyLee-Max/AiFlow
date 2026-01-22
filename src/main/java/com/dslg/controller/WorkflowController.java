package com.dslg.controller;

import com.dslg.dto.WorkflowGenerationRequest;
import com.dslg.dto.WorkflowGenerationResponse;
import com.dslg.dto.WorkflowStructureResponse;
import com.dslg.service.*;
import com.dslg.service.model.*;
import com.dslg.platform.PlatformStrategyFactory;
import com.dslg.platform.PlatformStrategy;
import com.dslg.platform.PlatformType;
import com.dslg.platform.impl.InBuilderPlatformStrategy;
import com.dslg.ai.factory.AIModelFactory;
import com.dslg.ai.service.AIModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 工作流生成控制器
 * 
 * 提供工作流DSL生成的REST API接口
 */
@Slf4j
@RestController
@RequestMapping("/v1/workflow")
@RequiredArgsConstructor
@Validated
public class WorkflowController {

    private final TaskDecompositionService taskDecompositionService;
    private final McpServerService mcpServerService;
    private final PlatformStrategyFactory platformStrategyFactory;
    private final FileStorageService fileStorageService;
    private final AIModelFactory aiModelFactory;

    /**
     * 阶段一：需求分析与任务分解
     */
    @PostMapping("/{platform}/analyze")
    public ResponseEntity<WorkflowGenerationResponse> analyzeRequirement(
            @PathVariable String platform,
            @Valid @RequestBody WorkflowGenerationRequest request) {
        log.info("收到需求分析请求，用户输入: {}, 目标平台: {}", request.getUserInput(), platform);
        long startTime = System.currentTimeMillis();

        try {
            // 获取平台策略
            PlatformType platformType = PlatformType.fromCode(platform);
            PlatformStrategy platformStrategy = platformStrategyFactory.getStrategy(platformType);

            // 任务分解
            String customPrompt = platformStrategy.buildTaskDecompositionPrompt(request.getUserInput(), "");
            TaskDecompositionResult taskResult = 
                    taskDecompositionService.decomposeTaskWithPrompt(customPrompt, request.getUserInput(), "");

            // 构建响应（只返回分析结果，不生成DSL）
            WorkflowGenerationResponse response = WorkflowGenerationResponse.builder()
                    .success(true)
                    .taskDecomposition(convertTaskDecomposition(taskResult))
                    .generationTime(LocalDateTime.now())
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build();

            // 添加平台信息
            if (response.getMetadata() == null) {
                response.setMetadata(new HashMap<>());
            }
            response.getMetadata().put("platform", platform);
            response.getMetadata().put("platformName", platformType.getDisplayName());
            response.getMetadata().put("phase", "analysis");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("需求分析过程中发生错误", e);
            return ResponseEntity.ok(WorkflowGenerationResponse.builder()
                    .success(false)
                    .error("分析过程中发生错误: " + e.getMessage())
                    .generationTime(LocalDateTime.now())
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build());
        }
    }

    /**
     * 生成工作流DSL
     * 
     * @param request 生成请求
     * @return 生成结果
     */
    @PostMapping("/{platform}/generate")
    public ResponseEntity<WorkflowGenerationResponse> generateWorkflow(
            @PathVariable String platform,
            @Valid @RequestBody WorkflowGenerationRequest request) {
        log.info("收到工作流生成请求，用户输入: {}, 目标平台: {}", request.getUserInput(), platform);
        long startTime = System.currentTimeMillis();

        try {
            // 获取平台策略
            PlatformType platformType = PlatformType.fromCode(platform);
            PlatformStrategy platformStrategy = platformStrategyFactory.getStrategy(platformType);
            
            // 处理任务分解和MCP请求构建
            Map<String, Object> mcpRequest;
            TaskDecompositionResult taskResult = null;
            
            if (request.getTaskDecompositionResult() != null) {
                // 阶段二：使用用户确认的步骤生成
                log.info("检测到确认的任务步骤，进入第二阶段生成");
                
                // 将DTO转换为Service Model
                List<TaskStep> confirmedSteps = convertDtoTaskStepsToServiceTaskSteps(
                        request.getTaskDecompositionResult().getSteps());
                
                // 构建第二阶段提示词
                String mcpPrompt = platformStrategy.buildMcpGenerationPrompt(
                        request.getUserInput(), 
                        confirmedSteps, 
                        "");
                
                // 调用AI模型生成MCP请求
                AIModelService aiService = aiModelFactory.getAvailableModelService();
                String mcpResponseStr = aiService.generateText(mcpPrompt);
                log.info("AI模型生成的MCP请求响应长度: {}", mcpResponseStr.length());
                
                // 解析AI响应构建MCP请求
                mcpRequest = platformStrategy.buildMcpRequestFromAiResponse(
                        request.getUserInput(),
                        mcpResponseStr,
                        ""
                );
                
                // 构造一个临时的TaskResult用于响应返回
                taskResult = TaskDecompositionResult.builder()
                        .success(true)
                        .steps(confirmedSteps)
                        .summary(request.getTaskDecompositionResult().getSummary())
                        .build();
                        
            } else {
                // 传统模式：一站式生成
                log.info("未检测到确认步骤，执行完整生成流程");
                
                // 使用平台策略进行任务分解
                String customPrompt = platformStrategy.buildTaskDecompositionPrompt(request.getUserInput(), "");
                taskResult = taskDecompositionService.decomposeTaskWithPrompt(customPrompt, request.getUserInput(), "");

                // 使用平台策略构建MCP请求
                mcpRequest = platformStrategy.buildMcpRequestFromAiResponse(
                        request.getUserInput(),
                        taskResult.getRawResponse(),
                        ""
                );
            }
            
            log.info("构建MCP请求完成，准备发送到Server");
            
            // 直接调用MCP Server生成DSL
            McpServerService.DslGenerationResult dslResult = mcpServerService.sendStandardMcpRequest(
                    mcpRequest, 
                    platformStrategy.getMcpServerUrl(),
                    platformStrategy
            );

            if (!dslResult.isSuccess()) {
                log.error("DSL生成失败: {}", dslResult.getError());
                return ResponseEntity.ok(WorkflowGenerationResponse.builder()
                        .success(false)
                        .error("DSL生成失败: " + dslResult.getError())
                        .generationTime(LocalDateTime.now())
                        .processingTimeMs(System.currentTimeMillis() - startTime)
                        .build());
            }

            // 构建响应
            WorkflowGenerationResponse response = WorkflowGenerationResponse.builder()
                    .success(true)
                    .dslContent(dslResult.getDslContent())
                    .dslFormat(dslResult.getDslFormat())
                    .taskDecomposition(convertTaskDecomposition(taskResult))
                    .generationTime(LocalDateTime.now())
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .metadata(dslResult.getMetadata())
                    .build();

            // 保存DSL内容到文件
            if (response.getDslContent() != null && response.getDslFormat() != null) {
                try {
                    String processName = fileStorageService.extractProcessName(
                            response.getDslContent(), 
                            response.getDslFormat()
                    );
                    String savedFilePath = fileStorageService.saveDslToFile(
                            response.getDslContent(),
                            response.getDslFormat(),
                            platform,
                            processName
                    );
                    
                    if (savedFilePath != null) {
                        // 将保存的文件路径添加到响应元数据中
                        if (response.getMetadata() == null) {
                            response.setMetadata(new HashMap<>());
                        }
                        response.getMetadata().put("savedFilePath", savedFilePath);
                        log.info("DSL文件已保存到: {}", savedFilePath);
                    }
                } catch (Exception e) {
                    log.warn("保存DSL文件时发生错误，但不影响响应返回", e);
                }
            }

            // 添加平台相关信息到响应
            if (response.getMetadata() == null) {
                response.setMetadata(new HashMap<>());
            }
            response.getMetadata().put("platform", platform);
            response.getMetadata().put("platformName", platformType.getDisplayName());
            response.getMetadata().put("supportedFormats", platformStrategy.getSupportedDslFormats());
            response.getMetadata().put("defaultFormat", platformStrategy.getDefaultDslFormat());

            log.info("工作流DSL生成成功，平台: {}, 耗时: {}ms", platform, response.getProcessingTimeMs());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("工作流生成过程中发生错误", e);
            return ResponseEntity.ok(WorkflowGenerationResponse.builder()
                    .success(false)
                    .error("生成过程中发生错误: " + e.getMessage())
                    .generationTime(LocalDateTime.now())
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build());
        }
    }

    // 辅助方法

    private List<TaskStep> convertDtoTaskStepsToServiceTaskSteps(
            List<WorkflowGenerationRequest.TaskStep> dtoSteps) {
        if (dtoSteps == null) {
            return new ArrayList<>();
        }
        return dtoSteps.stream()
                .map(dto -> TaskStep.builder()
                        .id(dto.getId())
                        .name(dto.getName())
                        .description(dto.getDescription())
                        .type(dto.getType())
                        .dependencies(dto.getDependencies())
                        .build())
                .collect(Collectors.toList());
    }

    private WorkflowGenerationResponse.TaskDecompositionResult convertTaskDecomposition(
            TaskDecompositionResult result) {
        
        List<WorkflowGenerationResponse.TaskStep> steps = result.getSteps().stream()
                .map(step -> WorkflowGenerationResponse.TaskStep.builder()
                        .id(step.getId())
                        .name(step.getName())
                        .description(step.getDescription())
                        .type(step.getType())
                        .parameters(new HashMap<>())
                        .dependencies(step.getDependencies())
                        .build())
                .collect(Collectors.toList());

        return WorkflowGenerationResponse.TaskDecompositionResult.builder()
                .steps(steps)
                .summary(result.getSummary())
                .controlFlow(new HashMap<>())
                .build();
    }

    /**
     * 生成工作流结构（节点和边列表）
     * 新接口：直接返回 nodes 和 edges，不生成文件
     * 
     * @param platform 平台类型
     * @param request 生成请求
     * @return 包含节点和边列表的响应
     */
    @PostMapping("/{platform}/structure")
    public ResponseEntity<WorkflowStructureResponse> generateWorkflowStructure(
            @PathVariable String platform,
            @Valid @RequestBody WorkflowGenerationRequest request) {
        log.info("收到工作流结构生成请求，用户输入: {}, 目标平台: {}", request.getUserInput(), platform);
        long startTime = System.currentTimeMillis();

        try {
            // 验证平台类型
            PlatformType platformType = PlatformType.fromCode(platform);
            if (platformType != PlatformType.IN_BUILDER) {
                return ResponseEntity.ok(WorkflowStructureResponse.builder()
                        .success(false)
                        .error("当前只支持 InBuilder 平台的结构生成")
                        .generationTime(LocalDateTime.now())
                        .processingTimeMs(System.currentTimeMillis() - startTime)
                        .build());
            }

            // 获取 InBuilder 策略
            InBuilderPlatformStrategy inBuilderStrategy = 
                (InBuilderPlatformStrategy) platformStrategyFactory.getStrategy(platformType);
            
            // 第一步：任务分解
            TaskDecompositionResult taskResult;
            List<TaskStep> confirmedSteps;
            
            if (request.getTaskDecompositionResult() != null) {
                // 使用用户确认的步骤
                log.info("使用用户确认的任务步骤");
                confirmedSteps = convertDtoTaskStepsToServiceTaskSteps(
                        request.getTaskDecompositionResult().getSteps());
                taskResult = TaskDecompositionResult.builder()
                        .success(true)
                        .steps(confirmedSteps)
                        .summary(request.getTaskDecompositionResult().getSummary())
                        .build();
            } else {
                // 自动任务分解
                log.info("执行自动任务分解");
                String decompositionPrompt = inBuilderStrategy.buildTaskDecompositionPrompt(
                        request.getUserInput(), "");
                taskResult = taskDecompositionService.decomposeTaskWithPrompt(
                        decompositionPrompt, request.getUserInput(), "");
                confirmedSteps = taskResult.getSteps();
            }

            // 第二步：生成节点和边的 Prompt
            log.info("生成 MCP Prompt");
            String mcpPrompt = inBuilderStrategy.buildMcpGenerationPrompt(
                    request.getUserInput(), 
                    confirmedSteps, 
                    "");
            
            // 第三步：调用 AI 生成节点和边的 JSON
            log.info("调用 AI 模型生成节点和边");
            AIModelService aiService = aiModelFactory.getAvailableModelService();
            String aiResponse = aiService.generateText(mcpPrompt);
            log.info("AI 响应长度: {} 字符", aiResponse.length());
            
            // 第四步：解析节点和边
            log.info("解析节点和边");
            WorkflowStructure workflow = inBuilderStrategy.parseNodesAndEdges(aiResponse);
            
            if (!workflow.isSuccess()) {
                return ResponseEntity.ok(WorkflowStructureResponse.builder()
                        .success(false)
                        .error("解析节点和边失败: " + workflow.getError())
                        .taskDecomposition(convertTaskDecomposition(taskResult))
                        .generationTime(LocalDateTime.now())
                        .processingTimeMs(System.currentTimeMillis() - startTime)
                        .build());
            }
            
            // 第五步：验证节点和边
            log.info("验证节点和边");
            Map<String, Object> validationResult = inBuilderStrategy.validateNodesAndEdges(
                    workflow.getNodes(), 
                    workflow.getEdges());
            
            boolean isValid = (Boolean) validationResult.get("valid");
            List<String> errors = (List<String>) validationResult.get("errors");
            List<String> warnings = (List<String>) validationResult.get("warnings");
            
            // 构建响应
            WorkflowStructureResponse response = WorkflowStructureResponse.builder()
                    .success(isValid)
                    .nodes(workflow.getNodes())
                    .edges(workflow.getEdges())
                    .build();
            
            // 添加元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("platform", platform);
            metadata.put("platformName", platformType.getDisplayName());
            metadata.put("nodeCount", workflow.getNodes().size());
            metadata.put("edgeCount", workflow.getEdges().size());
            metadata.put("aiResponseLength", aiResponse.length());
            response.setMetadata(metadata);
            
            if (!isValid) {
                response.setError("验证失败，请查看 validation 字段了解详情");
                log.warn("工作流验证失败: {}", errors);
            } else {
                log.info("工作流结构生成成功，节点数: {}, 边数: {}, 耗时: {}ms", 
                        workflow.getNodes().size(), 
                        workflow.getEdges().size(), 
                        response.getProcessingTimeMs());
            }
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("工作流结构生成过程中发生错误", e);
            return ResponseEntity.ok(WorkflowStructureResponse.builder()
                    .success(false)
                    .error("生成过程中发生错误: " + e.getMessage())
                    .generationTime(LocalDateTime.now())
                    .processingTimeMs(System.currentTimeMillis() - startTime)
                    .build());
        }
    }
}
