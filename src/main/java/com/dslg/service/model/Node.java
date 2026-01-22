package com.dslg.service.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * InBuilder 流程节点模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Node {
    
    /**
     * 节点唯一标识
     */
    private String id;
    
    /**
     * 节点显示名称
     */
    private String name;
    
    /**
     * 节点类型 (start, end, variableDef, batchAssignValue, selector)
     */
    private String kind;
    
    /**
     * 输入端口列表
     */
    private List<String> inputPorts;
    
    /**
     * 输出端口列表
     */
    private List<String> outputPorts;
    
    /**
     * 输入参数列表
     */
    private List<Map<String, Object>> inputParams;
    
    /**
     * 输出参数列表
     */
    private List<Map<String, Object>> outputParams;
    
    /**
     * 其他动态属性 (如 selector 的 branches, batchAssignValue 的 expresses 等)
     */
    @Builder.Default
    private Map<String, Object> additionalProperties = new HashMap<>();
    
    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        this.additionalProperties.put(name, value);
    }
    
    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return this.additionalProperties;
    }
}
