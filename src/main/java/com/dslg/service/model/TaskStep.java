package com.dslg.service.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务步骤
 */
public class TaskStep {
    private String id;
    private String name;
    private String description;
    private String type;
    private List<String> dependencies;
    private List<String> inputs;
    private List<String> outputs;
    private String conditions;
    private boolean isParallel;
    private String estimatedTime;
    private String priority;

    public static TaskStepBuilder builder() {
        return new TaskStepBuilder();
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public List<String> getDependencies() { return dependencies; }
    public List<String> getInputs() { return inputs; }
    public List<String> getOutputs() { return outputs; }
    public String getConditions() { return conditions; }
    public boolean isParallel() { return isParallel; }
    public String getEstimatedTime() { return estimatedTime; }
    public String getPriority() { return priority; }
    
    // Setters
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setType(String type) { this.type = type; }
    public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }
    public void setInputs(List<String> inputs) { this.inputs = inputs; }
    public void setOutputs(List<String> outputs) { this.outputs = outputs; }
    public void setConditions(String conditions) { this.conditions = conditions; }
    public void setParallel(boolean parallel) { this.isParallel = parallel; }
    public void setEstimatedTime(String estimatedTime) { this.estimatedTime = estimatedTime; }
    public void setPriority(String priority) { this.priority = priority; }

    public static class TaskStepBuilder {
        private String id;
        private String name;
        private String description;
        private String type;
        private List<String> dependencies;
        private List<String> inputs;
        private List<String> outputs;
        private String conditions;
        private boolean isParallel;
        private String estimatedTime;
        private String priority;

        public TaskStepBuilder id(String id) { this.id = id; return this; }
        public TaskStepBuilder name(String name) { this.name = name; return this; }
        public TaskStepBuilder description(String description) { this.description = description; return this; }
        public TaskStepBuilder type(String type) { this.type = type; return this; }
        public TaskStepBuilder dependencies(List<String> dependencies) { this.dependencies = dependencies; return this; }
        public TaskStepBuilder inputs(List<String> inputs) { this.inputs = inputs; return this; }
        public TaskStepBuilder outputs(List<String> outputs) { this.outputs = outputs; return this; }
        public TaskStepBuilder conditions(String conditions) { this.conditions = conditions; return this; }
        public TaskStepBuilder isParallel(boolean isParallel) { this.isParallel = isParallel; return this; }
        public TaskStepBuilder estimatedTime(String estimatedTime) { this.estimatedTime = estimatedTime; return this; }
        public TaskStepBuilder priority(String priority) { this.priority = priority; return this; }

        public TaskStep build() {
            TaskStep step = new TaskStep();
            step.id = this.id;
            step.name = this.name;
            step.description = this.description;
            step.type = this.type;
            step.dependencies = this.dependencies != null ? this.dependencies : new ArrayList<>();
            step.inputs = this.inputs != null ? this.inputs : new ArrayList<>();
            step.outputs = this.outputs != null ? this.outputs : new ArrayList<>();
            step.conditions = this.conditions;
            step.isParallel = this.isParallel;
            step.estimatedTime = this.estimatedTime;
            step.priority = this.priority;
            return step;
        }
    }
}