package com.promptforge.llm;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Shared HTTP plumbing for the provider clients.
 */
abstract class HttpLlmSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http;
    private final Duration timeout;

    protected HttpLlmSupport(int timeoutSeconds) {
        this.timeout = Duration.ofSeconds(Math.max(5, timeoutSeconds));
        this.http = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    protected static ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * Posts a JSON body and returns the parsed response.
     *
     * @param url     target url
     * @param headers request headers
     * @param body    json body
     * @return the parsed JSON tree
     */
    protected JsonNode post(String url, Map<String, String> headers, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(builder::header);
        try {
            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new LlmException(providerName() + " returned HTTP " + response.statusCode() + ": "
                        + shorten(response.body()));
            }
            return MAPPER.readTree(response.body());
        } catch (LlmException e) {
            throw e;
        } catch (IOException e) {
            throw new LlmException(providerName() + " request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException(providerName() + " request was interrupted.", e);
        }
    }

    private static String shorten(String body) {
        if (body == null) {
            return "";
        }
        String cleaned = body.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 400 ? cleaned.substring(0, 400) + "..." : cleaned;
    }

    /**
     * @return the provider name used in error messages
     */
    protected abstract String providerName();
}
