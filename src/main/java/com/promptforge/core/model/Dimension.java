package com.promptforge.core.model;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The building blocks of a well-formed prompt that PromptForge scores and fills in.
 */
public enum Dimension {

    ROLE("Role", 0.10,
            "Who the AI should be. Framing expertise and perspective measurably improves answer quality."),
    TASK("Task", 0.20,
            "The concrete action and deliverable. Must be an explicit, verifiable instruction."),
    CONTEXT("Context", 0.16,
            "Background, audience, domain, stack and goal. Supplies the information the AI cannot guess."),
    CONSTRAINTS("Constraints", 0.16,
            "Hard limits and exclusions: length, libraries, tone, forbidden approaches."),
    OUTPUT_FORMAT("Output format", 0.16,
            "The required shape of the response. Without it, output is unusable downstream."),
    EXAMPLES("Examples", 0.08,
            "One representative sample of a good answer. Strongest single lever for format control."),
    ACCEPTANCE("Acceptance criteria", 0.14,
            "Definition of done. Lets the AI self-check and lets you verify the answer.");

    private final String label;
    private final double weight;
    private final String description;

    Dimension(String label, double weight, String description) {
        this.label = label;
        this.weight = weight;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public double weight() {
        return weight;
    }

    public String description() {
        return description;
    }

    /**
     * Serialises as an object so the UI gets the label and weight without a lookup table.
     *
     * @return the JSON representation of this dimension
     */
    @JsonValue
    public Map<String, Object> asJson() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", name());
        map.put("label", label);
        map.put("weight", weight);
        map.put("description", description);
        return map;
    }
}
