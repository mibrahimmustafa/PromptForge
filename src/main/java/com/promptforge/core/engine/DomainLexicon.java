package com.promptforge.core.engine;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.promptforge.core.model.TaskType;

/**
 * Vocabulary used by the local engine: domain vocabulary, action verbs, vagueness markers
 * and conflicting-term pairs.
 *
 * <p>Everything here is intentionally static and data-only so the engine stays deterministic
 * and can be extended without touching the analysis logic.
 */
public final class DomainLexicon {

    /** Domain keywords mapped to a concrete expert role. */
    private static final Map<String, String> ROLES = new LinkedHashMap<>();

    /** Domain keywords mapped to a task type. */
    private static final Map<TaskType, List<String>> TASK_KEYWORDS = new LinkedHashMap<>();

    static {
        ROLES.put("java", "a senior Java engineer with deep experience in Spring Boot, JPA and design patterns");
        ROLES.put("spring", "a senior Java engineer specialising in Spring Boot and microservices");
        ROLES.put("kotlin", "a senior Kotlin engineer");
        ROLES.put("jakarta ee", "a senior Jakarta EE architect");
        ROLES.put("python", "a senior Python engineer focused on clean, testable code");
        ROLES.put("typescript", "a senior TypeScript engineer");
        ROLES.put("javascript", "a senior JavaScript engineer");
        ROLES.put("react", "a senior React engineer with strong front-end architecture skills");
        ROLES.put("angular", "a senior Angular engineer");
        ROLES.put("vue", "a senior Vue.js engineer");
        ROLES.put("sql", "a data analyst and SQL expert");
        ROLES.put("data", "a data analyst skilled in SQL, statistics and data modelling");
        ROLES.put("machine learning", "a machine-learning engineer");
        ROLES.put("ml", "a machine-learning engineer");
        ROLES.put("ai", "a senior AI engineer");
        ROLES.put("docker", "a DevOps and containerisation engineer");
        ROLES.put("kubernetes", "a platform engineer specialising in Kubernetes");
        ROLES.put("aws", "a cloud architect experienced with AWS");
        ROLES.put("azure", "a cloud architect experienced with Azure");
        ROLES.put("security", "a security engineer focused on defensive design");
        ROLES.put("database", "a database engineer focused on schema design and query performance");
        ROLES.put("frontend", "a senior front-end engineer with an eye for accessibility and performance");
        ROLES.put("backend", "a senior back-end engineer");
        ROLES.put("devops", "a DevOps engineer focused on delivery pipelines and observability");
        ROLES.put("architecture", "a software architect who weighs trade-offs explicitly");
        ROLES.put("testing", "a QA engineer who writes meaningful, reproducible tests");
        ROLES.put("technical writing", "a senior technical writer who writes for engineers");
        ROLES.put("documentation", "a technical writer who documents systems for maintainers");
        ROLES.put("marketing", "a senior growth marketer who thinks in experiments and funnel metrics");
        ROLES.put("copywriting", "a conversion-focused copywriter");
        ROLES.put("seo", "an SEO strategist");
        ROLES.put("finance", "a financial analyst who states assumptions explicitly");
        ROLES.put("legal", "a legal analyst who flags risk rather than giving legal advice");
        ROLES.put("education", "an instructional designer who writes for learners");
        ROLES.put("translation", "a professional translator who preserves tone and intent");
        ROLES.put("research", "a research analyst who cites sources and separates fact from inference");

        TASK_KEYWORDS.put(TaskType.CODE, List.of(
                "code", "function", "class", "method", "bug", "refactor", "endpoint", "controller", "repository",
                "entity", "dto", "unit test", "integration test", "compile", "stack trace", "exception",
                "java", "python", "javascript", "typescript", "sql", "regex", "algorithm", "script", "deploy"));
        TASK_KEYWORDS.put(TaskType.DATA, List.of(
                "dataset", "data analysis", "csv", "chart", "metric", "kpi", "statistics", "visualis", "visualiz",
                "trend", "aggregate", "query", "schema", "etl", "dashboard", "clean the data", "insight"));
        TASK_KEYWORDS.put(TaskType.WRITING, List.of(
                "blog", "email", "copy", "article", "newsletter", "readme", "documentation", "guide", "post",
                "landing page", "press release", "announcement", "tone", "narrative", "story"));
        TASK_KEYWORDS.put(TaskType.RESEARCH, List.of(
                "research", "investigate", "compare", "literature", "market analysis", "competitive",
                "sources", "summarise sources", "summarize sources", "find out", "landscape"));
        TASK_KEYWORDS.put(TaskType.PRODUCT, List.of(
                "roadmap", "feature", "requirement", "backlog", "user story", "prd", "priorit", "mvp",
                "acceptance criteria", "persona", "journey"));
        TASK_KEYWORDS.put(TaskType.BUSINESS, List.of(
                "strategy", "business plan", "proposal", "budget", "roi", "forecast", "pitch", "go-to-market",
                "pricing", "stakeholder", "kpi", "operating model"));
    }

    /** Verbs that introduce an actionable deliverable. */
    public static final Set<String> ACTION_VERBS = new LinkedHashSet<>(List.of(
            "write", "create", "build", "generate", "produce", "draft", "compose",
            "explain", "describe", "summarise", "summarize", "outline", "list",
            "translate", "rewrite", "rephrase", "edit", "proofread", "format",
            "refactor", "debug", "fix", "repair", "review", "audit", "optimise", "optimize",
            "implement", "code", "design", "architect", "convert", "migrate", "port",
            "analyse", "analyze", "compare", "contrast", "evaluate", "assess", "critique",
            "recommend", "suggest", "propose", "plan", "estimate", "calculate",
            "extract", "parse", "classify", "categorise", "categorize", "predict",
            "test", "verify", "validate", "benchmark", "profile", "measure",
            "identify", "find", "detect", "investigate", "trace", "map", "model",
            "brainstorm", "ideate", "prototype", "scaffold", "refine", "improve",
            "give", "provide", "show", "tell", "answer", "respond", "reply", "help",
            "what", "how", "why", "when", "where", "which", "who"));

    /** Unfalsifiable or unmeasurable words that weaken a prompt. */
    public static final Set<String> VAGUE_TERMS = new LinkedHashSet<>(List.of(
            "good", "great", "nice", "better", "best", "professional", "advanced", "modern",
            "appropriate", "suitable", "proper", "correct", "high-quality", "efficient", "optimal",
            "stuff", "things", "something", "various", "several", "many", "some", "few", "etc",
            "asap", "soon", "quickly", "fast", "robust", "scalable", "secure", "user-friendly",
            "seamless", "flexible", "comprehensive", "detailed", "simple", "clean", "elegant",
            "state-of-the-art", "cutting-edge", "best-in-class", "world-class"));

    /** Term pairs that cannot both be satisfied; the model will pick one arbitrarily. */
    public static final List<String[]> CONFLICTS = List.of(
            new String[]{"simple", "comprehensive"},
            new String[]{"simple", "exhaustive"},
            new String[]{"short", "detailed"},
            new String[]{"brief", "thorough"},
            new String[]{"concise", "elaborate"},
            new String[]{"quick", "exhaustive"},
            new String[]{"beginner", "expert-level"},
            new String[]{"maximalist", "minimalist"});

    private DomainLexicon() {
    }

    /**
     * Finds the most specific domain keyword present in the text and returns its expert role.
     *
     * @param text lower-cased prompt text
     * @return the role description, or {@code null} when no domain matches
     */
    public static String roleFor(String text) {
        String best = null;
        int bestLength = -1;
        for (Map.Entry<String, String> entry : ROLES.entrySet()) {
            if (text.contains(entry.getKey()) && entry.getKey().length() > bestLength) {
                best = entry.getValue();
                bestLength = entry.getKey().length();
            }
        }
        return best;
    }

    /**
     * Infers the type of work from keywords, falling back to a generic type.
     *
     * @param text lower-cased prompt text
     * @return the inferred task type
     */
    public static TaskType taskTypeFor(String text) {
        TaskType best = TaskType.GENERAL;
        int bestScore = 0;
        for (Map.Entry<TaskType, List<String>> entry : TASK_KEYWORDS.entrySet()) {
            int score = 0;
            for (String keyword : entry.getValue()) {
                if (text.contains(keyword)) {
                    score += keyword.contains(" ") ? 2 : 1;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = entry.getKey();
            }
        }
        return best;
    }

    /**
     * Collects the vague terms actually used in the prompt.
     *
     * @param text lower-cased prompt text
     * @return the vague terms found, in order of appearance
     */
    public static java.util.List<String> vagueTermsIn(String text) {
        java.util.List<String> found = new java.util.ArrayList<>();
        for (String term : VAGUE_TERMS) {
            if (containsWord(text, term)) {
                found.add(term);
            }
        }
        return found;
    }

    /**
     * Word-boundary aware contains check.
     *
     * @param text  haystack
     * @param word  needle
     * @return true when {@code word} appears as a whole word
     */
    public static boolean containsWord(String text, String word) {
        int index = text.indexOf(word);
        while (index >= 0) {
            boolean startOk = index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1));
            int end = index + word.length();
            boolean endOk = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (startOk && endOk) {
                return true;
            }
            index = text.indexOf(word, index + 1);
        }
        return false;
    }

    /**
     * Normalises text for keyword matching.
     *
     * @param text raw text
     * @return lower-cased text with collapsed whitespace
     */
    public static String normalise(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
