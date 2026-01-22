package com.dslg.service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * InBuilder 流程连线模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Edge {
    
    /**
     * 源节点 ID
     */
    private String sourceNodeId;
    
    /**
     * 目标节点 ID
     */
    private String targetNodeId;
    
    /**
     * 源节点的输出端口 (默认为 'output')
     */
    private String sourcePort;
    
    /**
     * 目标节点的输入端口 (默认为 'input')
     */
    private String targetPort;
}
