package com.promptforge.core.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Incoming API payload.
 *
 * @param prompt       the raw prompt to reshape (required)
 * @param profile      domain preset, defaults to GENERALIST
 * @param engine       RULE or LLM, defaults to RULE
 * @param strict       when true the template keeps explicit placeholders for missing sections
 * @param includeExamples when true an Examples section is added to the reshaped prompt
 * @param provider     optional LLM provider override: anthropic | openai | gemini
 * @param model        optional LLM model id override
 */
public record PromptRequest(
        @NotBlank(message = "prompt must not be blank") String prompt,
        String profile,
        String engine,
        Boolean strict,
        Boolean includeExamples,
        String provider,
        String model) {

    public PromptProfile profileOrDefault() {
        return PromptProfile.from(profile);
    }

    public Engine engineOrDefault() {
        return Engine.from(engine);
    }

    public boolean strictOrDefault() {
        return Boolean.TRUE.equals(strict);
    }

    public boolean includeExamplesOrDefault() {
        return includeExamples == null || includeExamples;
    }
}
