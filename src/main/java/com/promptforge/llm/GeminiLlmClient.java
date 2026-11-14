package com.promptforge.llm;

import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Google Gemini generateContent client.
 */
public class GeminiLlmClient extends HttpLlmSupport implements LlmClient {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public GeminiLlmClient(String apiKey, String model, int maxTokens, int timeoutSeconds) {
        super(timeoutSeconds);
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    @Override
    public String provider() {
        return "gemini";
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String complete(String system, String user) {
        ObjectNode body = mapper().createObjectNode();

        ObjectNode systemInstruction = body.putObject("systemInstruction");
        ArrayNode systemParts = systemInstruction.putArray("parts");
        systemParts.addObject().put("text", system);

        ArrayNode contents = body.putArray("contents");
        ObjectNode content = contents.addObject();
        content.put("role", "user");
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", user);

        body.putObject("generationConfig").put("maxOutputTokens", maxTokens);

        String url = BASE_URL + model + ":generateContent";
        JsonNode response = post(url, Map.of("x-goog-api-key", apiKey), body.toString());

        StringBuilder text = new StringBuilder();
        for (JsonNode candidate : response.path("candidates")) {
            for (JsonNode part : candidate.path("content").path("parts")) {
                text.append(part.path("text").asText(""));
            }
        }
        if (text.isEmpty()) {
            throw new LlmException("Gemini returned no text content.");
        }
        return text.toString();
    }

    @Override
    protected String providerName() {
        return "Gemini";
    }
}
