package com.dslg.ai.service;

import com.dslg.ai.model.AIModelProvider;

/**
 * AI模型服务接口
 * 
 * 定义统一的AI模型调用接口，支持：
 * - 文本生成
 * - 任务分解
 * - 意图识别
 * - 知识抽取
 */
public interface AIModelService {

    /**
     * 获取支持的AI模型提供者
     */
    AIModelProvider getProvider();

    /**
     * 生成文本响应
     * 
     * @param prompt 输入提示词
     * @return AI生成的响应文本
     */
    String generateText(String prompt);

    /**
     * 生成文本响应（带系统提示）
     * 
     * @param systemPrompt 系统提示词
     * @param userPrompt 用户提示词
     * @return AI生成的响应文本
     */
    String generateText(String systemPrompt, String userPrompt);

    /**
     * 判断用户输入是否为工作流生成意图
     * 
     * @param userInput 用户自然语言输入
     * @return true表示是工作流生成意图，false表示不是
     */
    boolean isWorkflowGenerationIntent(String userInput);

    /**
     * 将自然语言任务分解为具体步骤
     * 
     * @param taskDescription 任务描述
     * @return 分解后的任务步骤列表（JSON格式）
     */
    String decomposeTask(String taskDescription);

    /**
     * 从文本中抽取结构化知识
     * 
     * @param text 输入文本
     * @param knowledgeType 知识类型
     * @return 抽取的结构化知识（JSON格式）
     */
    String extractKnowledge(String text, String knowledgeType);

    /**
     * 检查模型是否可用
     */
    boolean isAvailable();
}