package com.dslg.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工作流生成响应DTO
 */
@Data
@Builder
public class WorkflowGenerationResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 生成的DSL内容
     */
    private String dslContent;

    /**
     * DSL格式
     */
    private String dslFormat;

    /**
     * 任务分解结果
     */
    private TaskDecompositionResult taskDecomposition;

    /**
     * 知识检索结果
     */
    private KnowledgeRetrievalResult knowledgeRetrieval;

    /**
     * 生成时间
     */
    private LocalDateTime generationTime;

    /**
     * 处理耗时（毫秒）
     */
    private long processingTimeMs;

    /**
     * 元数据
     */
    private Map<String, Object> metadata;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 警告信息
     */
    private List<String> warnings;

    /**
     * 任务分解结果
     */
    @Data
    @Builder
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
    public static class TaskStep {
        private String id;
        private String name;
        private String description;
        private String type;
        private Map<String, Object> parameters;
        private List<String> dependencies;
    }

    /**
     * 知识检索结果
     */
    @Data
    @Builder
    public static class KnowledgeRetrievalResult {
        private int totalResults;
        private List<KnowledgeItem> items;
        private String extractedKnowledge;
    }

    /**
     * 知识项
     */
    @Data
    @Builder
    public static class KnowledgeItem {
        private String title;
        private String content;
        private String type;
        private String domain;
        private double relevanceScore;
    }
}