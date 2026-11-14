package com.promptforge.core.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.promptforge.core.model.PromptAnalysis;
import com.promptforge.core.model.PromptProfile;

class PromptRewriterTest {

    private final PromptAnalyzer analyzer = new PromptAnalyzer();
    private final PromptRewriter rewriter = new PromptRewriter();

    private PromptRewriter.RewriteOutput rewrite(String prompt, PromptProfile profile) {
        PromptAnalysis analysis = analyzer.analyze(prompt, profile);
        return rewriter.rewrite(analysis, profile, true);
    }

    @Test
    @DisplayName("the reshaped prompt contains every section of the anatomy")
    void reshapedPromptHasAllSections() {
        PromptRewriter.RewriteOutput output = rewrite(
                "can you write me a good post about our new api, professional and not too long, for developers",
                PromptProfile.WRITING);

        assertThat(output.reshaped())
                .contains("## Role")
                .contains("## Task")
                .contains("## Context")
                .contains("## Constraints")
                .contains("## Output Format")
                .contains("## Acceptance Criteria");
        assertThat(output.reshaped()).contains("Audience:");
        assertThat(output.reshaped()).contains("- ");
    }

    @Test
    @DisplayName("the template keeps explicit placeholders for unknown sections")
    void templateKeepsPlaceholders() {
        PromptRewriter.RewriteOutput output = rewrite("write something about sales", PromptProfile.GENERALIST);

        assertThat(output.template()).contains("<");
        assertThat(output.template()).contains("## Role");
        assertThat(output.template()).contains("## Acceptance Criteria");
        assertThat(output.template()).doesNotContain("<<");
    }

    @Test
    @DisplayName("the template writes known values in full and only marks real gaps")
    void templateWritesKnownValuesInFull() {
        PromptRewriter.RewriteOutput output = rewrite(
                "You are a senior data analyst. Analyse the revenue CSV and report the trend.",
                PromptProfile.DATA_ANALYSIS);

        assertThat(output.template()).contains("senior data analyst");
        assertThat(output.template()).contains("Analyse the revenue CSV");
        assertThat(output.template()).contains("<what success enables>");
        assertThat(output.template()).doesNotContain("<<");
    }

    @Test
    @DisplayName("an existing role is preserved rather than replaced")
    void existingRoleIsPreserved() {
        PromptRewriter.RewriteOutput output = rewrite(
                "You are a senior data analyst. Analyse the revenue CSV and report the trend.",
                PromptProfile.DATA_ANALYSIS);

        assertThat(output.reshaped()).contains("data analyst");
    }

    @Test
    @DisplayName("clarifying questions are produced for a vague prompt")
    void vaguePromptProducesQuestions() {
        PromptRewriter.RewriteOutput output = rewrite("help me with the report", PromptProfile.GENERALIST);

        assertThat(output.questions()).isNotEmpty();
        assertThat(output.questions().stream().anyMatch(q -> q.toLowerCase().contains("audience")
                || q.toLowerCase().contains("persona"))).isTrue();
    }

    @Test
    @DisplayName("explicit constraints from the original prompt are carried over")
    void originalConstraintsAreCarriedOver() {
        PromptRewriter.RewriteOutput output = rewrite(
                "Write a migration guide for Postgres. Do not use any ORM and keep it under 200 words.",
                PromptProfile.CODING);

        assertThat(output.reshaped()).contains("Do not use any ORM");
        assertThat(output.reshaped()).contains("under 200 words");
    }

    @Test
    @DisplayName("the reshaped prompt is never empty")
    void reshapedPromptIsNeverEmpty() {
        PromptRewriter.RewriteOutput output = rewrite("", PromptProfile.GENERALIST);
        assertThat(output.reshaped()).isNotBlank();
    }

    @Test
    @DisplayName("the original request survives when no action can be extracted")
    void originalRequestSurvivesWithoutATask() {
        PromptRewriter.RewriteOutput output = rewrite("spring boot pagination for orders", PromptProfile.CODING);

        assertThat(output.reshaped()).contains("spring boot pagination for orders");
        assertThat(output.reshaped()).doesNotContain("the request below, following");
        assertThat(output.reshaped()).contains("Fulfil the request below completely");
    }

    @Test
    @DisplayName("a request wrapped in politeness is quoted without the wrapper")
    void politenessWrapperIsRemovedFromTheQuotedRequest() {
        PromptRewriter.RewriteOutput output = rewrite("can you spring boot pagination for orders",
                PromptProfile.CODING);

        assertThat(output.reshaped()).contains("spring boot pagination for orders");
        assertThat(output.reshaped()).doesNotContain("can you spring boot");
    }
}
