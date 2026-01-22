package com.dslg.ai.factory;

import com.dslg.ai.model.AIModelProvider;
import com.dslg.ai.service.AIModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI模型工厂类
 * 
 * 使用工厂模式管理不同的AI模型服务
 * 支持动态切换和负载均衡
 */
@Slf4j
@Component
public class AIModelFactory {

    private final Map<AIModelProvider, AIModelService> modelServices;
    private final String defaultProvider;

    public AIModelFactory(
            @Qualifier("deepSeekModelService") AIModelService deepSeekService,
            @Qualifier("chatGPTModelService") AIModelService chatGPTService,
            @Qualifier("ollamaModelService") AIModelService ollamaService,
            @Value("${ai.default-provider:deepseek}") String defaultProvider) {
        
        this.defaultProvider = defaultProvider;
        this.modelServices = new ConcurrentHashMap<>();
        
        // 注册所有模型服务
        registerModelService(deepSeekService);
        registerModelService(chatGPTService);
        registerModelService(ollamaService);
        
        log.info("AI Model Factory initialized with {} providers, default: {}", 
                modelServices.size(), defaultProvider);
    }

    /**
     * 注册模型服务
     */
    private void registerModelService(AIModelService service) {
        modelServices.put(service.getProvider(), service);
        log.debug("Registered AI model service: {}", service.getProvider());
    }

    /**
     * 获取默认的AI模型服务
     */
    public AIModelService getDefaultModelService() {
        AIModelProvider provider = AIModelProvider.fromCode(defaultProvider);
        return getModelService(provider);
    }

    /**
     * 根据提供者获取AI模型服务
     */
    public AIModelService getModelService(AIModelProvider provider) {
        AIModelService service = modelServices.get(provider);
        if (service == null) {
            throw new IllegalArgumentException("Unsupported AI model provider: " + provider);
        }
        return service;
    }

    /**
     * 根据代码获取AI模型服务
     */
    public AIModelService getModelService(String providerCode) {
        AIModelProvider provider = AIModelProvider.fromCode(providerCode);
        return getModelService(provider);
    }

    /**
     * 获取可用的AI模型服务
     * 如果指定的服务不可用，会尝试其他可用的服务
     */
    public AIModelService getAvailableModelService(AIModelProvider preferredProvider) {
        // 首先尝试首选的提供者
        AIModelService preferredService = modelServices.get(preferredProvider);
        if (preferredService != null && preferredService.isAvailable()) {
            return preferredService;
        }

        // 如果首选不可用，尝试其他可用的服务
        for (AIModelService service : modelServices.values()) {
            if (service.isAvailable()) {
                log.warn("Preferred provider {} not available, using {} instead", 
                        preferredProvider, service.getProvider());
                return service;
            }
        }

        throw new RuntimeException("No available AI model service found");
    }

    /**
     * 获取可用的AI模型服务（使用默认首选）
     */
    public AIModelService getAvailableModelService() {
        AIModelProvider defaultProviderEnum = AIModelProvider.fromCode(defaultProvider);
        return getAvailableModelService(defaultProviderEnum);
    }

    /**
     * 检查指定提供者是否可用
     */
    public boolean isProviderAvailable(AIModelProvider provider) {
        AIModelService service = modelServices.get(provider);
        return service != null && service.isAvailable();
    }

    /**
     * 获取所有注册的提供者
     */
    public Map<AIModelProvider, AIModelService> getAllProviders() {
        return Map.copyOf(modelServices);
    }

    /**
     * 获取可用提供者的数量
     */
    public long getAvailableProviderCount() {
        return modelServices.values().stream()
                .mapToLong(service -> service.isAvailable() ? 1 : 0)
                .sum();
    }
}