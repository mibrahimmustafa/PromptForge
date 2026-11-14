package com.promptforge.core.model;

/**
 * Shape of the work being requested. Drives role inference, default output format and
 * default acceptance criteria.
 */
public enum TaskType {

    CODE("Code"),
    DATA("Data analysis"),
    WRITING("Writing"),
    RESEARCH("Research"),
    PRODUCT("Product"),
    BUSINESS("Business"),
    GENERAL("General");

    private final String label;

    TaskType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
