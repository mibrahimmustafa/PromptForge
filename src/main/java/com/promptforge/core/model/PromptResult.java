package com.promptforge.core.model;

import java.util.List;

/**
 * The full response returned by the reshape service.
 *
 * @param original       the prompt as submitted
 * @param reshapedPrompt ready-to-use improved prompt
 * @param templatePrompt fill-in-the-blank template with explicit placeholders
 * @param questions      clarifying questions the user should answer to close remaining gaps
 * @param analysis       the analysis of the original prompt
 * @param engineUsed     which engine actually produced the result
 * @param notes          engine notes, e.g. a fallback notice
 */
public record PromptResult(
        String original,
        String reshapedPrompt,
        String templatePrompt,
        List<String> questions,
        PromptAnalysis analysis,
        String engineUsed,
        List<String> notes) {
}
