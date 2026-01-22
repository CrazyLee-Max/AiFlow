package com.dslg.dto;

import com.dslg.service.model.Edge;
import com.dslg.service.model.Node;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 工作流结构响应（返回节点和边列表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStructureResponse {
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 节点列表
     */
    private List<Node> nodes;
    
    /**
     * 边列表
     */
    private List<Edge> edges;
    
    /**
     * 任务分解结果（可选，用于展示分解步骤）
     */
    private WorkflowGenerationResponse.TaskDecompositionResult taskDecomposition;
    
    /**
     * 错误信息
     */
    private String error;
    
    /**
     * 生成时间
     */
    private LocalDateTime generationTime;
    
    /**
     * 处理耗时（毫秒）
     */
    private Long processingTimeMs;
    
    /**
     * 元数据
     */
    private Map<String, Object> metadata;
    
    /**
     * 验证结果
     */
    private ValidationResult validation;
    
    /**
     * 验证结果内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {
        private boolean valid;
        private List<String> errors;
        private List<String> warnings;
    }
}
