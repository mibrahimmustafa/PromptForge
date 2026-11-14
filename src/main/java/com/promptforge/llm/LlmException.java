package com.promptforge.llm;

/**
 * Raised when a provider call fails. The message is safe to show to the user.
 */
public class LlmException extends RuntimeException {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
