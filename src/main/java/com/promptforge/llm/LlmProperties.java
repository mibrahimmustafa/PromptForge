package com.promptforge.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the optional LLM upgrade path.
 *
 * <p>All keys are supplied through environment variables so nothing sensitive is committed:
 * {@code ANTHROPIC_API_KEY}, {@code OPENAI_API_KEY}, {@code GEMINI_API_KEY}.
 */
@ConfigurationProperties(prefix = "promptforge.llm")
public class LlmProperties {

    /** Preferred provider: {@code auto}, {@code anthropic}, {@code openai} or {@code gemini}. */
    private String provider = "auto";

    /** Anthropic settings. */
    private final Provider anthropic = new Provider("claude-sonnet-5-5");

    /** OpenAI settings. */
    private final Provider openai = new Provider("gpt-5.5");

    /** Gemini settings. */
    private final Provider gemini = new Provider("gemini-3.8-flash");

    /** Maximum tokens requested from the model. */
    private int maxTokens = 4000;

    /** Request timeout in seconds. */
    private int timeoutSeconds = 60;

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Provider getAnthropic() {
        return anthropic;
    }

    public Provider getOpenai() {
        return openai;
    }

    public Provider getGemini() {
        return gemini;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Settings of a single provider.
     */
    public static class Provider {

        private String apiKey = "";
        private String model;

        public Provider(String defaultModel) {
            this.model = defaultModel;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    /**
     * @param key the candidate key
     * @return true when the key is present and non-blank
     */
    public static boolean hasKey(String key) {
        return key != null && !key.isBlank();
    }
}
