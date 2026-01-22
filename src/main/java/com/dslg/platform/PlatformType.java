package com.dslg.platform;

/**
 * 支持的低代码平台类型枚举
 * 
 * 定义系统支持的各种低代码平台，每个平台有不同的DSL格式和MCP Server配置
 */
public enum PlatformType {
    /**
     * InBuilder 低代码平台
     */
    IN_BUILDER("in_builder", "InBuilder", "http://localhost:3001/mcp");

    private final String code;
    private final String displayName;
    private final String defaultMcpServerUrl;

    PlatformType(String code, String displayName, String defaultMcpServerUrl) {
        this.code = code;
        this.displayName = displayName;
        this.defaultMcpServerUrl = defaultMcpServerUrl;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultMcpServerUrl() {
        return defaultMcpServerUrl;
    }

    /**
     * 根据代码获取平台类型
     * 
     * @param code 平台代码
     * @return 平台类型，如果未找到则返回GENERIC
     */
    public static PlatformType fromCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            return IN_BUILDER;
        }
        
        for (PlatformType platform : values()) {
            if (platform.code.equalsIgnoreCase(code.trim())) {
                return platform;
            }
        }
        
        return IN_BUILDER;
    }

    /**
     * 获取所有支持的平台代码
     * 
     * @return 平台代码数组
     */
    public static String[] getAllCodes() {
        PlatformType[] platforms = values();
        String[] codes = new String[platforms.length];
        for (int i = 0; i < platforms.length; i++) {
            codes[i] = platforms[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return String.format("%s(%s)", displayName, code);
    }
}