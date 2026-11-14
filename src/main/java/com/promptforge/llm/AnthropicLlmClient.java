package com.promptforge.llm;

import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Anthropic Messages API client.
 */
public class AnthropicLlmClient extends HttpLlmSupport implements LlmClient {

    private static final String URL = "https://api.anthropic.com/v1/messages";

    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public AnthropicLlmClient(String apiKey, String model, int maxTokens, int timeoutSeconds) {
        super(timeoutSeconds);
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public String provider() {
        return "anthropic";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String complete(String system, String user) {
        ObjectNode body = mapper().createObjectNode();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", system);
        ArrayNode messages = body.putArray("messages");
        ObjectNode message = messages.addObject();
        message.put("role", "user");
        message.put("content", user);

        JsonNode response = post(URL, Map.of(
                "x-api-key", apiKey,
                "anthropic-version", "2023-06-01"), body.toString());

        JsonNode content = response.path("content");
        StringBuilder text = new StringBuilder();
        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        if (text.isEmpty()) {
            throw new LlmException("Anthropic returned no text content.");
        }
        return text.toString();
    }

    @Override
    protected String providerName() {
        return "Anthropic";
    }
}
