package com.promptforge.core.model;

/**
 * Domain presets. Each profile biases role inference, default output format and
 * default acceptance criteria.
 */
public enum PromptProfile {

    GENERALIST("Generalist", TaskType.GENERAL),
    CODING("Coding", TaskType.CODE),
    DATA_ANALYSIS("Data analysis", TaskType.DATA),
    WRITING("Writing", TaskType.WRITING),
    RESEARCH("Research", TaskType.RESEARCH),
    PRODUCT("Product", TaskType.PRODUCT),
    BUSINESS("Business", TaskType.BUSINESS);

    private final String label;
    private final TaskType taskType;

    PromptProfile(String label, TaskType taskType) {
        this.label = label;
        this.taskType = taskType;
    }

    public String label() {
        return label;
    }

    public TaskType taskType() {
        return taskType;
    }

    public static PromptProfile from(String value) {
        if (value == null || value.isBlank()) {
            return GENERALIST;
        }
        for (PromptProfile profile : values()) {
            if (profile.name().equalsIgnoreCase(value.trim())) {
                return profile;
            }
        }
        return GENERALIST;
    }
}
