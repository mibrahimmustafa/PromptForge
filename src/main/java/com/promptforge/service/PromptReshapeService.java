package com.promptforge.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.promptforge.core.engine.PromptAnalyzer;
import com.promptforge.core.engine.PromptRewriter;
import com.promptforge.core.model.Diagnostic;
import com.promptforge.core.model.Engine;
import com.promptforge.core.model.PromptAnalysis;
import com.promptforge.core.model.PromptProfile;
import com.promptforge.core.model.PromptRequest;
import com.promptforge.core.model.PromptResult;
import com.promptforge.core.model.Severity;
import com.promptforge.llm.LlmClient;
import com.promptforge.llm.LlmException;
import com.promptforge.llm.LlmGateway;

/**
 * Orchestrates the reshape pipeline.
 *
 * <p>The local analyzer and rewriter always run - they produce the analysis, the template and
 * the fallback result. When the caller selects the LLM engine and a provider is configured,
 * the analysis is handed to the model so the rewrite is grounded in concrete findings instead
 * of a guess. If the provider call fails the rule-based result is returned with a note.
 */
@Service
public class PromptReshapeService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PromptAnalyzer analyzer = new PromptAnalyzer();
    private final PromptRewriter rewriter = new PromptRewriter();
    private final LlmGateway gateway;

    public PromptReshapeService(LlmGateway gateway) {
        this.gateway = gateway;
    }

    /**
     * Reshapes the submitted prompt.
     *
     * @param request the request payload
     * @return the result
     */
    public PromptResult reshape(PromptRequest request) {
        PromptProfile profile = request.profileOrDefault();
        Engine engine = request.engineOrDefault();

        PromptAnalysis analysis = analyzer.analyze(request.prompt(), profile);
        PromptRewriter.RewriteOutput rewrite =
                rewriter.rewrite(analysis, profile, request.includeExamplesOrDefault());

        // Template mode keeps the placeholders visible in the reshaped prompt itself, so the
        // user fills the gaps in rather than accepting the defaults.
        boolean strict = request.strictOrDefault();
        String reshaped = strict ? rewrite.template() : rewrite.reshaped();

        if (engine == Engine.RULE) {
            return new PromptResult(analysis.original(), reshaped, rewrite.template(),
                    rewrite.questions(), analysis, Engine.RULE.label(), List.of());
        }

        if (strict) {
            // The LLM path fills the gaps by design - template mode explicitly does not want that.
            List<String> notes = List.of("Template mode keeps the placeholders, so the LLM upgrade "
                    + "was skipped. Switch the engine back to Rule-based or turn template mode off "
                    + "for a fully rewritten prompt.");
            return new PromptResult(analysis.original(), reshaped, rewrite.template(),
                    rewrite.questions(), analysis, "Template mode", notes);
        }

        return reshapeWithLlm(request, analysis, rewrite, reshaped);
    }

    private PromptResult reshapeWithLlm(PromptRequest request, PromptAnalysis analysis,
                                        PromptRewriter.RewriteOutput rewrite, String reshaped) {
        List<String> notes = new ArrayList<>();
        Optional<LlmClient> client = gateway.resolve(request.provider(), request.model());

        if (client.isEmpty()) {
            notes.add("No LLM provider is configured (set ANTHROPIC_API_KEY, OPENAI_API_KEY or "
                    + "GEMINI_API_KEY). Falling back to the local engine.");
            return new PromptResult(analysis.original(), reshaped, rewrite.template(),
                    rewrite.questions(), analysis, Engine.RULE.label() + " (fallback)", notes);
        }

        LlmClient llm = client.get();
        try {
            String raw = llm.complete(systemPrompt(), userPrompt(analysis, rewrite));
            LlmPayload payload = parse(raw);
            String llmPrompt = payload.reshapedPrompt() != null && !payload.reshapedPrompt().isBlank()
                    ? payload.reshapedPrompt()
                    : reshaped;
            List<String> questions = payload.questions().isEmpty()
                    ? rewrite.questions()
                    : payload.questions();
            notes.add("Rewritten by " + llm.provider() + " (" + llm.model() + "), grounded in "
                    + analysis.diagnostics().size() + " analyzer findings.");
            return new PromptResult(analysis.original(), llmPrompt, rewrite.template(), questions,
                    analysis, llm.provider() + " / " + llm.model(), notes);
        } catch (LlmException e) {
            notes.add("LLM call failed (" + e.getMessage() + "). Falling back to the local engine.");
            return new PromptResult(analysis.original(), reshaped, rewrite.template(),
                    rewrite.questions(), analysis, Engine.RULE.label() + " (fallback)", notes);
        } catch (RuntimeException e) {
            notes.add("LLM response could not be parsed (" + e.getMessage() + "). Falling back to the local engine.");
            return new PromptResult(analysis.original(), reshaped, rewrite.template(),
                    rewrite.questions(), analysis, Engine.RULE.label() + " (fallback)", notes);
        }
    }

    // ------------------------------------------------------------------ LLM prompt

    private String systemPrompt() {
        return """
                You are a senior prompt engineer. You rewrite rough, underspecified user requests into \
                best-practice prompts for large language models.

                Every rewritten prompt must contain these sections, in this order:
                1. Role - who the AI is (expertise and seniority).
                2. Task - one explicit, actionable deliverable.
                3. Context - audience, background, stack, and the goal behind the request.
                4. Constraints - hard limits and exclusions.
                5. Output format - a concrete, checkable output shape.
                6. Examples - one representative sample of a good output.
                7. Acceptance criteria - checkable conditions for success.

                Rules:
                - Preserve the user's intent and every explicit constraint they stated.
                - Never invent facts about their business, data or system; state assumptions instead.
                - Make the output format concrete enough to be checked mechanically.
                - Make every acceptance criterion verifiable, never "good quality" or "professional".
                - Keep the prompt as short as the task allows; no filler, no praise, no restating.

                Respond with ONLY a JSON object, no prose and no code fences:
                {"reshapedPrompt": "<the full rewritten prompt as markdown>", "questions": ["<clarifying question>", ...]}
                """;
    }

    private String userPrompt(PromptAnalysis analysis, PromptRewriter.RewriteOutput rewrite) {
        StringBuilder builder = new StringBuilder();
        builder.append("ORIGINAL PROMPT\n");
        builder.append("\"\"\"\n").append(analysis.original()).append("\n\"\"\"\n\n");
        builder.append("ANALYZER SCORE: ").append(analysis.score()).append("/100 (")
                .append(analysis.grade()).append(")\n");
        builder.append("DETECTED TASK TYPE: ").append(analysis.taskType().label()).append("\n\n");
        builder.append("ANALYZER FINDINGS\n");
        for (Diagnostic diagnostic : analysis.diagnostics()) {
            builder.append("- [").append(diagnostic.severity().name()).append("] ")
                    .append(diagnostic.title()).append(": ").append(diagnostic.suggestion()).append("\n");
        }
        builder.append("\nDRAFT TEMPLATE (structure to follow, fill with concrete values)\n");
        builder.append("\"\"\"\n").append(rewrite.template()).append("\n\"\"\"\n\n");
        builder.append("Produce the final reshaped prompt and the clarifying questions that are still open.");
        return builder.toString();
    }

    // ------------------------------------------------------------------ parsing

    private record LlmPayload(String reshapedPrompt, List<String> questions) {
    }

    private LlmPayload parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("empty response");
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "");
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("no JSON object in response");
        }
        try {
            JsonNode node = MAPPER.readTree(cleaned.substring(start, end + 1));
            String reshaped = node.path("reshapedPrompt").asText("");
            List<String> questions = new ArrayList<>();
            for (JsonNode question : node.path("questions")) {
                String value = question.asText("").trim();
                if (!value.isEmpty()) {
                    questions.add(value);
                }
            }
            return new LlmPayload(reshaped, questions);
        } catch (Exception e) {
            throw new IllegalStateException("invalid JSON: " + e.getMessage(), e);
        }
    }

    /**
     * @return the severity levels, useful for the UI legend
     */
    public static List<Severity> severities() {
        return List.of(Severity.values());
    }
}
