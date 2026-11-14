package com.promptforge.core.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.promptforge.core.model.Diagnostic;
import com.promptforge.core.model.Dimension;
import com.promptforge.core.model.PromptAnalysis;
import com.promptforge.core.model.PromptProfile;
import com.promptforge.core.model.Severity;

class PromptAnalyzerTest {

    private final PromptAnalyzer analyzer = new PromptAnalyzer();

    @Test
    @DisplayName("a well formed prompt scores high and reports no critical findings")
    void wellFormedPromptScoresHigh() {
        String prompt = """
                You are a senior Java engineer specialising in Spring Boot.
                Write a REST endpoint that returns paginated customer orders.
                The audience is a frontend team using our documented JSON contract.
                Use only Spring Boot and JPA, no new dependencies, and keep the code under 120 lines.
                Respond with a Markdown table containing the file, the purpose and the endpoint signature.
                Include one example request and response.
                The acceptance criteria are: the endpoint compiles, pagination is total-safe, and the \
                JSON matches the existing contract.
                """;

        PromptAnalysis analysis = analyzer.analyze(prompt, PromptProfile.CODING);

        assertThat(analysis.score()).isGreaterThanOrEqualTo(80);
        assertThat(analysis.diagnostics())
                .filteredOn(d -> d.severity() == Severity.CRITICAL)
                .isEmpty();
        assertThat(analysis.diagnostics())
                .filteredOn(d -> d.code().equals("NO_OUTPUT_FORMAT"))
                .isEmpty();
        assertThat(analysis.taskType()).isEqualTo(com.promptforge.core.model.TaskType.CODE);
    }

    @Test
    @DisplayName("a vague request is flagged for the missing output format")
    void vagueRequestIsFlagged() {
        PromptAnalysis analysis = analyzer.analyze(
                "can you write me a good post about our new api, professional and not too long",
                PromptProfile.GENERALIST);

        List<Diagnostic> critical = analysis.diagnostics().stream()
                .filter(d -> d.severity() == Severity.CRITICAL)
                .toList();

        assertThat(critical).isNotEmpty();
        assertThat(critical).anyMatch(d -> d.code().equals("NO_OUTPUT_FORMAT"));
        assertThat(analysis.score()).isLessThan(50);
        assertThat(analysis.diagnostics())
                .anyMatch(d -> d.code().equals("POLITE_FRAME"));
    }

    @Test
    @DisplayName("the polite wrapper is removed from the extracted task")
    void politeWrapperIsStripped() {
        PromptAnalysis analysis = analyzer.analyze(
                "Please could you write a migration plan for our monolith to microservices",
                PromptProfile.GENERALIST);

        assertThat(analysis.taskStatement()).isNotNull();
        assertThat(analysis.taskStatement().toLowerCase()).doesNotContain("could you");
        assertThat(analysis.taskStatement().toLowerCase()).contains("migration plan");
    }

    @Test
    @DisplayName("the \"I need you to...\" wrapper is removed from the extracted task")
    void needYouToWrapperIsStripped() {
        PromptAnalysis analysis = analyzer.analyze(
                "I need you to write a function in java that sorts a list", PromptProfile.CODING);

        assertThat(analysis.taskStatement()).isNotNull();
        assertThat(analysis.taskStatement().toLowerCase()).doesNotContain("i need you to");
        assertThat(analysis.taskStatement().toLowerCase()).startsWith("write");
    }

    @Test
    @DisplayName("the \"I want to...\" wrapper is removed from the extracted task")
    void wantToWrapperIsStripped() {
        PromptAnalysis analysis = analyzer.analyze(
                "I want to write a function in java that sorts a list", PromptProfile.CODING);

        assertThat(analysis.taskStatement().toLowerCase()).startsWith("write");
    }

    @Test
    @DisplayName("the role is detected when the prompt states one")
    void roleIsDetected() {
        PromptAnalysis analysis = analyzer.analyze(
                "You are a senior data analyst. Analyse the sales CSV.",
                PromptProfile.DATA_ANALYSIS);

        assertThat(analysis.detectedRole()).contains("data analyst");
        assertThat(analysis.scoreOf(Dimension.ROLE)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("conflicting scopes are reported")
    void conflictingScopesAreReported() {
        PromptAnalysis analysis = analyzer.analyze(
                "Write a simple but comprehensive guide that is short yet detailed about Java streams.",
                PromptProfile.CODING);

        assertThat(analysis.diagnostics())
                .anyMatch(d -> d.code().equals("CONFLICTING_SCOPES"));
    }

    @Test
    @DisplayName("an empty prompt is reported as critical")
    void emptyPromptIsCritical() {
        PromptAnalysis analysis = analyzer.analyze("   ", PromptProfile.GENERALIST);

        assertThat(analysis.score()).isZero();
        assertThat(analysis.diagnostics())
                .anyMatch(d -> d.code().equals("EMPTY_PROMPT") && d.severity() == Severity.CRITICAL);
    }

    @Test
    @DisplayName("constraints and the stack are extracted from the text")
    void constraintsAndStackAreExtracted() {
        PromptAnalysis analysis = analyzer.analyze(
                "Using Spring Boot and PostgreSQL, write a migration for the orders table. "
                        + "Do not use any new dependencies and keep the downtime under one minute.",
                PromptProfile.CODING);

        assertThat(analysis.extractedConstraints()).isNotEmpty();
        assertThat(analysis.detectedStack()).contains("Spring Boot");
        assertThat(analysis.detectedStack()).contains("PostgreSQL");
    }

    @Test
    @DisplayName("a generic stack token is dropped when a specific one covers it")
    void genericStackTokenIsDropped() {
        PromptAnalysis analysis = analyzer.analyze(
                "Write a Spring Boot endpoint using JPA.", PromptProfile.CODING);

        assertThat(analysis.detectedStack()).contains("Spring Boot");
        assertThat(analysis.detectedStack()).doesNotContain("Spring,");
    }

    @Test
    @DisplayName("a constraint keeps the full noun phrase it applies to")
    void constraintKeepsItsNounPhrase() {
        PromptAnalysis analysis = analyzer.analyze(
                "Use only Spring Boot and JPA, no new dependencies.", PromptProfile.CODING);

        assertThat(analysis.extractedConstraints())
                .anyMatch(c -> c.contains("Spring Boot and JPA"));
        assertThat(analysis.extractedConstraints())
                .anyMatch(c -> c.toLowerCase().contains("no new dependencies"));
    }

    @Test
    @DisplayName("the requested output format is captured with its detail")
    void outputFormatIsCaptured() {
        PromptAnalysis analysis = analyzer.analyze(
                "Respond with a Markdown table containing the file and the endpoint signature.",
                PromptProfile.CODING);

        assertThat(analysis.extractedFormat()).isNotNull();
        assertThat(analysis.extractedFormat().toLowerCase()).contains("markdown table");
        assertThat(analysis.extractedFormat().toLowerCase()).contains("endpoint signature");
    }

    @Test
    @DisplayName("an input file name is not mistaken for a requested output format")
    void inputFileIsNotAnOutputFormat() {
        PromptAnalysis analysis = analyzer.analyze(
                "Analyze our sales csv and tell me what's going on with revenue.", PromptProfile.DATA_ANALYSIS);

        assertThat(analysis.extractedFormat()).isNull();
        assertThat(analysis.diagnostics())
                .anyMatch(d -> d.code().equals("NO_OUTPUT_FORMAT"));
    }

    @Test
    @DisplayName("a format word in an output context is detected")
    void formatWordInOutputContextIsDetected() {
        PromptAnalysis analysis = analyzer.analyze(
                "Summarise the findings in JSON.", PromptProfile.DATA_ANALYSIS);

        assertThat(analysis.extractedFormat()).isEqualTo("JSON");
    }

    @Test
    @DisplayName("the examples given by the user are captured")
    void userExamplesAreCaptured() {
        PromptAnalysis analysis = analyzer.analyze(
                "Write the endpoint. For example: GET /orders?page=1 returns 20 orders.",
                PromptProfile.CODING);

        assertThat(analysis.extractedExamples()).isNotEmpty();
    }

    @Test
    @DisplayName("a request for an example is not mistaken for an example")
    void exampleRequestIsNotAnExample() {
        PromptAnalysis analysis = analyzer.analyze(
                "Write the endpoint. Include one example request and response.", PromptProfile.CODING);

        assertThat(analysis.extractedExamples()).isEmpty();
        assertThat(analysis.has(com.promptforge.core.model.Dimension.EXAMPLES)).isTrue();
    }

    @Test
    @DisplayName("a known stack earns partial context credit")
    void knownStackEarnsPartialContextCredit() {
        PromptAnalysis analysis = analyzer.analyze("spring boot pagination for orders", PromptProfile.CODING);

        assertThat(analysis.detectedStack()).contains("Spring Boot");
        assertThat(analysis.scoreOf(Dimension.CONTEXT)).isEqualTo(0.5);
        assertThat(analysis.diagnostics()).anyMatch(d -> d.code().equals("NO_TASK"));
    }
}
