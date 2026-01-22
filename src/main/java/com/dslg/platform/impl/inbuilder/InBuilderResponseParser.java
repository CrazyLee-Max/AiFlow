package com.dslg.platform.impl.inbuilder;

import com.dslg.service.model.Edge;
import com.dslg.service.model.Node;
import com.dslg.service.model.WorkflowStructure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * InBuilder 响应解析服务
 * 
 * InBuilder 平台特定的响应解析逻辑
 */
@Slf4j
@Component
public class InBuilderResponseParser {
    
    private final ObjectMapper objectMapper;
    
    public InBuilderResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * 解析 AI 返回的节点和边的 JSON
     * 
     * @param aiResponse AI 返回的 JSON 字符串
     * @return WorkflowStructure 包含节点列表和边列表
     */
    public WorkflowStructure parseNodesAndEdges(String aiResponse) {
        log.info("=== 开始解析 InBuilder 节点和边 ===");
        
        List<Node> nodes = new ArrayList<>();
        List<Edge> edges = new ArrayList<>();
        
        try {
            // 提取 JSON 内容
            String jsonContent = extractJsonFromResponse(aiResponse);
            JsonNode rootNode = objectMapper.readTree(jsonContent);
            
            // 解析 nodes 数组
            if (rootNode.has("nodes") && rootNode.get("nodes").isArray()) {
                JsonNode nodesArray = rootNode.get("nodes");
                for (JsonNode nodeJson : nodesArray) {
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
                JsonNode edgesArray = rootNode.get("edges");
                for (JsonNode edgeJson : edgesArray) {
                    Edge edge = parseEdge(edgeJson);
                    edges.add(edge);
                    log.debug("解析连线: {} -> {}", 
                            edge.getSourceNodeId(), edge.getTargetNodeId());
                }
                log.info("成功解析 {} 条连线", edges.size());
            } else {
                log.warn("AI 返回的 JSON 中没有 edges 数组");
            }
            
            log.info("=== InBuilder 节点和边解析完成 ===");
            
            return WorkflowStructure.builder()
                    .nodes(nodes)
                    .edges(edges)
                    .success(true)
                    .build();
            
        } catch (Exception e) {
            log.error("解析 InBuilder 节点和边时发生异常", e);
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
    private Node parseNode(JsonNode nodeJson) {
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
    private Edge parseEdge(JsonNode edgeJson) {
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
     * 从 AI 响应中提取 JSON 内容
     * 处理可能的 markdown 代码块包装
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return "{}";
        }
        
        String trimmed = response.trim();
        
        // 如果被 markdown 代码块包装，提取内容
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastBackticks = trimmed.lastIndexOf("```");
            
            if (firstNewline != -1 && lastBackticks > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastBackticks).trim();
            }
        }
        
        return trimmed;
    }
}
