package com.dslg.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 节点定义服务
 * 负责加载和管理 node-definitions 目录下的节点定义文件
 */
@Slf4j
@Service
public class NodeDefinitionService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    private final DeviceModelService deviceModelService;
    
    public NodeDefinitionService(DeviceModelService deviceModelService) {
        this.deviceModelService = deviceModelService;
    }
    
    /**
     * 存储所有节点定义，key为节点类型(kind)，value为节点定义JSON
     */
    private final Map<String, JsonNode> nodeDefinitions = new HashMap<>();
    
    /**
     * 节点定义文件目录
     */
    private static final String NODE_DEFINITIONS_PATH = "node-definitions/";
    
    /**
     * 支持的节点类型列表
     */
    private static final String[] SUPPORTED_NODE_TYPES = {
        "start", "end", "variableDef", "batchAssignValue", "selector"
    };

    /**
     * 初始化时加载所有节点定义
     */
    @PostConstruct
    public void init() {
        log.info("开始加载节点定义文件...");
        
        for (String nodeType : SUPPORTED_NODE_TYPES) {
            try {
                loadNodeDefinition(nodeType);
            } catch (Exception e) {
                log.warn("加载节点定义失败: {} - {}", nodeType, e.getMessage());
            }
        }
        
        log.info("节点定义加载完成，共加载 {} 个节点类型", nodeDefinitions.size());
    }

    /**
     * 加载单个节点定义文件
     */
    private void loadNodeDefinition(String nodeType) throws IOException {
        String fileName = nodeType + ".json";
        String filePath = NODE_DEFINITIONS_PATH + fileName;
        
        try {
            ClassPathResource resource = new ClassPathResource(filePath);
            if (!resource.exists()) {
                log.debug("节点定义文件不存在: {}", filePath);
                return;
            }
            
            try (InputStream inputStream = resource.getInputStream()) {
                JsonNode definition = objectMapper.readTree(inputStream);
                nodeDefinitions.put(nodeType, definition);
                log.info("成功加载节点定义: {}", nodeType);
            }
        } catch (IOException e) {
            log.error("读取节点定义文件失败: {}", filePath, e);
            throw e;
        }
    }

    /**
     * 获取指定类型的节点定义
     * 
     * @param nodeType 节点类型
     * @return 节点定义JSON，如果不存在返回null
     */
    public JsonNode getNodeDefinition(String nodeType) {
        return nodeDefinitions.get(nodeType);
    }

    /**
     * 检查节点类型是否被支持
     * 
     * @param nodeType 节点类型
     * @return 是否支持
     */
    public boolean isNodeTypeSupported(String nodeType) {
        return nodeDefinitions.containsKey(nodeType) || deviceModelService.isDeviceTypeSupported(nodeType);
    }

    /**
     * 获取所有已加载的节点类型
     * 
     * @return 节点类型集合
     */
    public Map<String, JsonNode> getAllNodeDefinitions() {
        return new HashMap<>(nodeDefinitions);
    }

    /**
     * 获取节点定义的模板部分
     * 
     * @param nodeType 节点类型
     * @return 模板JSON，如果不存在返回null
     */
    public JsonNode getNodeTemplate(String nodeType) {
        JsonNode definition = getNodeDefinition(nodeType);
        if (definition != null && definition.has("template")) {
            return definition.get("template");
        }
        return null;
    }

    /**
     * 获取节点定义的描述
     * 
     * @param nodeType 节点类型
     * @return 描述文本，如果不存在返回空字符串
     */
    public String getNodeDescription(String nodeType) {
        JsonNode definition = getNodeDefinition(nodeType);
        if (definition != null && definition.has("description")) {
            return definition.get("description").asText();
        }
        return "";
    }

    /**
     * 格式化节点定义为可读的字符串（用于生成prompt）
     * 
     * @param nodeType 节点类型
     * @return 格式化的节点定义字符串
     */
    public String formatNodeDefinitionForPrompt(String nodeType) {
        // 如果是设备类型，使用 DeviceModelService 生成定义
        if (deviceModelService.isDeviceTypeSupported(nodeType)) {
            return deviceModelService.buildDeviceNodeDefinition(nodeType);
        }
        
        JsonNode definition = getNodeDefinition(nodeType);
        if (definition == null) {
            return "";
        }
        
        try {
            StringBuilder sb = new StringBuilder();
            
            // 添加节点名称和描述
            if (definition.has("name")) {
                sb.append("   - ").append(definition.get("name").asText());
            }
            if (definition.has("description")) {
                sb.append(": ").append(definition.get("description").asText());
            }
            sb.append("\n");
            
            // 添加模板信息（格式化为易读的JSON）
            if (definition.has("template")) {
                JsonNode template = definition.get("template");
                String templateJson = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(template);
                
                // 缩进每一行
                String[] lines = templateJson.split("\n");
                for (String line : lines) {
                    sb.append("     ").append(line).append("\n");
                }
            }
            
            // 添加注意事项（如果有）
            if (definition.has("notes")) {
                sb.append("     注意事项: ");
                sb.append(objectMapper.writeValueAsString(definition.get("notes")));
                sb.append("\n");
            }
            
            return sb.toString();
            
        } catch (Exception e) {
            log.error("格式化节点定义失败: {}", nodeType, e);
            return "";
        }
    }
}
