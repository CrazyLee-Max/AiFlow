package com.dslg.service;

import com.dslg.platform.impl.inbuilder.InBuilderVariableReferenceFixer;
import com.dslg.service.model.Edge;
import com.dslg.service.model.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InBuilder 变量引用修复服务测试
 */
class VariableReferenceFixerServiceTest {
    
    private InBuilderVariableReferenceFixer fixer;
    
    @BeforeEach
    void setUp() {
        fixer = new InBuilderVariableReferenceFixer();
    }
    
    @Test
    void testFixVariableReferenceInSameNode() {
        // 测试场景：在同一个 batchAssignValue 节点内，第二个赋值引用第一个赋值修改的变量
        
        // 创建 variableDef 节点
        Node variableDef = Node.builder()
                .id("variableDef_1")
                .kind("variableDef")
                .name("初始化变量")
                .inputPorts(Arrays.asList("input"))
                .outputPorts(Arrays.asList("output"))
                .inputParams(new ArrayList<>())
                .outputParams(Arrays.asList(
                    createParam("coffeeResult", "variableDef_1_coffeeResult", ""),
                    createParam("finalResult", "variableDef_1_finalResult", "")
                ))
                .build();
        
        // 创建 batchAssignValue 节点（包含问题）
        Map<String, Object> express1 = new HashMap<>();
        express1.put("kind", "assignValue");
        express1.put("leftExpress", createNodeVariable("variableDef_variableDef_1", "coffeeResult", "variableDef_1_coffeeResult"));
        express1.put("rightExpress", createStringConst("咖啡已预订"));
        
        Map<String, Object> express2 = new HashMap<>();
        express2.put("kind", "assignValue");
        express2.put("leftExpress", createNodeVariable("variableDef_variableDef_1", "finalResult", "variableDef_1_finalResult"));
        // 问题：这里引用的是 variableDef_1，但应该引用 batchAssignValue_1
        express2.put("rightExpress", createNodeVariable("variableDef_variableDef_1", "coffeeResult", "variableDef_1_coffeeResult"));
        
        Map<String, Object> additionalProps = new HashMap<>();
        additionalProps.put("expresses", Arrays.asList(express1, express2));
        
        Node batchAssign = Node.builder()
                .id("batchAssignValue_1")
                .kind("batchAssignValue")
                .name("赋值操作")
                .inputPorts(Arrays.asList("input"))
                .outputPorts(Arrays.asList("output"))
                .inputParams(new ArrayList<>())
                .outputParams(new ArrayList<>())
                .additionalProperties(additionalProps)
                .build();
        
        List<Node> nodes = Arrays.asList(variableDef, batchAssign);
        List<Edge> edges = Arrays.asList(
            Edge.builder()
                .sourceNodeId("variableDef_1")
                .targetNodeId("batchAssignValue_1")
                .sourcePort("output")
                .targetPort("input")
                .build()
        );
        
        // 执行修复
        List<Node> fixedNodes = fixer.fixVariableReferences(nodes, edges);
        
        // 验证修复结果
        Node fixedBatchAssign = fixedNodes.stream()
                .filter(n -> "batchAssignValue_1".equals(n.getId()))
                .findFirst()
                .orElse(null);
        
        assertNotNull(fixedBatchAssign);
        
        List<Map<String, Object>> expresses = (List<Map<String, Object>>) 
                fixedBatchAssign.getAdditionalProperties().get("expresses");
        
        // 第二个表达式的右侧应该引用 batchAssignValue_1
        Map<String, Object> rightExpr = (Map<String, Object>) expresses.get(1).get("rightExpress");
        String nodeCode = (String) rightExpr.get("nodeCode");
        
        assertEquals("batchAssignValue_batchAssignValue_1", nodeCode, 
                "应该引用当前节点 batchAssignValue_1，而不是 variableDef_1");
    }
    
    private Map<String, Object> createParam(String code, String id, String value) {
        Map<String, Object> param = new HashMap<>();
        param.put("code", code);
        param.put("id", id);
        
        Map<String, Object> type = new HashMap<>();
        type.put("source", "default");
        type.put("typeId", "string");
        type.put("typeName", "String");
        param.put("type", type);
        
        if (value != null) {
            Map<String, Object> valueExpr = new HashMap<>();
            valueExpr.put("kind", "stringConst");
            valueExpr.put("value", value);
            param.put("valueExpr", valueExpr);
        }
        
        return param;
    }
    
    private Map<String, Object> createNodeVariable(String nodeCode, String variable, String variableId) {
        Map<String, Object> expr = new HashMap<>();
        expr.put("kind", "nodeVariable");
        expr.put("nodeCode", nodeCode);
        expr.put("variable", variable);
        expr.put("variableId", variableId);
        return expr;
    }
    
    private Map<String, Object> createStringConst(String value) {
        Map<String, Object> expr = new HashMap<>();
        expr.put("kind", "stringConst");
        expr.put("value", value);
        return expr;
    }
}
