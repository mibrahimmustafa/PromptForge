package com.promptforge.core.model;

/**
 * Which engine performs the reshape.
 */
public enum Engine {

    /** Local deterministic analyzer + rewriter. Always available, no network, no cost. */
    RULE("Rule-based"),
    /** LLM-backed rewrite, grounded in the local analyzer findings. Requires an API key. */
    LLM("LLM");

    private final String label;

    Engine(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Engine from(String value) {
        if (value == null || value.isBlank()) {
            return RULE;
        }
        for (Engine engine : values()) {
            if (engine.name().equalsIgnoreCase(value.trim())) {
                return engine;
            }
        }
        return RULE;
    }
}
