package com.dslg;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Workflow DSL Generator Application
 * 
 * 基于AI的工作流DSL生成系统主启动类
 * 支持自然语言输入，智能识别工作流生成意图，
 * 通过任务分解、知识检索和MCP Server调用完成DSL生成
 */
@SpringBootApplication
public class WorkflowDslGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowDslGeneratorApplication.class, args);
    }
}