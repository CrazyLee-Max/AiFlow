package com.dslg.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 文件存储服务
 * 
 * 负责将DSL内容保存到文件系统中
 */
@Slf4j
@Service
public class FileStorageService {

    private static final String RESULT_DIR = "result";
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * 保存DSL内容到文件
     * 
     * @param dslContent DSL内容
     * @param dslFormat DSL格式（如bpmn、xml等）
     * @param platform 平台类型
     * @param processName 流程名称（可选）
     * @return 保存的文件路径
     */
    public String saveDslToFile(String dslContent, String dslFormat, String platform, String processName) {
        if (!StringUtils.hasText(dslContent)) {
            log.warn("DSL内容为空，跳过文件保存");
            return null;
        }

        try {
            // 获取resources目录路径
            String resourcesPath = getResourcesPath();
            Path resultDir = Paths.get(resourcesPath, RESULT_DIR);
            
            // 确保result目录存在
            if (!Files.exists(resultDir)) {
                Files.createDirectories(resultDir);
                log.info("创建result目录: {}", resultDir);
            }

            // 生成文件名
            String fileName = generateFileName(platform, processName, dslFormat);
            Path filePath = resultDir.resolve(fileName);

            // 写入文件
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(dslContent);
            }

            log.info("DSL文件保存成功: {}", filePath);
            return filePath.toString();

        } catch (IOException e) {
            log.error("保存DSL文件失败", e);
            return null;
        }
    }

    /**
     * 生成文件名
     * 
     * @param platform 平台类型
     * @param processName 流程名称
     * @param dslFormat DSL格式
     * @return 生成的文件名
     */
    private String generateFileName(String platform, String processName, String dslFormat) {
        StringBuilder fileName = new StringBuilder();
        
        // 添加时间戳
        fileName.append(LocalDateTime.now().format(FILE_NAME_FORMATTER));
        
        // 添加平台信息
        if (StringUtils.hasText(platform)) {
            fileName.append("_").append(platform);
        }
        
        // 添加流程名称（如果有）
        if (StringUtils.hasText(processName)) {
            // 清理流程名称，移除特殊字符
            String cleanProcessName = processName.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]", "_");
            fileName.append("_").append(cleanProcessName);
        }
        
        // 添加文件扩展名
        String extension = StringUtils.hasText(dslFormat) ? dslFormat.toLowerCase() : "txt";
        fileName.append(".").append(extension);
        
        return fileName.toString();
    }

    /**
     * 获取resources目录的绝对路径
     * 
     * @return resources目录路径
     * @throws IOException 如果无法获取路径
     */
    private String getResourcesPath() throws IOException {
        try {
            // 尝试从classpath获取resources目录
            ClassPathResource resource = new ClassPathResource("");
            File resourcesDir = resource.getFile();
            return resourcesDir.getAbsolutePath();
        } catch (IOException e) {
            // 如果从classpath获取失败，使用项目相对路径
            String projectDir = System.getProperty("user.dir");
            return Paths.get(projectDir, "src", "main", "resources").toString();
        }
    }

    /**
     * 从DSL内容中提取流程名称
     * 
     * @param dslContent DSL内容
     * @param dslFormat DSL格式
     * @return 提取的流程名称，如果无法提取则返回null
     */
    public String extractProcessName(String dslContent, String dslFormat) {
        if (!StringUtils.hasText(dslContent) || !StringUtils.hasText(dslFormat)) {
            return null;
        }

        try {
            if ("bpmn".equalsIgnoreCase(dslFormat) || "xml".equalsIgnoreCase(dslFormat)) {
                // 从BPMN XML中提取流程名称
                return extractBpmnProcessName(dslContent);
            }
            // 可以添加其他格式的名称提取逻辑
            return null;
        } catch (Exception e) {
            log.warn("提取流程名称失败", e);
            return null;
        }
    }

    /**
     * 从BPMN XML中提取流程名称
     * 
     * @param bpmnContent BPMN XML内容
     * @return 流程名称
     */
    private String extractBpmnProcessName(String bpmnContent) {
        // 简单的正则表达式提取process name属性
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("name=\"([^\"]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(bpmnContent);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        
        return null;
    }
}