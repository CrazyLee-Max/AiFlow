package com.dslg.service.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务分解结果
 */
public class TaskDecompositionResult {
    private boolean success;
    private String errorMessage;
    private List<TaskStep> steps;
    private ControlFlow controlFlow;
    private String summary;
    private String aiProvider;
    private String rawResponse;

    public static TaskDecompositionResultBuilder builder() {
        return new TaskDecompositionResultBuilder();
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public List<TaskStep> getSteps() { return steps; }
    public void setSteps(List<TaskStep> steps) { this.steps = steps; }
    public ControlFlow getControlFlow() { return controlFlow; }
    public void setControlFlow(ControlFlow controlFlow) { this.controlFlow = controlFlow; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }
    public String getRawResponse() { return rawResponse; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }

    public static class TaskDecompositionResultBuilder {
        private boolean success;
        private String errorMessage;
        private List<TaskStep> steps;
        private ControlFlow controlFlow;
        private String summary;

        public TaskDecompositionResultBuilder success(boolean success) {
            this.success = success;
            return this;
        }

        public TaskDecompositionResultBuilder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public TaskDecompositionResultBuilder steps(List<TaskStep> steps) {
            this.steps = steps;
            return this;
        }

        public TaskDecompositionResultBuilder controlFlow(ControlFlow controlFlow) {
            this.controlFlow = controlFlow;
            return this;
        }

        public TaskDecompositionResultBuilder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public TaskDecompositionResult build() {
            TaskDecompositionResult result = new TaskDecompositionResult();
            result.success = this.success;
            result.errorMessage = this.errorMessage;
            result.steps = this.steps != null ? this.steps : new ArrayList<>();
            result.controlFlow = this.controlFlow;
            result.summary = this.summary;
            return result;
        }
    }
}