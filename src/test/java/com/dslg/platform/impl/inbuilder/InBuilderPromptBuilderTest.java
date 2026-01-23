package com.dslg.platform.impl.inbuilder;

import com.dslg.service.DeviceModelService;
import com.dslg.service.NodeDefinitionService;
import com.dslg.service.model.TaskStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class InBuilderPromptBuilderTest {

    @Mock
    private NodeDefinitionService nodeDefinitionService;

    @Mock
    private DeviceModelService deviceModelService;

    private InBuilderPromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        promptBuilder = new InBuilderPromptBuilder(nodeDefinitionService, deviceModelService);
    }

    @Test
    void testBuildTaskDecompositionPrompt_IncludeDeviceTypes() {
        // Mock DeviceModelService behavior
        when(deviceModelService.getDeviceTypesDescription())
                .thenReturn("可用的设备节点类型：\n  - camera (摄像头节点): powerOn, powerOff\n");

        String prompt = promptBuilder.buildTaskDecompositionPrompt("Test Task", "Context");

        // Verify prompt content
        assertTrue(prompt.contains("[设备节点类型]"));
        assertTrue(prompt.contains("- camera (摄像头节点): powerOn, powerOff"));
    }

    @Test
    void testBuildMcpGenerationPrompt_IncludeDeviceNodeDefinition() {
        // Mock DeviceModelService behavior
        Set<String> categories = new HashSet<>();
        categories.add("camera");
        when(deviceModelService.getAllDeviceCategories()).thenReturn(categories);
        when(deviceModelService.isDeviceTypeSupported("camera")).thenReturn(true);
        when(deviceModelService.buildDeviceNodeDefinition("camera"))
                .thenReturn("## 设备节点: 摄像头节点 (kind: camera)\n...");

        // Prepare input
        TaskStep step = TaskStep.builder()
                .id("step1")
                .name("Use Camera")
                .type("camera")
                .build();
        List<TaskStep> steps = Collections.singletonList(step);

        String prompt = promptBuilder.buildMcpGenerationPrompt("Use camera", steps, "Context");

        // Verify prompt content
        assertTrue(prompt.contains("设备节点: [camera]"));
        assertTrue(prompt.contains("## 设备节点: 摄像头节点 (kind: camera)"));
        assertTrue(prompt.contains("[重要] inputParams 填写规范示例"));
    }
}
