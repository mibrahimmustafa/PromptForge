package com.promptforge.llm;

import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * OpenAI Chat Completions client.
 */
public class OpenAiLlmClient extends HttpLlmSupport implements LlmClient {

    private static final String URL = "https://api.openai.com/v1/chat/completions";

    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public OpenAiLlmClient(String apiKey, String model, int maxTokens, int timeoutSeconds) {
        super(timeoutSeconds);
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public String provider() {
        return "openai";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String complete(String system, String user) {
        ObjectNode body = mapper().createObjectNode();
        body.put("model", model);
        body.put("max_completion_tokens", maxTokens);
        ArrayNode messages = body.putArray("messages");
        ObjectNode systemMessage = messages.addObject();
        systemMessage.put("role", "system");
        systemMessage.put("content", system);
        ObjectNode userMessage = messages.addObject();
        userMessage.put("role", "user");
        userMessage.put("content", user);

        JsonNode response = post(URL, Map.of("Authorization", "Bearer " + apiKey), body.toString());

        String text = response.path("choices").path(0).path("message").path("content").asText("");
        if (text.isBlank()) {
            throw new LlmException("OpenAI returned no message content.");
        }
        return text;
    }

    @Override
    protected String providerName() {
        return "OpenAI";
    }
}
