package com.promptforge.core.model;

/**
 * A single finding about the original prompt.
 *
 * @param code       stable machine-readable identifier, e.g. {@code NO_OUTPUT_FORMAT}
 * @param title      short human readable headline
 * @param detail     why it matters for this specific prompt
 * @param severity   how badly it hurts the result
 * @param dimension  the dimension the finding belongs to (may be {@code null})
 * @param suggestion concrete advice
 * @param autoFixed  true when the rewriter already handled it in the reshaped prompt
 */
public record Diagnostic(
        String code,
        String title,
        String detail,
        Severity severity,
        Dimension dimension,
        String suggestion,
        boolean autoFixed) {
}
