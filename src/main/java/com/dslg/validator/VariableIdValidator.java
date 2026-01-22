package com.dslg.validator;

import com.dslg.service.model.Node;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 变量ID验证器
 * 
 * 验证节点中变量的id和variableId字段是否正确设置
 */
@Slf4j
@Component
public class VariableIdValidator {
    
    /**
     * 验证所有节点的变量定义和引用
     * 
     * @param nodes 节点列表
     * @return 验证结果
     */
    public ValidationResult validateVariableIds(List<Node> nodes) {
        log.info("=== 开始验证变量ID ===");
        
        ValidationResult result = new ValidationResult();
        
        // 第一步：收集所有定义的变量ID
        Map<String, VariableDefinition> definedVariables = new HashMap<>();
        collectDefinedVariables(nodes, definedVariables, result);
        
        // 第二步：验证所有变量引用
        validateVariableReferences(nodes, definedVariables, result);
        
        log.info("=== 变量ID验证完成 ===");
        log.info("定义的变量数: {}", definedVariables.size());
        log.info("错误数: {}", result.getErrors().size());
        log.info("警告数: {}", result.getWarnings().size());
        
        return result;
    }
    
    /**
     * 收集所有定义的变量
     */
    private void collectDefinedVariables(
            List<Node> nodes, 
            Map<String, VariableDefinition> definedVariables,
            ValidationResult result) {
        
        for (Node node : nodes) {
            String nodeId = node.getId();
            String nodeKind = node.getKind();
            
            // 检查 inputParams (start节点)
            if (node.getInputParams() != null) {
                for (Map<String, Object> param : node.getInputParams()) {
                    validateAndCollectVariable(param, nodeId, nodeKind, "inputParams", 
                            definedVariables, result);
                }
            }
            
            // 检查 outputParams (variableDef, end节点)
            if (node.getOutputParams() != null) {
                for (Map<String, Object> param : node.getOutputParams()) {
                    validateAndCollectVariable(param, nodeId, nodeKind, "outputParams", 
                            definedVariables, result);
                }
            }
        }
    }
    
    /**
     * 验证并收集单个变量定义
     */
    private void validateAndCollectVariable(
            Map<String, Object> param,
            String nodeId,
            String nodeKind,
            String paramType,
            Map<String, VariableDefinition> definedVariables,
            ValidationResult result) {
        
        String code = (String) param.get("code");
        String id = (String) param.get("id");
        
        if (code == null || code.isEmpty()) {
            result.addError(String.format(
                    "节点 %s 的 %s 中存在缺少 code 字段的变量", nodeId, paramType));
            return;
        }
        
        // 验证是否有id字段
        if (id == null || id.isEmpty()) {
            result.addError(String.format(
                    "节点 %s (%s) 的变量 '%s' 缺少 id 字段", nodeId, nodeKind, code));
            return;
        }
        
        // 验证id格式是否符合规范: 节点ID_变量名
        String expectedIdPrefix = nodeId + "_";
        if (!id.startsWith(expectedIdPrefix)) {
            result.addWarning(String.format(
                    "节点 %s (%s) 的变量 '%s' 的 id '%s' 不符合推荐格式 '%s变量名'",
                    nodeId, nodeKind, code, id, expectedIdPrefix));
        }
        
        // 检查id是否重复
        if (definedVariables.containsKey(id)) {
            VariableDefinition existing = definedVariables.get(id);
            result.addError(String.format(
                    "变量ID重复: '%s' 在节点 %s 和节点 %s 中都有定义",
                    id, existing.nodeId, nodeId));
        } else {
            definedVariables.put(id, new VariableDefinition(nodeId, nodeKind, code, id, paramType));
            log.debug("收集到变量定义: id={}, nodeId={}, code={}", id, nodeId, code);
        }
    }
    
    /**
     * 验证所有变量引用
     */
    private void validateVariableReferences(
            List<Node> nodes,
            Map<String, VariableDefinition> definedVariables,
            ValidationResult result) {
        
        for (Node node : nodes) {
            String nodeId = node.getId();
            
            // 检查 outputParams 中的 valueExpr
            if (node.getOutputParams() != null) {
                for (Map<String, Object> param : node.getOutputParams()) {
                    Object valueExpr = param.get("valueExpr");
                    if (valueExpr instanceof Map) {
                        validateValueExpr((Map<String, Object>) valueExpr, nodeId, 
                                "outputParams", definedVariables, result);
                    }
                }
            }
            
            // 检查 batchAssignValue 节点的 expresses
            if ("batchAssignValue".equals(node.getKind())) {
                Object expressesObj = node.getAdditionalProperties().get("expresses");
                if (expressesObj instanceof List) {
                    List<Map<String, Object>> expresses = (List<Map<String, Object>>) expressesObj;
                    for (Map<String, Object> express : expresses) {
                        // 检查 leftExpress
                        Object leftExpress = express.get("leftExpress");
                        if (leftExpress instanceof Map) {
                            validateValueExpr((Map<String, Object>) leftExpress, nodeId, 
                                    "expresses.leftExpress", definedVariables, result);
                        }
                        // 检查 rightExpress
                        Object rightExpress = express.get("rightExpress");
                        if (rightExpress instanceof Map) {
                            validateValueExpr((Map<String, Object>) rightExpress, nodeId, 
                                    "expresses.rightExpress", definedVariables, result);
                        }
                    }
                }
            }
            
            // 检查 selector 节点的 branches
            if ("selector".equals(node.getKind())) {
                Object branchesObj = node.getAdditionalProperties().get("branches");
                if (branchesObj instanceof List) {
                    List<Map<String, Object>> branches = (List<Map<String, Object>>) branchesObj;
                    for (Map<String, Object> branch : branches) {
                        Object conditionExpr = branch.get("conditionExpr");
                        if (conditionExpr instanceof Map) {
                            validateConditionExpr((Map<String, Object>) conditionExpr, nodeId, 
                                    definedVariables, result);
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 验证 valueExpr 中的 variableId
     */
    private void validateValueExpr(
            Map<String, Object> valueExpr,
            String nodeId,
            String location,
            Map<String, VariableDefinition> definedVariables,
            ValidationResult result) {
        
        String kind = (String) valueExpr.get("kind");
        
        // 只验证 nodeVariable 类型
        if (!"nodeVariable".equals(kind)) {
            return;
        }
        
        String nodeCode = (String) valueExpr.get("nodeCode");
        String variable = (String) valueExpr.get("variable");
        String variableId = (String) valueExpr.get("variableId");
        
        // 验证是否有 variableId 字段
        if (variableId == null || variableId.isEmpty()) {
            result.addError(String.format(
                    "节点 %s 的 %s 中引用变量 '%s' (来自节点 %s) 时缺少 variableId 字段",
                    nodeId, location, variable, nodeCode));
            return;
        }
        
        // 验证 variableId 是否存在于定义中
        if (!definedVariables.containsKey(variableId)) {
            result.addError(String.format(
                    "节点 %s 的 %s 中引用了不存在的 variableId: '%s'",
                    nodeId, location, variableId));
            return;
        }
        
        // 验证 variableId 对应的变量名是否匹配
        VariableDefinition def = definedVariables.get(variableId);
        if (!def.code.equals(variable)) {
            result.addWarning(String.format(
                    "节点 %s 的 %s 中 variableId '%s' 对应的变量名应该是 '%s'，但实际引用的是 '%s'",
                    nodeId, location, variableId, def.code, variable));
        }
        
        log.debug("验证变量引用: nodeId={}, variableId={}, variable={}", nodeId, variableId, variable);
    }
    
    /**
     * 验证 conditionExpr 中的变量引用
     */
    private void validateConditionExpr(
            Map<String, Object> conditionExpr,
            String nodeId,
            Map<String, VariableDefinition> definedVariables,
            ValidationResult result) {
        
        Object expressesObj = conditionExpr.get("expresses");
        if (!(expressesObj instanceof List)) {
            return;
        }
        
        List<Map<String, Object>> expresses = (List<Map<String, Object>>) expressesObj;
        for (Map<String, Object> express : expresses) {
            // 检查 leftExpress
            Object leftExpress = express.get("leftExpress");
            if (leftExpress instanceof Map) {
                validateValueExpr((Map<String, Object>) leftExpress, nodeId, 
                        "branches.conditionExpr.leftExpress", definedVariables, result);
            }
            
            // 检查 rightExpress
            Object rightExpress = express.get("rightExpress");
            if (rightExpress instanceof Map) {
                validateValueExpr((Map<String, Object>) rightExpress, nodeId, 
                        "branches.conditionExpr.rightExpress", definedVariables, result);
            }
        }
    }
    
    /**
     * 变量定义信息
     */
    private static class VariableDefinition {
        String nodeId;
        String nodeKind;
        String code;
        String id;
        String paramType;
        
        VariableDefinition(String nodeId, String nodeKind, String code, String id, String paramType) {
            this.nodeId = nodeId;
            this.nodeKind = nodeKind;
            this.code = code;
            this.id = id;
            this.paramType = paramType;
        }
    }
    
    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        
        public void addError(String error) {
            errors.add(error);
            log.warn("验证错误: {}", error);
        }
        
        public void addWarning(String warning) {
            warnings.add(warning);
            log.info("验证警告: {}", warning);
        }
        
        public boolean isValid() {
            return errors.isEmpty();
        }
        
        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
        
        public List<String> getWarnings() {
            return new ArrayList<>(warnings);
        }
    }
}
