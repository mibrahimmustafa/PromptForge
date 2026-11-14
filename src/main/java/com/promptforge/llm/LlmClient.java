package com.promptforge.llm;

/**
 * Minimal provider-agnostic contract for a chat completion.
 */
public interface LlmClient {

    /**
     * @return the provider name, e.g. {@code anthropic}
     */
    String provider();

    /**
     * @return the model id this client talks to
     */
    String model();

    /**
     * Runs a completion.
     *
     * @param system the system instruction
     * @param user   the user message
     * @return the raw assistant text
     * @throws LlmException when the provider call fails
     */
    String complete(String system, String user);
}
