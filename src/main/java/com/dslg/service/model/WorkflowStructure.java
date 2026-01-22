package com.dslg.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 工作流结构模型，包含节点列表和边列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStructure {
    
    /**
     * 节点列表
     */
    private List<Node> nodes;
    
    /**
     * 边列表
     */
    private List<Edge> edges;
    
    /**
     * 是否解析成功
     */
    private boolean success;
    
    /**
     * 错误信息（如果解析失败）
     */
    private String error;
}
