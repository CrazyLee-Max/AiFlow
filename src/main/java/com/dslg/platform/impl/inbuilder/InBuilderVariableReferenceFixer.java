package com.dslg.platform.impl.inbuilder;

import com.dslg.service.model.Node;
import com.dslg.service.model.Edge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * InBuilder 变量引用修复服务
 * 
 * InBuilder 平台特定的变量引用修复逻辑
 * 
 * 功能：修复 LLM 生成的流程中变量引用不正确的问题
 * 场景：当变量在流程中被修改后，后续引用应该指向最后修改它的节点
 */
@Slf4j
@Component
public class InBuilderVariableReferenceFixer {
    
    /**
     * 修复工作流中的变量引用
     * 
     * @param nodes 节点列表
     * @param edges 边列表
     * @return 修复后的节点列表
     */
    public List<Node> fixVariableReferences(List<Node> nodes, List<Edge> edges) {
        log.info("=== 开始修复 InBuilder 变量引用 ===");
        
        // 1. 构建拓扑排序
        List<Node> sortedNodes = topologicalSort(nodes, edges);
        
        // 2. 追踪每个变量最后被修改的节点
        Map<String, String> variableLastModifiedBy = new HashMap<>();
        
        // 3. 遍历节点，修复引用
        int fixCount = 0;
        for (Node node : sortedNodes) {
            String nodeKind = node.getKind();
            
            switch (nodeKind) {
                case "variableDef":
                    recordVariableDefinitions(node, variableLastModifiedBy);
                    break;
                    
                case "batchAssignValue":
                    fixCount += fixBatchAssignValueNode(node, variableLastModifiedBy);
                    break;
                    
                case "selector":
                    fixCount += fixSelectorNode(node, variableLastModifiedBy);
                    break;
                    
                case "end":
                    fixCount += fixEndNode(node, variableLastModifiedBy);
                    break;
            }
        }
        
        log.info("=== InBuilder 变量引用修复完成，共修复 {} 处 ===", fixCount);
        return nodes;
    }
    
    /**
     * 记录 variableDef 节点定义的变量
     */
    private void recordVariableDefinitions(Node node, Map<String, String> tracker) {
        List<Map<String, Object>> outputParams = node.getOutputParams();
        if (outputParams == null) return;
        
        for (Map<String, Object> param : outputParams) {
            String variableId = (String) param.get("id");
            if (variableId != null) {
                tracker.put(variableId, node.getId());
                log.debug("记录变量定义: {} 在节点 {}", variableId, node.getId());
            }
        }
    }
    
    /**
     * 修复 batchAssignValue 节点中的变量引用
     */
    private int fixBatchAssignValueNode(Node node, Map<String, String> tracker) {
        Map<String, Object> additionalProps = node.getAdditionalProperties();
        if (additionalProps == null || !additionalProps.containsKey("expresses")) {
            return 0;
        }
        
        List<Map<String, Object>> expresses = (List<Map<String, Object>>) additionalProps.get("expresses");
        if (expresses == null) return 0;
        
        int fixCount = 0;
        // 临时记录当前节点内的修改
        Set<String> modifiedInCurrentNode = new HashSet<>();
        
        for (Map<String, Object> expr : expresses) {
            // 修复右侧表达式的引用
            Map<String, Object> rightExpr = (Map<String, Object>) expr.get("rightExpress");
            if (rightExpr != null) {
                fixCount += fixVariableReference(rightExpr, tracker, modifiedInCurrentNode, node.getId());
            }
            
            // 记录左侧变量被修改
            Map<String, Object> leftExpr = (Map<String, Object>) expr.get("leftExpress");
            if (leftExpr != null && "nodeVariable".equals(leftExpr.get("kind"))) {
                String variableId = (String) leftExpr.get("variableId");
                if (variableId != null) {
                    // 标记为在当前节点被修改
                    modifiedInCurrentNode.add(variableId);
                    // 更新全局追踪
                    tracker.put(variableId, node.getId());
                    log.debug("变量 {} 在节点 {} 中被修改", variableId, node.getId());
                }
            }
        }
        
        return fixCount;
    }
    
    /**
     * 修复变量引用的 nodeCode
     * 
     * @return 修复次数
     */
    private int fixVariableReference(Map<String, Object> expr, 
                                     Map<String, String> tracker,
                                     Set<String> modifiedInCurrentNode,
                                     String currentNodeId) {
        if (!"nodeVariable".equals(expr.get("kind"))) {
            return 0;
        }
        
        String variableId = (String) expr.get("variableId");
        if (variableId == null) {
            return 0;
        }
        
        // 检查是否在当前节点内已经被修改
        String correctNodeId;
        if (modifiedInCurrentNode.contains(variableId)) {
            // 引用当前节点
            correctNodeId = currentNodeId;
            log.debug("修复引用: 变量 {} 引用当前节点 {}", variableId, currentNodeId);
        } else {
            // 引用最后修改该变量的节点
            correctNodeId = tracker.get(variableId);
            if (correctNodeId == null) {
                log.warn("变量 {} 没有找到定义节点", variableId);
                return 0;
            }
        }
        
        // 生成正确的 nodeCode
        String correctNodeCode = generateNodeCode(correctNodeId);
        String currentNodeCode = (String) expr.get("nodeCode");
        
        if (!correctNodeCode.equals(currentNodeCode)) {
            log.info("修复 nodeCode: {} -> {} (变量: {})", 
                    currentNodeCode, correctNodeCode, variableId);
            expr.put("nodeCode", correctNodeCode);
            return 1;
        }
        
        return 0;
    }
    
    /**
     * 修复 selector 节点
     */
    private int fixSelectorNode(Node node, Map<String, String> tracker) {
        Map<String, Object> additionalProps = node.getAdditionalProperties();
        if (additionalProps == null || !additionalProps.containsKey("branches")) {
            return 0;
        }
        
        List<Map<String, Object>> branches = (List<Map<String, Object>>) additionalProps.get("branches");
        if (branches == null) return 0;
        
        int fixCount = 0;
        for (Map<String, Object> branch : branches) {
            Map<String, Object> conditionExpr = (Map<String, Object>) branch.get("conditionExpr");
            if (conditionExpr != null) {
                fixCount += fixExpressionRecursive(conditionExpr, tracker, new HashSet<>(), node.getId());
            }
        }
        
        return fixCount;
    }
    
    /**
     * 修复 end 节点
     */
    private int fixEndNode(Node node, Map<String, String> tracker) {
        List<Map<String, Object>> outputParams = node.getOutputParams();
        if (outputParams == null) return 0;
        
        int fixCount = 0;
        for (Map<String, Object> param : outputParams) {
            Map<String, Object> valueExpr = (Map<String, Object>) param.get("valueExpr");
            if (valueExpr != null) {
                fixCount += fixVariableReference(valueExpr, tracker, new HashSet<>(), node.getId());
            }
        }
        
        return fixCount;
    }
    
    /**
     * 递归修复表达式中的变量引用
     */
    private int fixExpressionRecursive(Map<String, Object> expr,
                                       Map<String, String> tracker,
                                       Set<String> modifiedInCurrentNode,
                                       String currentNodeId) {
        if (expr == null) return 0;
        
        int fixCount = 0;
        String kind = (String) expr.get("kind");
        
        if ("nodeVariable".equals(kind)) {
            fixCount += fixVariableReference(expr, tracker, modifiedInCurrentNode, currentNodeId);
        } else if ("logic".equals(kind) || "compare".equals(kind)) {
            // 递归处理嵌套表达式
            Object expresses = expr.get("expresses");
            if (expresses instanceof List) {
                for (Object subExpr : (List<?>) expresses) {
                    if (subExpr instanceof Map) {
                        fixCount += fixExpressionRecursive((Map<String, Object>) subExpr, 
                                             tracker, modifiedInCurrentNode, currentNodeId);
                    }
                }
            }
            
            Map<String, Object> leftExpr = (Map<String, Object>) expr.get("leftExpress");
            if (leftExpr != null) {
                fixCount += fixExpressionRecursive(leftExpr, tracker, modifiedInCurrentNode, currentNodeId);
            }
            
            Map<String, Object> rightExpr = (Map<String, Object>) expr.get("rightExpress");
            if (rightExpr != null) {
                fixCount += fixExpressionRecursive(rightExpr, tracker, modifiedInCurrentNode, currentNodeId);
            }
        }
        
        return fixCount;
    }
    
    /**
     * 生成 nodeCode
     * 格式: kind_nodeId
     * 例如: variableDef_1 -> variableDef_variableDef_1
     */
    private String generateNodeCode(String nodeId) {
        // 从 nodeId 提取 kind
        String kind = nodeId.contains("_") ? nodeId.substring(0, nodeId.indexOf("_")) : nodeId;
        return kind + "_" + nodeId;
    }
    
    /**
     * 拓扑排序
     */
    private List<Node> topologicalSort(List<Node> nodes, List<Edge> edges) {
        Map<String, Node> nodeMap = new HashMap<>();
        for (Node node : nodes) {
            nodeMap.put(node.getId(), node);
        }
        
        Map<String, List<String>> graph = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        
        // 初始化
        for (Node node : nodes) {
            graph.put(node.getId(), new ArrayList<>());
            inDegree.put(node.getId(), 0);
        }
        
        // 构建图
        for (Edge edge : edges) {
            graph.get(edge.getSourceNodeId()).add(edge.getTargetNodeId());
            inDegree.put(edge.getTargetNodeId(), 
                        inDegree.get(edge.getTargetNodeId()) + 1);
        }
        
        // 拓扑排序
        Queue<String> queue = new LinkedList<>();
        for (String nodeId : inDegree.keySet()) {
            if (inDegree.get(nodeId) == 0) {
                queue.offer(nodeId);
            }
        }
        
        List<Node> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            sorted.add(nodeMap.get(nodeId));
            
            for (String neighbor : graph.get(nodeId)) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.offer(neighbor);
                }
            }
        }
        
        return sorted;
    }
}
