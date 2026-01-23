package com.dslg.platform;

import com.dslg.platform.impl.InBuilderPlatformStrategy;
import com.dslg.platform.impl.inbuilder.InBuilderPromptBuilder;
import com.dslg.platform.impl.inbuilder.InBuilderResponseParser;
import com.dslg.platform.impl.inbuilder.InBuilderVariableReferenceFixer;
import com.dslg.service.DeviceModelService;
import com.dslg.service.NodeDefinitionService;
import com.dslg.validator.VariableIdValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 平台策略工厂类
 * 
 * 根据平台类型返回对应的策略实现，使用单例模式缓存策略实例
 */
@Slf4j
@Component
public class PlatformStrategyFactory {

    private final Map<PlatformType, PlatformStrategy> strategyCache = new ConcurrentHashMap<>();
    
    // 通用服务
    private final NodeDefinitionService nodeDefinitionService;
    private final DeviceModelService deviceModelService;
    private final VariableIdValidator variableIdValidator;
    
    // InBuilder 特定服务
    private final InBuilderPromptBuilder inBuilderPromptBuilder;
    private final InBuilderResponseParser inBuilderResponseParser;
    private final InBuilderVariableReferenceFixer inBuilderVariableFixer;
    
    public PlatformStrategyFactory(
            NodeDefinitionService nodeDefinitionService,
            DeviceModelService deviceModelService,
            VariableIdValidator variableIdValidator,
            InBuilderPromptBuilder inBuilderPromptBuilder,
            InBuilderResponseParser inBuilderResponseParser,
            InBuilderVariableReferenceFixer inBuilderVariableFixer) {
        this.nodeDefinitionService = nodeDefinitionService;
        this.deviceModelService = deviceModelService;
        this.variableIdValidator = variableIdValidator;
        this.inBuilderPromptBuilder = inBuilderPromptBuilder;
        this.inBuilderResponseParser = inBuilderResponseParser;
        this.inBuilderVariableFixer = inBuilderVariableFixer;
    }

    /**
     * 根据平台类型获取对应的策略实现
     * 
     * @param platformType 平台类型
     * @return 平台策略实现
     */
    public PlatformStrategy getStrategy(PlatformType platformType) {
        if (platformType == null) {
            platformType = PlatformType.IN_BUILDER;
        }

        return strategyCache.computeIfAbsent(platformType, this::createStrategy);
    }

    /**
     * 根据平台代码获取对应的策略实现
     * 
     * @param platformCode 平台代码
     * @return 平台策略实现
     */
    public PlatformStrategy getStrategy(String platformCode) {
        PlatformType platformType = PlatformType.fromCode(platformCode);
        return getStrategy(platformType);
    }

    /**
     * 获取所有支持的平台类型
     * 
     * @return 平台类型到策略的映射
     */
    public Map<PlatformType, PlatformStrategy> getAllStrategies() {
        Map<PlatformType, PlatformStrategy> allStrategies = new HashMap<>();
        for (PlatformType platformType : PlatformType.values()) {
            allStrategies.put(platformType, getStrategy(platformType));
        }
        return allStrategies;
    }

    /**
     * 检查是否支持指定的平台
     * 
     * @param platformCode 平台代码
     * @return 是否支持
     */
    public boolean isSupported(String platformCode) {
        PlatformType platformType = PlatformType.fromCode(platformCode);
        return platformType != null;
    }

    /**
     * 获取平台的详细信息
     * 
     * @param platformCode 平台代码
     * @return 平台信息
     */
    public Map<String, Object> getPlatformInfo(String platformCode) {
        PlatformStrategy strategy = getStrategy(platformCode);
        Map<String, Object> info = new HashMap<>();
        info.put("platformType", strategy.getPlatformType());
        info.put("supportedFormats", strategy.getSupportedDslFormats());
        info.put("defaultFormat", strategy.getDefaultDslFormat());
        info.put("mcpServerUrl", strategy.getMcpServerUrl());
        info.put("metadata", strategy.getPlatformMetadata());
        return info;
    }

    /**
     * 创建策略实例
     * 
     * @param platformType 平台类型
     * @return 策略实例
     */
    private PlatformStrategy createStrategy(PlatformType platformType) {
        log.info("创建平台策略实例: {}", platformType);
        
        switch (platformType) {
            case IN_BUILDER:
                return new InBuilderPlatformStrategy(
                    nodeDefinitionService,
                    deviceModelService,
                    variableIdValidator,
                    inBuilderPromptBuilder,
                    inBuilderResponseParser,
                    inBuilderVariableFixer
                );
            default:
                log.warn("未知的平台类型: {}，使用InBuilder策略", platformType);
                return new InBuilderPlatformStrategy(
                    nodeDefinitionService,
                    deviceModelService,
                    variableIdValidator,
                    inBuilderPromptBuilder,
                    inBuilderResponseParser,
                    inBuilderVariableFixer
                );
        }
    }

    /**
     * 清除策略缓存（主要用于测试）
     */
    public void clearCache() {
        strategyCache.clear();
        log.info("平台策略缓存已清除");
    }

    /**
     * 获取缓存大小
     * 
     * @return 缓存中的策略数量
     */
    public int getCacheSize() {
        return strategyCache.size();
    }
}