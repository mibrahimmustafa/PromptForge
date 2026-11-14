package com.promptforge.core.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.promptforge.core.model.Dimension;
import com.promptforge.core.model.PromptAnalysis;
import com.promptforge.core.model.PromptProfile;
import com.promptforge.core.model.TaskType;

/**
 * Turns an analysis into two artefacts:
 *
 * <ul>
 *   <li>the <b>reshaped prompt</b> - complete and usable right now, with sensible defaults
 *       filled in for anything the original prompt did not specify;</li>
 *   <li>the <b>template prompt</b> - the same skeleton with explicit {@code <placeholders>} for
 *       every gap, so nothing is silently invented.</li>
 * </ul>
 *
 * <p>Plus the list of clarifying questions that would close the remaining gaps.
 */
public class PromptRewriter {

    /**
     * The output of a rewrite.
     *
     * @param reshaped usable prompt
     * @param template prompt skeleton with placeholders
     * @param questions clarifying questions
     */
    public record RewriteOutput(String reshaped, String template, List<String> questions) {
    }

    /**
     * Rewrites the prompt described by the analysis.
     *
     * @param analysis         analysis of the original prompt
     * @param profile          the selected domain profile
     * @param includeExamples  whether an Examples section should be present
     * @return the rewrite artefacts
     */
    public RewriteOutput rewrite(PromptAnalysis analysis, PromptProfile profile, boolean includeExamples) {
        TaskType taskType = analysis.taskType();
        String lower = DomainLexicon.normalise(analysis.original());

        String role = resolveRole(analysis, lower);
        String task = resolveTask(analysis, taskType);
        String audience = analysis.detectedAudience();
        String stack = analysis.detectedStack();
        String goal = analysis.detectedGoal();
        List<String> constraints = new ArrayList<>(analysis.extractedConstraints());

        StringBuilder reshaped = new StringBuilder();
        StringBuilder template = new StringBuilder();

        // ---------------------------------------------------------------- role
        reshaped.append("## Role\n").append(role).append("\n\n");
        template.append("## Role\nYou are ")
                .append(orPlaceholder(analysis.detectedRole() != null
                        ? stripRolePrefix(analysis.detectedRole()) : null,
                        "expert persona and seniority"))
                .append("\n\n");

        // ---------------------------------------------------------------- task
        reshaped.append("## Task\n").append(task).append("\n\n");
        template.append("## Task\n")
                .append(orPlaceholder(analysis.taskStatement(), "the deliverable, e.g. write a migration plan"))
                .append("\n\n");

        // ---------------------------------------------------------------- context
        reshaped.append("## Context\n");
        if (audience != null) {
            reshaped.append("- Audience: ").append(audience).append("\n");
        } else {
            reshaped.append("- Audience: ").append(PromptTemplates.audienceDefault(taskType)).append("\n");
        }
        if (stack != null) {
            reshaped.append("- Stack: ").append(stack).append("\n");
        } else {
            reshaped.append("- Domain: ").append(taskType.label()).append("\n");
        }
        if (goal != null) {
            reshaped.append("- Goal: ").append(goal).append("\n");
        } else {
            reshaped.append("- Goal: ").append(defaultGoal(taskType)).append("\n");
        }
        reshaped.append("\n");

        template.append("## Context\n");
        template.append("- Audience: ").append(orPlaceholder(audience, "who reads this")).append("\n");
        template.append("- Stack: ").append(orPlaceholder(stack, "technologies / domain")).append("\n");
        template.append("- Goal: ").append(orPlaceholder(goal, "what success enables")).append("\n\n");

        // ---------------------------------------------------------------- constraints
        List<String> defaults = PromptTemplates.defaultConstraints(taskType);
        List<String> merged = new ArrayList<>(constraints);
        for (String candidate : defaults) {
            if (merged.stream().noneMatch(existing -> similar(existing, candidate))) {
                merged.add(candidate);
            }
        }
        reshaped.append("## Constraints\n");
        for (String constraint : merged) {
            reshaped.append("- ").append(capitalise(constraint)).append("\n");
        }
        reshaped.append("\n");

        template.append("## Constraints\n");
        if (constraints.isEmpty()) {
            template.append("- <hard limits: length, technology, tone, exclusions>\n");
        } else {
            for (String constraint : constraints) {
                template.append("- ").append(capitalise(constraint)).append("\n");
            }
            template.append("- <any further limits>\n");
        }
        template.append("\n");

        // ---------------------------------------------------------------- output format
        String format = analysis.extractedFormat();
        reshaped.append("## Output Format\n");
        if (format != null && !format.isBlank()) {
            reshaped.append(capitalise(format)).append(".\n\n");
        } else {
            reshaped.append(capitalise(PromptTemplates.defaultFormat(taskType))).append(".\n\n");
        }
        template.append("## Output Format\n")
                .append(orPlaceholder(format != null && !format.isBlank() ? format : null,
                        "exact output shape, e.g. Markdown table with columns X, Y, Z"))
                .append("\n\n");

        // ---------------------------------------------------------------- examples
        if (includeExamples) {
            reshaped.append("## Examples\n");
            if (analysis.extractedExamples().isEmpty()) {
                reshaped.append("Include one representative example of the output you expect.\n\n");
            } else {
                for (String example : analysis.extractedExamples()) {
                    reshaped.append("- ").append(example).append("\n");
                }
                reshaped.append("\n");
            }
            template.append("## Examples\n");
            if (analysis.extractedExamples().isEmpty()) {
                template.append("<one sample of a good output>\n\n");
            } else {
                for (String example : analysis.extractedExamples()) {
                    template.append("- ").append(example).append("\n");
                }
                template.append("\n");
            }
        }

        // ---------------------------------------------------------------- acceptance criteria
        reshaped.append("## Acceptance Criteria\n");
        List<String> criteria = PromptTemplates.acceptanceCriteria(taskType);
        for (String criterion : criteria) {
            reshaped.append("- ").append(criterion).append("\n");
        }
        reshaped.append("\n");

        template.append("## Acceptance Criteria\n");
        for (String criterion : criteria) {
            template.append("- ").append(criterion).append("\n");
        }
        template.append("- <additional checkable conditions>\n");

        return new RewriteOutput(reshaped.toString().trim(), template.toString().trim(),
                buildQuestions(analysis));
    }

    // ------------------------------------------------------------------ helpers

    private String resolveRole(PromptAnalysis analysis, String lower) {
        if (analysis.detectedRole() != null) {
            String role = analysis.detectedRole();
            String capitalised = Character.toUpperCase(role.charAt(0)) + role.substring(1);
            return capitalised.endsWith(".") ? capitalised : capitalised + ".";
        }
        String inferred = DomainLexicon.roleFor(lower);
        if (inferred != null) {
            return "You are " + inferred + ".";
        }
        return "You are " + PromptTemplates.defaultRole(analysis.taskType()) + ".";
    }

    /**
     * The task section keeps only the actionable core. Everything else the prompt said is
     * already carried by the context, constraints and format sections - repeating it here
     * would make the prompt longer without adding information.
     *
     * <p>When no action could be extracted the user's own request is quoted instead, so the
     * reshaped prompt still carries the intent instead of pointing at a request that is not
     * in the document.
     *
     * @param analysis the analysis
     * @param taskType the detected task type
     * @return the task line
     */
    private String resolveTask(PromptAnalysis analysis, TaskType taskType) {
        if (analysis.taskStatement() != null && !analysis.taskStatement().isBlank()) {
            return TextUtil.asSentence(analysis.taskStatement().trim());
        }
        String request = analysis.cleanedRequest() != null && !analysis.cleanedRequest().isBlank()
                ? analysis.cleanedRequest().trim()
                : analysis.original().trim();
        if (request.isEmpty()) {
            return "Produce a clear, complete " + taskType.label().toLowerCase(Locale.ROOT)
                    + " response that follows the format and constraints above.";
        }
        return "Fulfil the request below completely, following the format and constraints above.\n\n"
                + "\"" + request + "\"";
    }

    private String defaultGoal(TaskType taskType) {
        return switch (taskType) {
            case CODE -> "code that can be merged without rework";
            case DATA -> "a decision the reader can take with the numbers in front of them";
            case WRITING -> "a message the reader understands and acts on";
            case RESEARCH -> "an evidence-backed answer with the open questions made explicit";
            case PRODUCT -> "a scope the team can actually ship";
            case BUSINESS -> "a recommendation that can be funded and executed";
            case GENERAL -> "an answer the reader can act on immediately";
        };
    }

    /**
     * Returns the value when it is known, otherwise a visible placeholder. The template only
     * marks real gaps - a known value is written out in full.
     *
     * @param value         the detected value, may be {@code null}
     * @param gapDescription plain description of what the user still has to supply
     * @return the value or the placeholder
     */
    private String orPlaceholder(String value, String gapDescription) {
        if (value == null || value.isBlank()) {
            return "<" + gapDescription + ">";
        }
        return value.trim();
    }

    private String stripRolePrefix(String role) {
        String result = role;
        for (String prefix : new String[]{"you are", "you're", "act as", "acting as", "act like",
                "your role is", "you will be", "assume the role of"}) {
            if (result.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                result = result.substring(prefix.length()).trim();
                break;
            }
        }
        return TextUtil.stripTrailingPunctuation(result);
    }

    /**
     * Capitalises the first letter of a clause so every bullet reads as a sentence.
     *
     * @param text the clause
     * @return the capitalised clause
     */
    private String capitalise(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private boolean similar(String a, String b) {
        String left = a.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z ]", "");
        String right = b.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z ]", "");
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        return left.contains(right) || right.contains(left);
    }

    private List<String> buildQuestions(PromptAnalysis analysis) {
        List<String> questions = new ArrayList<>();
        if (analysis.detectedRole() == null) {
            questions.add("Who should the AI be? Name the persona and its seniority "
                    + "(for example \"a senior Java engineer\").");
        }
        if (analysis.detectedAudience() == null) {
            questions.add("Who reads the output, and what do they already know?");
        }
        if (analysis.detectedGoal() == null) {
            questions.add("What does this output have to achieve - what does the reader do with it?");
        }
        if (analysis.extractedFormat() == null || analysis.extractedFormat().isBlank()) {
            questions.add("What exact output shape do you want (JSON schema, Markdown table, code block, "
                    + "numbered steps, ...)?");
        }
        if (analysis.extractedConstraints().isEmpty()) {
            questions.add("What are the hard limits - maximum length, allowed libraries, tone, and anything "
                    + "that would make the answer wrong?");
        }
        if (analysis.taskStatement() == null) {
            questions.add("What exactly should be produced, and what is the deliverable called?");
        }
        questions.add("Should the answer include one example of a good output, and if so what does it look like?");
        return questions;
    }

    /**
     * Dimensions that are missing from the prompt, useful for UI badges.
     *
     * @param analysis the analysis
     * @return missing dimensions
     */
    public static List<Dimension> missingDimensions(PromptAnalysis analysis) {
        List<Dimension> missing = new ArrayList<>();
        for (var score : analysis.dimensions()) {
            if (!score.present()) {
                missing.add(score.dimension());
            }
        }
        return missing;
    }
}
