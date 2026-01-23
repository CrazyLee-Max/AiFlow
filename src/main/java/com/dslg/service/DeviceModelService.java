package com.dslg.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 设备模型管理服务
 * 
 * 职责：
 * 1. 扫描并加载 device 目录下的设备模型文件
 * 2. 维护设备模型映射表 Map<category, DeviceModel>
 * 3. 提供设备信息查询接口
 * 4. 动态构造设备节点定义
 */
@Slf4j
@Service
public class DeviceModelService {
    
    @Value("${node.definitions.device.path:src/main/resources/node-definitions/device}")
    private String deviceDefinitionsPath;
    
    private final ObjectMapper objectMapper;
    private final Map<String, DeviceModel> deviceModels = new HashMap<>();
    
    public DeviceModelService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    /**
     * 初始化：扫描并加载所有设备模型
     */
    @PostConstruct
    public void init() {
        log.info("=== 开始加载设备模型 ===");
        loadDeviceModels();
        log.info("=== 设备模型加载完成，共加载 {} 个设备类型 ===", deviceModels.size());
    }
    
    /**
     * 扫描并加载设备模型文件
     */
    private void loadDeviceModels() {
        File deviceDir = new File(deviceDefinitionsPath);
        
        if (!deviceDir.exists() || !deviceDir.isDirectory()) {
            log.warn("设备定义目录不存在: {}", deviceDefinitionsPath);
            return;
        }
        
        File[] files = deviceDir.listFiles((dir, name) -> 
            name.endsWith(".json") && 
            !name.equals("deviceNode.json") && 
            !name.equals("eventListen.json"));
        
        if (files == null || files.length == 0) {
            log.warn("未找到设备模型文件");
            return;
        }
        
        for (File file : files) {
            try {
                loadDeviceModel(file);
            } catch (Exception e) {
                log.error("加载设备模型文件失败: {}", file.getName(), e);
            }
        }
    }
    
    /**
     * 加载单个设备模型文件
     */
    private void loadDeviceModel(File file) throws IOException {
        log.info("加载设备模型: {}", file.getName());
        
        JsonNode root = objectMapper.readTree(file);
        
        String modelName = root.path("modelName").asText();
        String category = root.path("category").asText();
        
        if (category.isEmpty()) {
            log.warn("设备模型缺少 category 字段: {}", file.getName());
            return;
        }
        
        DeviceModel model = new DeviceModel();
        model.setModelName(modelName);
        model.setCategory(category);
        model.setFileName(file.getName());
        
        // 解析 actions
        JsonNode actionsNode = root.path("actions");
        if (actionsNode.isObject()) {
            Map<String, DeviceAction> actions = new HashMap<>();
            actionsNode.fields().forEachRemaining(entry -> {
                String actionName = entry.getKey();
                JsonNode actionNode = entry.getValue();
                
                DeviceAction action = new DeviceAction();
                action.setName(actionName);
                action.setDescription(actionNode.path("description").asText());
                
                // 解析 arguments
                JsonNode argsNode = actionNode.path("arguments");
                if (argsNode.isObject()) {
                    Map<String, DeviceArgument> arguments = new HashMap<>();
                    argsNode.fields().forEachRemaining(argEntry -> {
                        String argName = argEntry.getKey();
                        JsonNode argNode = argEntry.getValue();
                        
                        DeviceArgument argument = new DeviceArgument();
                        argument.setName(argName);
                        argument.setType(argNode.path("type").asText());
                        argument.setDescription(argNode.path("description").asText());
                        argument.setUnit(argNode.path("unit").asText(""));
                        argument.setReadOnly(argNode.path("readOnly").asBoolean(false));
                        
                        if (argNode.has("min") && !argNode.path("min").isNull()) {
                            argument.setMin(argNode.path("min").asDouble());
                        }
                        if (argNode.has("max") && !argNode.path("max").isNull()) {
                            argument.setMax(argNode.path("max").asDouble());
                        }
                        
                        // 解析 enumValues
                        JsonNode enumNode = argNode.path("enumValues");
                        if (enumNode.isArray()) {
                            List<String> enumValues = new ArrayList<>();
                            enumNode.forEach(item -> enumValues.add(item.asText()));
                            argument.setEnumValues(enumValues);
                        }
                        
                        arguments.put(argName, argument);
                    });
                    action.setArguments(arguments);
                }
                
                actions.put(actionName, action);
            });
            model.setActions(actions);
        }
        
        // 解析 events
        JsonNode eventsNode = root.path("events");
        if (eventsNode.isObject()) {
            Map<String, DeviceEvent> events = new HashMap<>();
            eventsNode.fields().forEachRemaining(entry -> {
                String eventName = entry.getKey();
                JsonNode eventNode = entry.getValue();
                
                DeviceEvent event = new DeviceEvent();
                event.setName(eventName);
                event.setDescription(eventNode.path("description").asText());
                event.setLevel(eventNode.path("level").asText("info"));
                
                // 解析 fields
                JsonNode fieldsNode = eventNode.path("fields");
                if (fieldsNode.isObject()) {
                    Map<String, DeviceEventField> fields = new HashMap<>();
                    fieldsNode.fields().forEachRemaining(fieldEntry -> {
                        String fieldName = fieldEntry.getKey();
                        JsonNode fieldNode = fieldEntry.getValue();
                        
                        DeviceEventField field = new DeviceEventField();
                        field.setName(fieldName);
                        field.setType(fieldNode.path("type").asText());
                        field.setDescription(fieldNode.path("description").asText());
                        field.setUnit(fieldNode.path("unit").asText(""));
                        
                        fields.put(fieldName, field);
                    });
                    event.setFields(fields);
                }
                
                events.put(eventName, event);
            });
            model.setEvents(events);
        }
        
        deviceModels.put(category, model);
        log.info("成功加载设备模型: {} (category: {}), {} 个操作, {} 个事件", 
                 modelName, category, model.getActions().size(), model.getEvents().size());
    }
    
    /**
     * 获取所有设备类型
     */
    public Set<String> getAllDeviceCategories() {
        return deviceModels.keySet();
    }
    
    /**
     * 获取设备模型
     */
    public DeviceModel getDeviceModel(String category) {
        return deviceModels.get(category);
    }
    
    /**
     * 检查设备类型是否存在
     */
    public boolean isDeviceTypeSupported(String category) {
        return deviceModels.containsKey(category);
    }
    
    /**
     * 获取设备类型的简要描述（用于 Prompt）
     */
    public String getDeviceTypesDescription() {
        if (deviceModels.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("可用的设备节点类型：\n");
        
        for (Map.Entry<String, DeviceModel> entry : deviceModels.entrySet()) {
            DeviceModel model = entry.getValue();
            sb.append(String.format("  - %s (%s): ", model.getCategory(), model.getModelName()));
            
            List<String> actionNames = new ArrayList<>(model.getActions().keySet());
            if (actionNames.size() <= 5) {
                sb.append(String.join(", ", actionNames));
            } else {
                sb.append(String.join(", ", actionNames.subList(0, 5)))
                  .append(" 等 ").append(actionNames.size()).append(" 个操作");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 动态构造设备节点定义（用于 Prompt）
     */
    public String buildDeviceNodeDefinition(String category) {
        DeviceModel model = deviceModels.get(category);
        if (model == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("## 设备节点: ").append(model.getModelName()).append(" (kind: ").append(category).append(")\n");
        sb.append("节点结构：\n");
        sb.append("{\n");
        sb.append("  \"id\": \"节点ID\",\n");
        sb.append("  \"name\": \"").append(model.getModelName()).append("\",\n");
        sb.append("  \"kind\": \"").append(category).append("\",\n");
        sb.append("  \"inputPorts\": [\"input\"],\n");
        sb.append("  \"outputPorts\": [\"output\"],\n");
        sb.append("  \"deviceId\": \"设备实例ID（如果是自动分配请填 auto，如果用户指定了设备（如'咖啡机1'），请尝试提取设备标识，如 'c1'）\",\n");
        sb.append("  \"deviceAction\": \"操作名称（从下面列表中选择）\",\n");
        sb.append("  \"inputParams\": [根据选择的操作动态生成，参数具体填写规则填写参照其他操作的参数说明],\n");
        sb.append("  \"outputParams\": []\n");
        sb.append("}\n\n");
        
        sb.append("可用操作列表：\n");
        for (Map.Entry<String, DeviceAction> entry : model.getActions().entrySet()) {
            DeviceAction action = entry.getValue();
            sb.append(String.format("  - %s: %s\n", action.getName(), action.getDescription()));
            
            if (!action.getArguments().isEmpty()) {
                sb.append("    参数：\n");
                for (Map.Entry<String, DeviceArgument> argEntry : action.getArguments().entrySet()) {
                    DeviceArgument arg = argEntry.getValue();
                    sb.append(String.format("      * %s (%s): %s", 
                                          arg.getName(), 
                                          mapTypeToTypeId(arg.getType()), 
                                          arg.getDescription()));
                    
                    if (!arg.getEnumValues().isEmpty()) {
                        sb.append(" [可选值: ").append(String.join(", ", arg.getEnumValues())).append("]");
                    }
                    if (arg.getMin() != null || arg.getMax() != null) {
                        sb.append(" [范围: ");
                        if (arg.getMin() != null) sb.append("min=").append(arg.getMin());
                        if (arg.getMin() != null && arg.getMax() != null) sb.append(", ");
                        if (arg.getMax() != null) sb.append("max=").append(arg.getMax());
                        sb.append("]");
                    }
                    sb.append("\n");
                }
            } else {
                sb.append("    无参数\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 映射设备类型到 DSL 类型ID
     */
    private String mapTypeToTypeId(String deviceType) {
        switch (deviceType.toLowerCase()) {
            case "string":
            case "enum":
                return "string";
            case "number":
            case "integer":
            case "float":
            case "double":
                return "number";
            case "boolean":
            case "bool":
                return "boolean";
            default:
                return "string";
        }
    }
    
    /**
     * 设备模型数据结构
     */
    @Data
    public static class DeviceModel {
        private String modelName;
        private String category;
        private String fileName;
        private Map<String, DeviceAction> actions = new HashMap<>();
        private Map<String, DeviceEvent> events = new HashMap<>();
    }
    
    @Data
    public static class DeviceAction {
        private String name;
        private String description;
        private Map<String, DeviceArgument> arguments = new HashMap<>();
    }
    
    @Data
    public static class DeviceArgument {
        private String name;
        private String type;
        private String description;
        private String unit;
        private boolean readOnly;
        private Double min;
        private Double max;
        private List<String> enumValues = new ArrayList<>();
    }
    
    @Data
    public static class DeviceEvent {
        private String name;
        private String description;
        private String level;
        private Map<String, DeviceEventField> fields = new HashMap<>();
    }
    
    @Data
    public static class DeviceEventField {
        private String name;
        private String type;
        private String description;
        private String unit;
    }
}
