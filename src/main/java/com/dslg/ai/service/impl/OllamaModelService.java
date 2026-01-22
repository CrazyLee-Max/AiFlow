package com.dslg.ai.service.impl;

import com.dslg.ai.model.AIModelProvider;
import com.dslg.ai.service.AIModelService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Ollama模型服务实现
 * 
 * 使用AI4J框架调用本地部署的Ollama模型
 */
@Slf4j
@Service("ollamaModelService")
public class OllamaModelService implements AIModelService {

    private final ChatLanguageModel chatModel;

    public OllamaModelService(
            @Value("${ai.models.ollama.base-url}") 
            String baseUrl,
            @Value("${ai.models.ollama.model-name}") 
            String modelName,
            @Value("${ai.models.ollama.timeout:120s}") 
            Duration timeout,
            @Value("${ai.models.ollama.temperature:0.7}") 
            Double temperature) {
        
        this.chatModel = OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(timeout)
                .temperature(temperature)
                .build();
        
        log.info("Ollama model service initialized with model: {} at {}", modelName, baseUrl);
    }

    @Override
    public AIModelProvider getProvider() {
        return AIModelProvider.OLLAMA;
    }

    @Override
    public String generateText(String prompt) {
        try {
            return chatModel.generate(prompt);
        } catch (Exception e) {
            log.error("Error generating text with Ollama: {}", e.getMessage(), e);
            throw new RuntimeException("Ollama API调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String generateText(String systemPrompt, String userPrompt) {
        try {
            String combinedPrompt = String.format("System: %s\n\nUser: %s", systemPrompt, userPrompt);
            return chatModel.generate(combinedPrompt);
        } catch (Exception e) {
            log.error("Error generating text with system prompt using Ollama: {}", e.getMessage(), e);
            throw new RuntimeException("Ollama API调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isWorkflowGenerationIntent(String userInput) {
        String systemPrompt = """
                你是一个工作流意图识别专家。请判断用户的输入是否表达了想要生成工作流的意图。
                
                工作流生成意图的特征包括：
                1. 描述一系列需要执行的任务或步骤
                2. 涉及自动化流程或业务流程
                3. 包含条件判断、循环、数据处理等逻辑
                4. 提到工作流、流程、自动化等关键词
                
                请只回答 "true" 或 "false"，不要包含其他内容。
                """;
        
        try {
            String response = generateText(systemPrompt, userInput).trim().toLowerCase();
            return "true".equals(response);
        } catch (Exception e) {
            log.error("Error detecting workflow intent: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public String decomposeTask(String taskDescription) {
        String systemPrompt = """
                你是一个任务分解专家。请将用户描述的复杂任务分解为具体的、可执行的步骤。
                
                要求：
                1. 每个步骤都应该是具体的、可操作的
                2. 步骤之间应该有清晰的逻辑关系
                3. 识别出条件判断、循环、并行等控制结构
                4. 返回JSON格式，包含steps数组，每个步骤包含id、name、description、type、dependencies等字段
                
                示例格式：
                {
                  "steps": [
                    {
                      "id": "step1",
                      "name": "数据获取",
                      "description": "从数据源获取所需数据",
                      "type": "data_input",
                      "dependencies": []
                    }
                  ]
                }
                """;
        
        return generateText(systemPrompt, taskDescription);
    }

    @Override
    public String extractKnowledge(String text, String knowledgeType) {
        String systemPrompt = String.format("""
                你是一个知识抽取专家。请从给定的文本中抽取%s类型的知识。
                
                知识类型说明：
                - variable-binding: 变量绑定关系
                - control-logic: 控制逻辑模式
                - data-flow: 数据流转规则
                - domain-knowledge: 业务领域知识
                
                请返回JSON格式的结构化知识，包含：
                - type: 知识类型
                - title: 知识标题
                - content: 知识内容
                - tags: 相关标签
                - confidence: 置信度(0-1)
                """, knowledgeType);
        
        return generateText(systemPrompt, text);
    }

    @Override
    public boolean isAvailable() {
        try {
            String testResponse = generateText("Hello");
            return testResponse != null && !testResponse.trim().isEmpty();
        } catch (Exception e) {
            log.warn("Ollama model is not available: {}", e.getMessage());
            return false;
        }
    }
}