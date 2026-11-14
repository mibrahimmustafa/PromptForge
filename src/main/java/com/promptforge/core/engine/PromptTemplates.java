package com.promptforge.core.engine;

import java.util.List;

import com.promptforge.core.model.PromptProfile;
import com.promptforge.core.model.TaskType;

/**
 * Default role, output format and acceptance criteria per type of work.
 *
 * <p>These are the fallbacks the rewriter uses when the original prompt does not supply
 * the information. They are deliberately concrete: a default should be usable as-is.
 */
final class PromptTemplates {

    private PromptTemplates() {
    }

    static String defaultRole(TaskType taskType) {
        return switch (taskType) {
            case CODE -> "a senior software engineer who writes production-ready, maintainable code";
            case DATA -> "a data analyst who shows the numbers behind every conclusion";
            case WRITING -> "a senior writer who adapts the message to the reader";
            case RESEARCH -> "a research analyst who separates verified fact from inference";
            case PRODUCT -> "a product manager who connects user problems to shippable scope";
            case BUSINESS -> "a strategy consultant who makes assumptions and trade-offs explicit";
            case GENERAL -> "a senior domain expert who answers directly and states assumptions";
        };
    }

    static String defaultFormat(TaskType taskType) {
        return switch (taskType) {
            case CODE -> "a fenced code block with the complete solution, followed by a short bullet list "
                    + "explaining the key decisions and any trade-offs";
            case DATA -> "a Markdown table with the key metrics, followed by three bullet points of insight";
            case WRITING -> "Markdown with a short headline, the body in short paragraphs, and bullet points "
                    + "where lists are clearer than prose";
            case RESEARCH -> "Markdown with a summary section, a findings section, and a table comparing the "
                    + "options on the criteria that matter";
            case PRODUCT -> "Markdown with the problem statement, three solution options with trade-offs, "
                    + "and a recommendation with success metrics";
            case BUSINESS -> "Markdown with an executive summary, the recommendation, the supporting rationale "
                    + "as bullet points, and the risks";
            case GENERAL -> "Markdown with a direct answer first, then the reasoning as bullet points";
        };
    }

    static List<String> acceptanceCriteria(TaskType taskType) {
        return switch (taskType) {
            case CODE -> List.of(
                    "The solution compiles and is complete - no placeholder methods or pseudo-code.",
                    "Every dependency used is named explicitly.",
                    "Edge cases and failure modes are handled, not just the happy path.",
                    "The explanation names the trade-offs instead of restating the code.");
            case DATA -> List.of(
                    "Every number is traceable to the source data or is explicitly labelled as an estimate.",
                    "The unit of every metric is stated.",
                    "No correlation is presented as causation.",
                    "Data gaps are listed rather than silently smoothed over.");
            case WRITING -> List.of(
                    "The opening line states the single most important point.",
                    "Every claim that could be checked is supported.",
                    "The tone matches the audience and no jargon is unexplained.",
                    "The text says what the reader should do next.");
            case RESEARCH -> List.of(
                    "Every non-obvious claim carries a source.",
                    "Facts, inferences and opinions are visually distinguishable.",
                    "Contradictory evidence is reported, not filtered out.",
                    "Unknowns are listed as open questions.");
            case PRODUCT -> List.of(
                    "Each option is tied to a named user problem.",
                    "Success is measured with a metric and a target.",
                    "Scope is small enough to ship in the stated timeframe.",
                    "The recommendation states what would invalidate it.");
            case BUSINESS -> List.of(
                    "The recommendation is actionable by the stated audience.",
                    "Assumptions and cost drivers are listed explicitly.",
                    "Risks include a mitigation for each.",
                    "The rationale links every claim back to the stated objective.");
            case GENERAL -> List.of(
                    "The answer directly addresses what was asked, with no preamble.",
                    "Every claim is either stated as fact, stated as an assumption, or marked as unknown.",
                    "The requested format is respected exactly.",
                    "The response is no longer than the request requires.");
        };
    }

    static List<String> defaultConstraints(TaskType taskType) {
        List<String> constraints = new java.util.ArrayList<>();
        constraints.add("Keep the response under 400 words unless the task requires more.");
        constraints.add("Do not invent facts, data, APIs or citations - state what is unknown instead.");
        constraints.add("State your assumptions explicitly when the request is ambiguous.");
        if (taskType == TaskType.CODE) {
            constraints.add("Use only the technologies and libraries named in the context - no new dependencies.");
            constraints.add("Never generate code that only works in theory; it must run as written.");
        } else if (taskType == TaskType.DATA) {
            constraints.add("Show the query or the calculation used to obtain each number.");
        } else if (taskType == TaskType.WRITING) {
            constraints.add("Avoid filler openers, hype and unearned superlatives.");
        }
        return constraints;
    }

    static String audienceDefault(TaskType taskType) {
        return switch (taskType) {
            case CODE -> "engineers on the team who will maintain this code";
            case DATA -> "a business stakeholder who will act on the findings";
            case WRITING -> "the reader of the published piece";
            case RESEARCH -> "a decision maker who needs the evidence, not the background";
            case PRODUCT -> "the cross-functional team that will build and ship it";
            case BUSINESS -> "an executive who will fund the decision";
            case GENERAL -> "the person who asked, who will act on the answer";
        };
    }

    static String profileName(PromptProfile profile) {
        return profile.label();
    }
}
