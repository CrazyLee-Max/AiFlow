package com.dslg.service.model;

/**
 * 控制流信息
 */
public class ControlFlow {
    private boolean hasConditions;
    private boolean hasLoops;
    private boolean hasParallel;

    public static ControlFlowBuilder builder() {
        return new ControlFlowBuilder();
    }

    // Getters
    public boolean isHasConditions() { return hasConditions; }
    public boolean isHasLoops() { return hasLoops; }
    public boolean isHasParallel() { return hasParallel; }

    public static class ControlFlowBuilder {
        private boolean hasConditions;
        private boolean hasLoops;
        private boolean hasParallel;

        public ControlFlowBuilder hasConditions(boolean hasConditions) {
            this.hasConditions = hasConditions;
            return this;
        }

        public ControlFlowBuilder hasLoops(boolean hasLoops) {
            this.hasLoops = hasLoops;
            return this;
        }

        public ControlFlowBuilder hasParallel(boolean hasParallel) {
            this.hasParallel = hasParallel;
            return this;
        }

        public ControlFlow build() {
            ControlFlow controlFlow = new ControlFlow();
            controlFlow.hasConditions = this.hasConditions;
            controlFlow.hasLoops = this.hasLoops;
            controlFlow.hasParallel = this.hasParallel;
            return controlFlow;
        }
    }
}