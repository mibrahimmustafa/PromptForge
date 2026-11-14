package com.promptforge.core.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.promptforge.core.model.Diagnostic;
import com.promptforge.core.model.Dimension;
import com.promptforge.core.model.DimensionScore;
import com.promptforge.core.model.PromptAnalysis;
import com.promptforge.core.model.PromptProfile;
import com.promptforge.core.model.Severity;
import com.promptforge.core.model.TaskType;

/**
 * Deterministic prompt analyzer.
 *
 * <p>Reads a rough prompt and reports, per dimension of the best-practice prompt anatomy,
 * how much usable material it contains. Produces a weighted score and an ordered list of
 * diagnostics with concrete, prompt-specific advice.
 */
public class PromptAnalyzer {

    private static final Pattern ROLE = Pattern.compile(
            "(?i)\\b(?:you are|you'?re|act as|acting as|act like|pretend (?:to be|you are)|"
                    + "assume (?:the role of|you are)|your role is|you will be|"
                    + "as an?\\s+[\\w-]+\\s+(?:engineer|developer|expert|analyst|writer|specialist|consultant|"
                    + "architect|manager|researcher|scientist|designer|devops|tester|marketer|strategist|lead|"
                    + "coach|teacher|student|professional|assistant))");

    private static final Pattern CONTEXT_MARKER = Pattern.compile(
            "(?i)\\b(?:audience|target(?:ing|ed)?\\b|background|in the context of|context of|because|since|"
                    + "given that|the project|our project|the codebase|this codebase|the company|our team|"
                    + "the customer|our customer|end user|user base|stakeholder|scenario|use case|workflow|"
                    + "domain|industry|market|budget|deadline|environment|repository|the team|for my team|"
                    + "at my company|we are building|we're building|i am building|i'm building)\\b");

    private static final Pattern PURPOSE_CLAUSE = Pattern.compile(
            "(?i)\\b(?:in order to|so that|so as to|to achieve|goal is|objective is|purpose is|"
                    + "the aim is|aims to|helps? (?:us|me|our team)|in order for)\\b");

    private static final Pattern CONSTRAINT = Pattern.compile(
            "(?i)\\b(?:must(?: not)?|should(?: not)?|shall|never|always|avoid(?:ing)?|without|do not|don'?t|"
                    + "no more than|at least|at most|only|except(?: for)?|exclud(?:e|ing)|unless|limit(?:ed)? to|"
                    + "cap(?:ped)?|maximum|minimum|keep|ensure|make sure|restricted to|non-negotiable|"
                    + "constraint(?:s)?|requirement(?:s)?|may not|refrain|prefer(?:red)?|"
                    + "focus(?:ed)? on|stick to|respect|respecting|follow(?:ing)?|stay within|"
                    + "not too|under \\d+|below \\d+|no longer than|maximum of|no more than|"
                    + "no (?:new|external|additional|other|third-?party|vendor|unnecessary))\\b");

    /**
     * A format word that only counts when it appears in an output context ("in JSON",
     * "as a Markdown table"). Without this a file name such as "sales csv" looks like a
     * requested output format.
     */
    private static final Pattern FORMAT_CONTEXT = Pattern.compile(
            "(?i)\\b(?:as|in|into|using|with)\\s+(?:an?\\s+|the\\s+)?"
                    + "(json|yaml|yml|xml|markdown|csv|tsv|html|sql|table|bullet points?|"
                    + "numbered list|code block|prose|outline|schema|mermaid|step-by-step steps?|"
                    + "test plan|checklist|diff)\\b");

    /**
     * A format word next to an explicit output verb ("the output should be JSON").
     */
    private static final Pattern FORMAT_NAMED = Pattern.compile(
            "(?i)\\b(?:output|format|respond|reply|answer|return|produce|structure|written|writing)"
                    + "\\b[^.;\\n]{0,40}?\\b(json|yaml|yml|xml|markdown|table|bullet points?|"
                    + "numbered list|code block|prose|outline|schema|mermaid|step-by-step steps?|"
                    + "test plan|checklist|diff)\\b");

    private static final Pattern EXAMPLES = Pattern.compile(
            "(?i)(?:\\bexamples?\\b|\\be\\.?g\\.?\\b|\\bfor instance\\b|\\bsample\\b|\\bdemo\\b|"
                    + "\\blike this\\b|\\bsuch as\\b|\\breference (?:input|output)\\b|\\bsample (?:input|output)\\b|"
                    + "\\binput:\\s|\\boutput:\\s)");

    private static final Pattern ACCEPTANCE = Pattern.compile(
            "(?i)\\b(?:acceptance criteria|success criteria|definition of done|quality bar|"
                    + "must (?:pass|return|include|contain|produce|not contain)|"
                    + "should (?:pass|return|include|contain|produce)|"
                    + "verify that|validate that|non-?functional|"
                    + "requirements?|unit tests?|integration tests?|test coverage|"
                    + "ensure that|guarantee|only if|given that the output)\\b");

    private static final Pattern AUDIENCE_CAPTURE = Pattern.compile(
            "(?i)\\b(?:the\\s+)?(?:target|intended|primary|secondary)?\\s*audience(?:\\s+(?:is|are|:))?\\s*"
                    + "([^.;\\n]{3,90})");

    private static final Pattern AUDIENCE_ALT = Pattern.compile(
            "(?i)\\b(?:written for|tailored for|aimed at|meant for|targeting|targeted at|for)\\s+"
                    + "((?:(?:non-?technical|technical|beginning|senior|junior|internal|external|our|my|new|"
                    + "paying|potential)\\s+){0,3}"
                    + "(?:users?|developers?|engineers?|programmers?|managers?|leaders?|stakeholders?|"
                    + "customers?|clients?|beginners?|students?|readers?|audience|people|team|members?|"
                    + "companies|teams)\\b[^.;\\n]{0,60})");

    private static final Pattern GOAL_CAPTURE = Pattern.compile(
            "(?i)\\b(?:in order to|so that|so as to|to achieve|our goal is|the goal is|the objective is|"
                    + "the purpose is|the aim is|aims to|helps? (?:us|me|our team)|in order for)\\s+"
                    + "([^.;\\n]{5,140})");

    private static final Pattern FORMAT_CAPTURE = Pattern.compile(
            "(?i)\\b(?:output|respond|reply|answer|return|produce|give me|show)\\s+(?:it|this|the\\s+\\w+)?\\s*"
                    + "(?:as|in|using|with|into)?\\s*((?:a|an|the)?\\s*[^.;\\n]{3,140})");

    private static final Pattern GREETING = Pattern.compile("^(?:hey|hi|hello|yo|dear)\\b[\\s,!.]*",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern POLITE_FRAME = Pattern.compile(
            "^(?:please\\s+)?(?:can|could|would|will)\\s+you\\s+(?:please\\s+)?", Pattern.CASE_INSENSITIVE);

    private static final Pattern I_WANT_FRAME = Pattern.compile(
            "^i\\s+(?:want|need|would like|am trying to|have to|have|require)\\s+"
                    + "(?:to\\s+|you\\s+(?:to\\s+)?|that\\s+you\\s+|us\\s+to\\s+)?", Pattern.CASE_INSENSITIVE);

    private static final Pattern HELP_ME_FRAME = Pattern.compile("^(?:help me|assist me|do this)\\s+",
            Pattern.CASE_INSENSITIVE);

    /** A leftover second person pronoun after the frame was removed. */
    private static final Pattern LEFTOVER_YOU = Pattern.compile("^you\\s+(?:to\\s+)?", Pattern.CASE_INSENSITIVE);

    private static final Pattern CONNECTOR = Pattern.compile("(?i)\\s+(?:and|but|also)\\s+");

    /** A sentence that asks for an example instead of being one. */
    private static final Pattern EXAMPLE_REQUEST = Pattern.compile(
            "(?i)^(?:please\\s+)?(?:include|provide|show|add|give|attach|supply)\\b[^.]*\\bexample");

    private static final Pattern CHAINING = Pattern.compile(
            "(?i)\\b(?:and also|and then|after that|as well as|plus,|also,)\\b");

    /** Technology tokens matched in the prompt, mapped to their display name. */
    private static final Map<String, String> STACK_TOKENS = new LinkedHashMap<>();

    static {
        STACK_TOKENS.put("java", "Java");
        STACK_TOKENS.put("spring boot", "Spring Boot");
        STACK_TOKENS.put("spring", "Spring");
        STACK_TOKENS.put("jpa", "JPA");
        STACK_TOKENS.put("hibernate", "Hibernate");
        STACK_TOKENS.put("maven", "Maven");
        STACK_TOKENS.put("gradle", "Gradle");
        STACK_TOKENS.put("jakarta", "Jakarta EE");
        STACK_TOKENS.put("javascript", "JavaScript");
        STACK_TOKENS.put("typescript", "TypeScript");
        STACK_TOKENS.put("react", "React");
        STACK_TOKENS.put("angular", "Angular");
        STACK_TOKENS.put("vue", "Vue.js");
        STACK_TOKENS.put("node", "Node.js");
        STACK_TOKENS.put("node.js", "Node.js");
        STACK_TOKENS.put("python", "Python");
        STACK_TOKENS.put("django", "Django");
        STACK_TOKENS.put("flask", "Flask");
        STACK_TOKENS.put("sql", "SQL");
        STACK_TOKENS.put("postgresql", "PostgreSQL");
        STACK_TOKENS.put("mysql", "MySQL");
        STACK_TOKENS.put("oracle", "Oracle");
        STACK_TOKENS.put("mongodb", "MongoDB");
        STACK_TOKENS.put("redis", "Redis");
        STACK_TOKENS.put("kafka", "Kafka");
        STACK_TOKENS.put("docker", "Docker");
        STACK_TOKENS.put("kubernetes", "Kubernetes");
        STACK_TOKENS.put("k8s", "Kubernetes");
        STACK_TOKENS.put("aws", "AWS");
        STACK_TOKENS.put("azure", "Azure");
        STACK_TOKENS.put("gcp", "GCP");
        STACK_TOKENS.put("terraform", "Terraform");
        STACK_TOKENS.put("jenkins", "Jenkins");
        STACK_TOKENS.put("gitlab", "GitLab");
        STACK_TOKENS.put("git", "Git");
        STACK_TOKENS.put("rest", "REST");
        STACK_TOKENS.put("graphql", "GraphQL");
        STACK_TOKENS.put("junit", "JUnit");
        STACK_TOKENS.put("testng", "TestNG");
        STACK_TOKENS.put("jmeter", "JMeter");
        STACK_TOKENS.put("selenium", "Selenium");
        STACK_TOKENS.put("linux", "Linux");
        STACK_TOKENS.put("nginx", "Nginx");
        STACK_TOKENS.put("microservice", "microservices");
        STACK_TOKENS.put("microservices", "microservices");
    }

    /**
     * Analyses the prompt.
     *
     * @param rawPrompt the user's prompt
     * @param profile   the selected domain profile
     * @return the analysis report
     */
    public PromptAnalysis analyze(String rawPrompt, PromptProfile profile) {
        String raw = rawPrompt == null ? "" : rawPrompt.trim();
        String flat = raw.replaceAll("\\s+", " ").trim();
        String lower = DomainLexicon.normalise(flat);
        List<String> sentences = TextUtil.sentences(raw);

        String stripped = stripFrames(flat);
        boolean framed = !stripped.equalsIgnoreCase(flat);

        String role = findRole(sentences);
        String taskStatement = findTask(stripped, sentences);
        boolean hasTask = taskStatement != null && !taskStatement.isBlank();
        boolean hasRole = role != null;
        String audience = findAudience(sentences);
        String stack = findStack(lower);
        String goal = findGoal(sentences);
        boolean hasContext = hasContext(lower, sentences, stack, goal, audience);
        List<String> constraints = findConstraints(sentences);
        String format = findFormat(sentences, lower);
        boolean hasFormat = format != null && !format.isBlank();
        boolean hasExamples = EXAMPLES.matcher(flat).find();
        List<String> examples = findExamples(sentences);
        boolean hasAcceptance = ACCEPTANCE.matcher(flat).find();

        TaskType taskType = resolveTaskType(lower, profile);

        List<Diagnostic> diagnostics = new ArrayList<>();
        List<DimensionScore> dimensions = new ArrayList<>();

        scoreRole(dimensions, diagnostics, hasRole, role, lower);
        scoreTask(dimensions, diagnostics, hasTask, framed, stripped);
        scoreContext(dimensions, diagnostics, hasContext, audience, stack, goal);
        scoreConstraints(dimensions, diagnostics, constraints);
        scoreFormat(dimensions, diagnostics, hasFormat, format);
        scoreExamples(dimensions, diagnostics, hasExamples);
        scoreAcceptance(dimensions, diagnostics, hasAcceptance);

        checkLength(raw, flat, diagnostics);
        checkVagueness(flat, diagnostics);
        checkConflicts(lower, diagnostics);
        checkChaining(flat, diagnostics);
        checkMultiTask(lower, diagnostics);

        int score = computeScore(dimensions);
        String grade = grade(score);

        diagnostics.sort(Comparator
                .comparingInt((Diagnostic d) -> d.severity().rank())
                .thenComparing(Diagnostic::code));

        return new PromptAnalysis(
                raw,
                TextUtil.wordCount(raw),
                score,
                grade,
                dimensions,
                diagnostics,
                role,
                audience,
                stack,
                goal,
                constraints,
                format,
                examples,
                taskType,
                taskStatement);
    }

    // ------------------------------------------------------------------ detection

    private String stripFrames(String flat) {
        String result = flat;
        result = GREETING.matcher(result).replaceFirst("");
        result = POLITE_FRAME.matcher(result).replaceFirst("");
        result = I_WANT_FRAME.matcher(result).replaceFirst("");
        result = HELP_ME_FRAME.matcher(result).replaceFirst("");
        result = LEFTOVER_YOU.matcher(result).replaceFirst("");
        return result.trim();
    }

    private String findRole(List<String> sentences) {
        for (String sentence : sentences) {
            Matcher matcher = ROLE.matcher(sentence);
            if (matcher.find()) {
                String candidate = sentence.substring(matcher.start());
                int comma = candidate.indexOf(',');
                if (comma > 0) {
                    String head = candidate.substring(0, comma);
                    // Keep the persona phrase; fall back to the full clause when it is too short.
                    candidate = head.length() >= 12 ? head : candidate;
                }
                return TextUtil.stripTrailingPunctuation(candidate.trim());
            }
        }
        return null;
    }

    private String findTask(String stripped, List<String> sentences) {
        if (stripped.isBlank()) {
            return null;
        }
        String firstWord = stripped.split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (DomainLexicon.ACTION_VERBS.contains(firstWord)) {
            return TextUtil.asSentence(stripped);
        }
        // A question is a task as well.
        for (String sentence : sentences) {
            if (sentence.endsWith("?")) {
                return TextUtil.asSentence(sentence);
            }
        }
        // Otherwise the task is the sentence that carries the action.
        for (String sentence : sentences) {
            if (containsActionVerb(sentence)) {
                return TextUtil.asSentence(sentence);
            }
        }
        return null;
    }

    private boolean containsActionVerb(String sentence) {
        for (String word : sentence.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            if (DomainLexicon.ACTION_VERBS.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasContext(String lower, List<String> sentences, String stack, String goal,
                               String audience) {
        if (audience != null) {
            return true;
        }
        if (CONTEXT_MARKER.matcher(lower).find()) {
            return true;
        }
        if (goal != null) {
            return true;
        }
        return stack != null && PURPOSE_CLAUSE.matcher(lower).find();
    }

    private String findAudience(List<String> sentences) {
        for (String sentence : sentences) {
            Matcher matcher = AUDIENCE_CAPTURE.matcher(sentence);
            if (matcher.find()) {
                String value = TextUtil.stripTrailingPunctuation(matcher.group(1).trim());
                if (value.length() > 2) {
                    return value;
                }
            }
        }
        for (String sentence : sentences) {
            Matcher matcher = AUDIENCE_ALT.matcher(sentence);
            if (matcher.find()) {
                String value = TextUtil.stripTrailingPunctuation(matcher.group(1).trim());
                if (value.length() > 2) {
                    return value;
                }
            }
        }
        return null;
    }

    private String findStack(String lower) {
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, String> entry : STACK_TOKENS.entrySet()) {
            if (!DomainLexicon.containsWord(lower, entry.getKey())) {
                continue;
            }
            String display = entry.getValue();
            // Skip a generic token when a more specific one already covers it ("Spring" vs "Spring Boot").
            boolean covered = found.stream().anyMatch(existing ->
                    existing.toLowerCase(Locale.ROOT).contains(display.toLowerCase(Locale.ROOT)));
            if (!covered) {
                found.removeIf(existing -> display.toLowerCase(Locale.ROOT)
                        .contains(existing.toLowerCase(Locale.ROOT)));
                found.add(display);
            }
        }
        return found.isEmpty() ? null : String.join(", ", found);
    }

    private String findGoal(List<String> sentences) {
        for (String sentence : sentences) {
            Matcher matcher = GOAL_CAPTURE.matcher(sentence);
            if (matcher.find()) {
                String value = TextUtil.stripTrailingPunctuation(matcher.group(1).trim());
                if (value.length() > 4) {
                    return value;
                }
            }
        }
        return null;
    }

    /**
     * Extracts the constraint clauses. Only the clause that carries the limit is kept - a
     * whole sentence would drag the rest of the request into the constraints section.
     *
     * @param sentences the prompt sentences
     * @return the constraint clauses, in order of appearance
     */
    private List<String> findConstraints(List<String> sentences) {
        List<String> constraints = new ArrayList<>();
        for (String sentence : sentences) {
            for (String clause : splitClauses(sentence)) {
                String cleaned = TextUtil.stripTrailingPunctuation(clause);
                if (cleaned.length() > 4 && CONSTRAINT.matcher(cleaned).find()
                        && !constraints.contains(cleaned)) {
                    constraints.add(cleaned);
                }
            }
        }
        return constraints;
    }

    /**
     * Splits a sentence into clauses.
     *
     * <p>Commas and semicolons always break a clause. A conjunction only breaks it when
     * <em>both</em> sides carry a constraint - otherwise the conjunction continues the noun
     * phrase ("Use only Spring Boot and JPA" must not lose "and JPA").
     *
     * @param sentence the sentence to split
     * @return the clauses
     */
    private List<String> splitClauses(String sentence) {
        String cleaned = TextUtil.stripBullet(sentence);
        List<String> clauses = new ArrayList<>();

        for (String segment : cleaned.split("[,;]")) {
            StringBuilder current = new StringBuilder();
            String pendingConnector = "";
            // A matcher is used instead of String.split because the connector itself has to
            // survive the split - it is part of the clause.
            List<String> parts = splitOnConnectors(segment);
            for (int i = 0; i < parts.size(); i++) {
                String token = parts.get(i).trim();
                if (token.isEmpty()) {
                    continue;
                }
                if (i % 2 == 1) {
                    pendingConnector = " " + token + " ";
                    continue;
                }
                boolean currentHasConstraint = current.length() > 0
                        && CONSTRAINT.matcher(current.toString()).find();
                boolean partHasConstraint = CONSTRAINT.matcher(token).find();
                if (currentHasConstraint && partHasConstraint) {
                    clauses.add(current.toString().trim());
                    current = new StringBuilder(token);
                } else if (current.length() == 0) {
                    // A leading conjunction belongs to the previous clause, not to this one.
                    current.append(token);
                } else {
                    current.append(pendingConnector).append(token);
                }
                pendingConnector = "";
            }
            if (current.length() > 0) {
                clauses.add(current.toString().trim());
            }
        }
        return clauses;
    }

    /**
     * Finds the requested output format.
     *
     * <p>The most specific signal wins: an explicit phrase ("respond with a Markdown table
     * containing ..."), then a format word in an output context ("in JSON"), then a format word
     * next to an output verb. A format word on its own is not enough - "our sales csv" names
     * an input file, not an output shape.
     *
     * @param sentences the prompt sentences
     * @param lower    the normalised prompt
     * @return the requested format, or {@code null} when none was requested
     */
    private String findFormat(List<String> sentences, String lower) {
        for (String sentence : sentences) {
            Matcher capture = FORMAT_CAPTURE.matcher(sentence);
            if (capture.find()) {
                String value = TextUtil.stripTrailingPunctuation(capture.group(1).trim());
                if (value.length() > 2) {
                    return value;
                }
            }
        }
        Matcher context = FORMAT_CONTEXT.matcher(lower);
        if (context.find()) {
            return canonicalFormat(context.group(1));
        }
        Matcher named = FORMAT_NAMED.matcher(lower);
        if (named.find()) {
            return canonicalFormat(named.group(1));
        }
        return null;
    }

    /**
     * Maps a format word to the way it should read inside the prompt.
     *
     * @param word the detected format word
     * @return a readable format description
     */
    private String canonicalFormat(String word) {
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "json" -> "JSON";
            case "yaml", "yml" -> "YAML";
            case "xml" -> "XML";
            case "markdown" -> "Markdown";
            case "csv" -> "CSV";
            case "tsv" -> "TSV";
            case "html" -> "HTML";
            case "sql" -> "SQL";
            case "table" -> "a Markdown table";
            case "bullet points", "bullet point" -> "bullet points";
            case "numbered list" -> "a numbered list";
            case "code block" -> "a fenced code block";
            case "prose" -> "prose paragraphs";
            case "outline" -> "an outline";
            case "schema" -> "a schema";
            case "mermaid" -> "a Mermaid diagram";
            case "step-by-step steps", "step-by-step step" -> "numbered step-by-step steps";
            case "test plan" -> "a test plan with test cases";
            case "checklist" -> "a checklist";
            case "diff" -> "a diff";
            default -> word;
        };
    }

    /**
     * The chosen profile is a deliberate user hint, so it wins over keyword detection.
     * Keyword detection only decides for the generic profile.
     *
     * @param lower   the normalised prompt
     * @param profile the selected profile
     * @return the task type to use
     */
    /**
     * Collects the example lines so the rewriter can keep them instead of inventing new ones.
     *
     * <p>A sentence that merely <em>asks</em> for an example is not an example - it is an
     * instruction, and the rewriter covers it with its own wording.
     *
     * @param sentences the prompt sentences
     * @return the example lines
     */
    private List<String> findExamples(List<String> sentences) {
        List<String> examples = new ArrayList<>();
        for (String sentence : sentences) {
            String cleaned = TextUtil.stripBullet(sentence);
            if (EXAMPLES.matcher(cleaned).find() && cleaned.length() > 8
                    && !EXAMPLE_REQUEST.matcher(cleaned).find()) {
                examples.add(TextUtil.stripTrailingPunctuation(cleaned));
            }
        }
        return examples;
    }

    /**
     * Splits a segment on conjunctions while keeping the connectors.
     *
     * <p>The result alternates: text, connector, text, connector, ...
     *
     * @param segment the segment to split
     * @return the alternating parts
     */
    private List<String> splitOnConnectors(String segment) {
        List<String> parts = new ArrayList<>();
        Matcher matcher = CONNECTOR.matcher(segment);
        int last = 0;
        while (matcher.find()) {
            parts.add(segment.substring(last, matcher.start()));
            parts.add(matcher.group());
            last = matcher.end();
        }
        parts.add(segment.substring(last));
        return parts;
    }

    private TaskType resolveTaskType(String lower, PromptProfile profile) {
        if (profile != PromptProfile.GENERALIST) {
            return profile.taskType();
        }
        return DomainLexicon.taskTypeFor(lower);
    }

    // ------------------------------------------------------------------ scoring

    private void scoreRole(List<DimensionScore> dimensions, List<Diagnostic> diagnostics,
                           boolean hasRole, String role, String lower) {
        String inferred = DomainLexicon.roleFor(lower);
        if (hasRole) {
            dimensions.add(new DimensionScore(Dimension.ROLE, 1.0, true, "Role framing found: \"" + role + "\""));
        } else {
            dimensions.add(new DimensionScore(Dimension.ROLE, 0.0, false,
                    inferred != null ? "No role stated, but the domain suggests " + inferred
                            : "No role stated"));
            diagnostics.add(new Diagnostic(
                    "NO_ROLE",
                    "No role or persona defined",
                    "The model has to guess the perspective. A stated role (seniority, specialisation) "
                            + "consistently raises answer quality.",
                    Severity.WARNING,
                    Dimension.ROLE,
                    inferred != null
                            ? "Add: \"You are " + inferred + "\"."
                            : "Add a \"Role\" line naming the expert persona and seniority.",
                    inferred != null));
        }
    }

    private void scoreTask(List<DimensionScore> dimensions, List<Diagnostic> diagnostics,
                           boolean hasTask, boolean framed, String stripped) {
        if (hasTask) {
            dimensions.add(new DimensionScore(Dimension.TASK, 1.0, true,
                    framed ? "Action found, but buried behind a polite request wrapper" : "Clear action found"));
            if (framed) {
                diagnostics.add(new Diagnostic(
                        "POLITE_FRAME",
                        "Request is wrapped in politeness",
                        "\"Can you...\", \"I want you to...\" and similar frames add tokens and dilute the "
                                + "instruction without adding meaning.",
                        Severity.INFO,
                        Dimension.TASK,
                        "Start with the imperative verb instead: \"" + TextUtil.stripTrailingPunctuation(stripped) + " ...\".",
                        true));
            }
        } else {
            dimensions.add(new DimensionScore(Dimension.TASK, 0.0, false,
                    "No actionable verb or question detected"));
            diagnostics.add(new Diagnostic(
                    "NO_TASK",
                    "No actionable task",
                    "Nothing states what to produce. Without a concrete deliverable the answer is guesswork.",
                    Severity.CRITICAL,
                    Dimension.TASK,
                    "Start with an action verb such as Write / Build / Analyse / Review / Compare and name the deliverable.",
                    false));
        }
    }

    private void scoreContext(List<DimensionScore> dimensions, List<Diagnostic> diagnostics,
                              boolean hasContext, String audience, String stack, String goal) {
        if (hasContext) {
            StringBuilder note = new StringBuilder("Context signals found");
            if (audience != null) {
                note.append("; audience: ").append(audience);
            }
            if (stack != null) {
                note.append("; stack: ").append(stack);
            }
            if (goal != null) {
                note.append("; goal: ").append(goal);
            }
            dimensions.add(new DimensionScore(Dimension.CONTEXT, 1.0, true, note.toString()));
        } else {
            dimensions.add(new DimensionScore(Dimension.CONTEXT, 0.0, false,
                    "No background, audience or goal supplied"));
            diagnostics.add(new Diagnostic(
                    "NO_CONTEXT",
                    "No context, audience or goal",
                    "The model has to assume the background. Most weak answers come from missing context, "
                            + "not from a weak model.",
                    Severity.WARNING,
                    Dimension.CONTEXT,
                    "Add who the output is for, the surrounding situation, and what success is used for.",
                    false));
        }
    }

    private void scoreConstraints(List<DimensionScore> dimensions, List<Diagnostic> diagnostics,
                                  List<String> constraints) {
        if (!constraints.isEmpty()) {
            dimensions.add(new DimensionScore(Dimension.CONSTRAINTS, 1.0, true,
                    constraints.size() + " constraint" + (constraints.size() == 1 ? "" : "s") + " detected"));
        } else {
            dimensions.add(new DimensionScore(Dimension.CONSTRAINTS, 0.0, false,
                    "No explicit limits or exclusions"));
            diagnostics.add(new Diagnostic(
                    "NO_CONSTRAINTS",
                    "No constraints or exclusions",
                    "Nothing tells the model what to avoid, so it may pick a reasonable but wrong approach "
                            + "(wrong length, wrong library, wrong tone).",
                    Severity.WARNING,
                    Dimension.CONSTRAINTS,
                    "State the hard limits: length, technologies to use or avoid, tone, and anything that "
                            + "would make the answer wrong.",
                    false));
        }
    }

    private void scoreFormat(List<DimensionScore> dimensions, List<Diagnostic> diagnostics,
                              boolean hasFormat, String format) {
        if (hasFormat) {
            dimensions.add(new DimensionScore(Dimension.OUTPUT_FORMAT, 1.0, true,
                    "Output format detected: " + format));
        } else {
            dimensions.add(new DimensionScore(Dimension.OUTPUT_FORMAT, 0.0, false,
                    "No required output shape"));
            diagnostics.add(new Diagnostic(
                    "NO_OUTPUT_FORMAT",
                    "No output format specified",
                    "The single highest-leverage omission. Without a format the answer cannot be reused "
                            + "programmatically or checked.",
                    Severity.CRITICAL,
                    Dimension.OUTPUT_FORMAT,
                    "Declare the output shape, e.g. \"Return a Markdown table with columns: X, Y, Z\" or "
                            + "\"Respond with JSON matching this schema\".",
                    false));
        }
    }

    private void scoreExamples(List<DimensionScore> dimensions, List<Diagnostic> diagnostics,
                               boolean hasExamples) {
        if (hasExamples) {
            dimensions.add(new DimensionScore(Dimension.EXAMPLES, 1.0, true, "Example(s) present"));
        } else {
            dimensions.add(new DimensionScore(Dimension.EXAMPLES, 0.0, false, "No example provided"));
            diagnostics.add(new Diagnostic(
                    "NO_EXAMPLES",
                    "No example of a good answer",
                    "One concrete example communicates format, tone and depth far faster than prose "
                            + "describing them.",
                    Severity.INFO,
                    Dimension.EXAMPLES,
                    "Add one representative sample of the output you want.",
                    false));
        }
    }

    private void scoreAcceptance(List<DimensionScore> dimensions, List<Diagnostic> diagnostics,
                                 boolean hasAcceptance) {
        if (hasAcceptance) {
            dimensions.add(new DimensionScore(Dimension.ACCEPTANCE, 1.0, true, "Acceptance criteria present"));
        } else {
            dimensions.add(new DimensionScore(Dimension.ACCEPTANCE, 0.0, false,
                    "No definition of done"));
            diagnostics.add(new Diagnostic(
                    "NO_ACCEPTANCE",
                    "No acceptance criteria",
                    "Without a definition of done neither side can tell whether the answer succeeded.",
                    Severity.WARNING,
                    Dimension.ACCEPTANCE,
                    "Add checkable conditions, e.g. \"must compile\", \"must cite every claim\", "
                            + "\"must stay under 300 words\".",
                    false));
        }
    }

    // ------------------------------------------------------------------ extra checks

    private void checkLength(String raw, String flat, List<Diagnostic> diagnostics) {
        int words = TextUtil.wordCount(raw);
        if (words == 0) {
            diagnostics.add(new Diagnostic(
                    "EMPTY_PROMPT", "Prompt is empty",
                    "There is nothing to reshape.",
                    Severity.CRITICAL, null, "Describe what you want the AI to produce.", false));
            return;
        }
        if (words < 12) {
            diagnostics.add(new Diagnostic(
                    "TOO_SHORT", "Prompt is too short to be specific",
                    words + " words leave too much for the model to assume.",
                    Severity.CRITICAL, null,
                    "Add the deliverable, the audience, the format and one constraint.", false));
        }
        if (words > 400) {
            diagnostics.add(new Diagnostic(
                    "TOO_LONG", "Prompt is very long",
                    words + " words in a single block makes the instruction easy to lose.",
                    Severity.INFO, null,
                    "Split into labelled sections (Role, Task, Context, Constraints, Format) so each "
                            + "requirement is unambiguous.", true));
        } else if (!raw.contains("\n") && words > 30) {
            diagnostics.add(new Diagnostic(
                    "WALL_OF_TEXT", "Prompt is a single dense block",
                    "Long single blocks hide which words are requirements and which are context.",
                    Severity.INFO, null,
                    "Break it into labelled sections or bullets.", true));
        }
    }

    private void checkVagueness(String flat, List<Diagnostic> diagnostics) {
        List<String> vague = DomainLexicon.vagueTermsIn(flat);
        if (vague.isEmpty()) {
            return;
        }
        String sample = vague.size() > 4 ? String.join(", ", vague.subList(0, 4)) + ", ..."
                : String.join(", ", vague);
        diagnostics.add(new Diagnostic(
                "VAGUE_TERMS", "Unfalsifiable wording detected",
                "These words cannot be verified or disagreed with: " + sample + ".",
                Severity.WARNING, null,
                "Replace each with a measurable criterion (a number, a limit, a named standard, a format).",
                false));
    }

    private void checkConflicts(String lower, List<Diagnostic> diagnostics) {
        for (String[] pair : DomainLexicon.CONFLICTS) {
            if (DomainLexicon.containsWord(lower, pair[0]) && DomainLexicon.containsWord(lower, pair[1])) {
                diagnostics.add(new Diagnostic(
                        "CONFLICTING_SCOPES", "Conflicting scope: " + pair[0] + " and " + pair[1],
                        "\"" + pair[0] + "\" and \"" + pair[1] + "\" pull the answer in opposite directions, "
                                + "so the model will choose arbitrarily.",
                        Severity.WARNING, null,
                        "Resolve the conflict explicitly, e.g. \"concise, but cover every item in a table\".",
                        false));
                return;
            }
        }
    }

    private void checkChaining(String flat, List<Diagnostic> diagnostics) {
        int chains = 0;
        Matcher matcher = CHAINING.matcher(flat);
        while (matcher.find()) {
            chains++;
        }
        if (chains >= 2) {
            diagnostics.add(new Diagnostic(
                    "CHAINED_REQUESTS", "Several requests chained together",
                    chains + " chained connectors suggest multiple deliverables in one prompt. Attention is "
                            + "split and later requirements get less care.",
                    Severity.INFO, null,
                    "Run one request per prompt, or label each request as its own numbered section.", false));
        }
    }

    private void checkMultiTask(String lower, List<Diagnostic> diagnostics) {
        int verbs = 0;
        for (String verb : DomainLexicon.ACTION_VERBS) {
            if (DomainLexicon.containsWord(lower, verb)) {
                verbs++;
            }
        }
        if (verbs >= 6) {
            diagnostics.add(new Diagnostic(
                    "MULTIPLE_DELIVERABLES", "Many different actions in one prompt",
                    "Detected " + verbs + " different action verbs. Each one needs its own format and its "
                            + "own success check.",
                    Severity.WARNING, null,
                    "Split into numbered sub-tasks, each with its own deliverable and format.", false));
        }
    }

    private int computeScore(List<DimensionScore> dimensions) {
        double total = 0;
        for (DimensionScore dimension : dimensions) {
            total += dimension.score() * dimension.dimension().weight();
        }
        return (int) Math.round(total * 100);
    }

    private String grade(int score) {
        if (score >= 85) {
            return "Excellent";
        }
        if (score >= 70) {
            return "Good";
        }
        if (score >= 50) {
            return "Fair";
        }
        if (score >= 30) {
            return "Weak";
        }
        return "Poor";
    }

    /**
     * Exposes the dimension anatomy to the rest of the application.
     *
     * @return every dimension in display order
     */
    public static List<Dimension> dimensions() {
        return List.of(Dimension.values());
    }

    /**
     * Convenience for callers that only need the optional extracted format.
     *
     * @param analysis an analysis report
     * @return the extracted output format, if any
     */
    public static Optional<String> formatOf(PromptAnalysis analysis) {
        return Optional.ofNullable(analysis.extractedFormat());
    }
}
