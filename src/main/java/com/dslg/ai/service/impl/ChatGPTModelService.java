package com.dslg.ai.service.impl;

import com.dslg.ai.model.AIModelProvider;
import com.dslg.ai.service.AIModelService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * ChatGPT模型服务实现
 * 
 * 使用AI4J框架调用OpenAI ChatGPT API
 */
@Slf4j
@Service("chatGPTModelService")
public class ChatGPTModelService implements AIModelService {

    private final ChatLanguageModel chatModel;

    public ChatGPTModelService(
            @Value("${ai.models.chatgpt.api-key}") 
            String apiKey,
            @Value("${ai.models.chatgpt.base-url}") 
            String baseUrl,
            @Value("${ai.models.chatgpt.model-name}") 
            String modelName,
            @Value("${ai.models.chatgpt.timeout:60s}") 
            Duration timeout,
            @Value("${ai.models.chatgpt.max-tokens:4000}") 
            Integer maxTokens,
            @Value("${ai.models.chatgpt.temperature:0.7}") 
            Double temperature) {
        
        // 如果API key为空，使用默认值避免启动失败
        String effectiveApiKey = (apiKey == null || apiKey.trim().isEmpty()) ? "dummy-key" : apiKey;
        
        this.chatModel = OpenAiChatModel.builder()
                .apiKey(effectiveApiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .timeout(timeout)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .build();
        
        log.info("ChatGPT model service initialized with model: {}", modelName);
        if ("dummy-key".equals(effectiveApiKey)) {
            log.warn("ChatGPT API key not configured, service will not be fully functional");
        }
    }

    @Override
    public AIModelProvider getProvider() {
        return AIModelProvider.CHATGPT;
    }

    @Override
    public String generateText(String prompt) {
        try {
            return chatModel.generate(prompt);
        } catch (Exception e) {
            log.error("Error generating text with ChatGPT: {}", e.getMessage(), e);
            throw new RuntimeException("ChatGPT API调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String generateText(String systemPrompt, String userPrompt) {
        try {
            String combinedPrompt = String.format("System: %s\n\nUser: %s", systemPrompt, userPrompt);
            return chatModel.generate(combinedPrompt);
        } catch (Exception e) {
            log.error("Error generating text with system prompt using ChatGPT: {}", e.getMessage(), e);
            throw new RuntimeException("ChatGPT API调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isWorkflowGenerationIntent(String userInput) {
        String systemPrompt = """
                You are a workflow intent recognition expert. Please determine if the user's input expresses an intent to generate a workflow.
                
                Characteristics of workflow generation intent include:
                1. Describing a series of tasks or steps to be executed
                2. Involving automation processes or business processes
                3. Including conditional logic, loops, data processing, etc.
                4. Mentioning keywords like workflow, process, automation, etc.
                
                Please only answer "true" or "false", without any other content.
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
                You are a task decomposition expert. Please break down the complex task described by the user into specific, executable steps.
                
                Requirements:
                1. Each step should be specific and actionable
                2. There should be clear logical relationships between steps
                3. Identify control structures like conditional judgments, loops, parallelism, etc.
                4. Return JSON format with steps array, each step containing id, name, description, type, dependencies fields
                
                Example format:
                {
                  "steps": [
                    {
                      "id": "step1",
                      "name": "Data Acquisition",
                      "description": "Retrieve required data from data source",
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
                You are a knowledge extraction expert. Please extract %s type knowledge from the given text.
                
                Knowledge type descriptions:
                - variable-binding: Variable binding relationships
                - control-logic: Control logic patterns
                - data-flow: Data flow rules
                - domain-knowledge: Business domain knowledge
                
                Please return structured knowledge in JSON format, including:
                - type: Knowledge type
                - title: Knowledge title
                - content: Knowledge content
                - tags: Related tags
                - confidence: Confidence score (0-1)
                """, knowledgeType);
        
        return generateText(systemPrompt, text);
    }

    @Override
    public boolean isAvailable() {
        try {
            String testResponse = generateText("Hello");
            return testResponse != null && !testResponse.trim().isEmpty();
        } catch (Exception e) {
            log.warn("ChatGPT model is not available: {}", e.getMessage());
            return false;
        }
    }
}