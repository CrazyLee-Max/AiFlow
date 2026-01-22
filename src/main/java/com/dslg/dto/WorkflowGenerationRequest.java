package com.dslg.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/**
 * 工作流生成请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowGenerationRequest {

    /**
     * 用户输入的需求描述
     */
    @NotBlank(message = "用户输入不能为空")
    @Size(max = 2000, message = "用户输入长度不能超过2000字符")
    private String userInput;

    /**
     * 现有工作流列表（可选）
     */
    private List<ExistingWorkflow> existingWorkflows;

    /**
     * 首选DSL格式（可选）
     */
    private String preferredDslFormat;

    /**
     * 业务领域（可选）
     */
    private String domain;

    /**
     * 知识类型（可选）
     */
    private String knowledgeType;

    /**
     * 额外参数（可选）
     */
    private Map<String, Object> parameters;

    /**
     * 确认的任务分解结果（可选，用于多轮对话生成阶段）
     */
    private TaskDecompositionResult taskDecompositionResult;

    /**
     * 现有工作流信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExistingWorkflow {
        /**
         * 工作流名称
         */
        private String name;

        /**
         * DSL内容
         */
        private String dslContent;

        /**
         * DSL格式
         */
        private String dslFormat;

        /**
         * 描述
         */
        private String description;
    }

    /**
     * 任务分解结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskDecompositionResult {
        private List<TaskStep> steps;
        private String summary;
        private Map<String, Object> controlFlow;
    }

    /**
     * 任务步骤
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskStep {
        private String id;
        private String name;
        private String description;
        private String type;
        private Map<String, Object> parameters;
        private List<String> dependencies;
    }
}