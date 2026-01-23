package com.dslg.platform.impl;

import com.dslg.platform.impl.inbuilder.InBuilderPromptBuilder;
import com.dslg.platform.impl.inbuilder.InBuilderResponseParser;
import com.dslg.platform.impl.inbuilder.InBuilderVariableReferenceFixer;
import com.dslg.service.DeviceModelService;
import com.dslg.service.NodeDefinitionService;
import com.dslg.service.model.Edge;
import com.dslg.service.model.Node;
import com.dslg.validator.VariableIdValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class InBuilderPlatformStrategyTest {

    @Mock
    private NodeDefinitionService nodeDefinitionService;
    @Mock
    private DeviceModelService deviceModelService;
    @Mock
    private VariableIdValidator variableIdValidator;
    @Mock
    private InBuilderPromptBuilder promptBuilder;
    @Mock
    private InBuilderResponseParser responseParser;
    @Mock
    private InBuilderVariableReferenceFixer variableFixer;

    private InBuilderPlatformStrategy platformStrategy;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        platformStrategy = new InBuilderPlatformStrategy(
                nodeDefinitionService,
                deviceModelService,
                variableIdValidator,
                promptBuilder,
                responseParser,
                variableFixer
        );

        // Mock VariableIdValidator to always pass
        when(variableIdValidator.validateVariableIds(any())).thenReturn(new VariableIdValidator.ValidationResult());
    }

    @Test
    void testValidateNodesAndEdges_WithDeviceNode() {
        // Setup supported types
        when(nodeDefinitionService.isNodeTypeSupported("start")).thenReturn(true);
        when(nodeDefinitionService.isNodeTypeSupported("end")).thenReturn(true);
        when(nodeDefinitionService.isNodeTypeSupported("camera")).thenReturn(false);
        
        // This is the key: device type IS supported by DeviceModelService
        when(deviceModelService.isDeviceTypeSupported("camera")).thenReturn(true);

        // Create nodes
        List<Node> nodes = new ArrayList<>();
        nodes.add(Node.builder().id("start").kind("start").build());
        nodes.add(Node.builder().id("cam1").kind("camera").build()); // Device node
        nodes.add(Node.builder().id("end").kind("end").build());

        // Create edges
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge("start", "cam1", "output", "input"));
        edges.add(new Edge("cam1", "end", "output", "input"));

        // Execute validation
        Map<String, Object> result = platformStrategy.validateNodesAndEdges(nodes, edges);

        // Verify
        assertTrue((Boolean) result.get("valid"), "Validation should pass for supported device node");
        List<String> errors = (List<String>) result.get("errors");
        assertTrue(errors.isEmpty(), "There should be no errors");
    }

    @Test
    void testValidateNodesAndEdges_WithUnsupportedNode() {
        // Setup supported types
        when(nodeDefinitionService.isNodeTypeSupported("start")).thenReturn(true);
        when(nodeDefinitionService.isNodeTypeSupported("end")).thenReturn(true);
        when(nodeDefinitionService.isNodeTypeSupported("unknown")).thenReturn(false);
        when(deviceModelService.isDeviceTypeSupported("unknown")).thenReturn(false);

        // Create nodes
        List<Node> nodes = new ArrayList<>();
        nodes.add(Node.builder().id("start").kind("start").build());
        nodes.add(Node.builder().id("unk1").kind("unknown").build());
        nodes.add(Node.builder().id("end").kind("end").build());

        // Create edges
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge("start", "unk1", "output", "input"));
        edges.add(new Edge("unk1", "end", "output", "input"));

        // Execute validation
        Map<String, Object> result = platformStrategy.validateNodesAndEdges(nodes, edges);

        // Verify
        assertFalse((Boolean) result.get("valid"), "Validation should fail for unsupported node");
        List<String> errors = (List<String>) result.get("errors");
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("不支持的类型: unknown")));
    }
}
