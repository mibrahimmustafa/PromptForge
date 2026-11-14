package com.promptforge.core.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Small text helpers shared by the analyzer and the rewriter.
 */
final class TextUtil {

    private TextUtil() {
    }

    /**
     * Splits text into sentences. Line breaks always end a sentence, which keeps bullet
     * lists and section blocks separated.
     *
     * @param text text to split
     * @return list of trimmed, non-empty sentences
     */
    static List<String> sentences(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            current.append(c);
            boolean boundary = c == '\n' || c == '\r'
                    || (c == '.' || c == '!' || c == '?')
                    && (i + 1 >= text.length() || Character.isWhitespace(text.charAt(i + 1)));
            if (boundary) {
                addIfPresent(result, current.toString());
                current.setLength(0);
            }
        }
        addIfPresent(result, current.toString());
        return result;
    }

    private static void addIfPresent(List<String> target, String candidate) {
        String trimmed = candidate.trim();
        if (!trimmed.isEmpty()) {
            target.add(trimmed);
        }
    }

    /**
     * Counts words in the text.
     *
     * @param text text to measure
     * @return number of whitespace separated tokens
     */
    static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    /**
     * Capitalises the first character and guarantees a trailing full stop.
     *
     * @param text text to normalise
     * @return normalised sentence
     */
    static String asSentence(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        String result = Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
        char last = result.charAt(result.length() - 1);
        if (last != '.' && last != '!' && last != '?' && last != ':') {
            result = result + ".";
        }
        return result;
    }

    /**
     * Removes trailing sentence punctuation.
     *
     * @param text text to clean
     * @return text without trailing {@code . ! ? : ; ,}
     */
    static String stripTrailingPunctuation(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replaceAll("[.!?:;,]+$", "");
    }

    /**
     * Removes markdown bullets and numbering from a line.
     *
     * @param text line to clean
     * @return line without leading list markers
     */
    static String stripBullet(String text) {
        return text == null ? "" : text.trim().replaceFirst("^(?:[-*+•]|\\d+[.)])\\s*", "");
    }
}
