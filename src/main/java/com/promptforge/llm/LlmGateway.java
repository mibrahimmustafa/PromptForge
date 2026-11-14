package com.promptforge.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Resolves which LLM client to use based on the configuration and any per-request override.
 *
 * <p>The rule-based engine never depends on this class - the LLM path is strictly optional.
 */
@Component
public class LlmGateway {

    private final LlmProperties properties;

    public LlmGateway(LlmProperties properties) {
        this.properties = properties;
    }

    /**
     * Resolves a client.
     *
     * @param providerOverride optional provider requested by the caller
     * @param modelOverride    optional model id requested by the caller
     * @return the client, or empty when no provider is configured
     */
    public Optional<LlmClient> resolve(String providerOverride, String modelOverride) {
        String requested = providerOverride != null && !providerOverride.isBlank()
                ? providerOverride.trim().toLowerCase(java.util.Locale.ROOT)
                : properties.getProvider().toLowerCase(java.util.Locale.ROOT);

        if (!"auto".equals(requested)) {
            return build(requested, modelOverride);
        }
        for (String candidate : List.of("anthropic", "openai", "gemini")) {
            Optional<LlmClient> client = build(candidate, modelOverride);
            if (client.isPresent()) {
                return client;
            }
        }
        return Optional.empty();
    }

    private Optional<LlmClient> build(String provider, String modelOverride) {
        switch (provider) {
            case "anthropic" -> {
                LlmProperties.Provider settings = properties.getAnthropic();
                if (LlmProperties.hasKey(settings.getApiKey())) {
                    return Optional.of(new AnthropicLlmClient(settings.getApiKey(),
                            firstNonBlank(modelOverride, settings.getModel()),
                            properties.getMaxTokens(), properties.getTimeoutSeconds()));
                }
            }
            case "openai" -> {
                LlmProperties.Provider settings = properties.getOpenai();
                if (LlmProperties.hasKey(settings.getApiKey())) {
                    return Optional.of(new OpenAiLlmClient(settings.getApiKey(),
                            firstNonBlank(modelOverride, settings.getModel()),
                            properties.getMaxTokens(), properties.getTimeoutSeconds()));
                }
            }
            case "gemini" -> {
                LlmProperties.Provider settings = properties.getGemini();
                if (LlmProperties.hasKey(settings.getApiKey())) {
                    return Optional.of(new GeminiLlmClient(settings.getApiKey(),
                            firstNonBlank(modelOverride, settings.getModel()),
                            properties.getMaxTokens(), properties.getTimeoutSeconds()));
                }
            }
            default -> {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred != null && !preferred.isBlank() ? preferred : fallback;
    }

    /**
     * @return the providers that currently have a usable key
     */
    public List<String> availableProviders() {
        List<String> providers = new ArrayList<>();
        if (LlmProperties.hasKey(properties.getAnthropic().getApiKey())) {
            providers.add("anthropic");
        }
        if (LlmProperties.hasKey(properties.getOpenai().getApiKey())) {
            providers.add("openai");
        }
        if (LlmProperties.hasKey(properties.getGemini().getApiKey())) {
            providers.add("gemini");
        }
        return providers;
    }

    /**
     * @return the configured properties
     */
    public LlmProperties properties() {
        return properties;
    }
}
