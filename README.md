# PromptForge

Reshape any rough prompt into a **best-practice prompt** — with a score, the gaps explained, a
ready-to-paste rewrite, a fill-in-the-blank template, and the clarifying questions you still need
to answer.

Java · Spring Boot 4 (Jakarta EE) · local-first engine, optional LLM upgrade.

---

## Why this exists

Most prompt failures are not model failures. They are *specification* failures:

- no role, so the model guesses the perspective;
- no task, so it guesses the deliverable;
- no output format, so the answer cannot be reused or checked;
- no constraints, so it picks a plausible-but-wrong approach;
- no definition of done, so nobody can tell whether the answer succeeded.

PromptForge measures exactly these gaps, then rebuilds the prompt around them.

## The prompt anatomy it scores against

| Dimension      | Weight | What good looks like                                                    |
|----------------|--------|--------------------------------------------------------------------------|
| Task           | 20%    | One explicit action and one named deliverable                            |
| Context        | 16%    | Audience, background, stack and the goal behind the request              |
| Output format  | 16%    | A concrete output shape that can be checked mechanically                  |
| Constraints    | 16%    | Hard limits and explicit exclusions                                      |
| Acceptance     | 14%    | Checkable conditions for success                                         |
| Role           | 10%    | Expertise and seniority stated up front                                  |
| Examples       | 8%     | One representative sample of a good answer                               |

The weighted total is the **prompt quality score** (0–100).

## What you get back

1. **Score & grade** – the weighted score plus a per-dimension breakdown.
2. **Findings** – every gap, ranked `Critical / Warning / Info`, each with a prompt-specific
   fix (e.g. *conflicting scopes: "simple" and "comprehensive"*).
3. **Reshaped prompt** – a complete, usable prompt with the gaps filled in.
4. **Template** – the same skeleton with explicit `<placeholders>` where information is still
   missing, so nothing is silently invented.
5. **Clarifying questions** – the short list of answers that would close the remaining gaps.

## Quick start

```bash
# Windows
mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

Then open <http://localhost:8080>.

Build and run the jar:

```bash
mvnw clean package
java -jar target/prompt-forge-1.0.0.jar
```

Requirements: JDK 17+ (tested on JDK 22). Maven is not needed — the Maven Wrapper is included.

## HTTP API

### `POST /api/reshape`

```jsonc
{
  "prompt": "can you write me a good post about our new api, it should be professional and not too long, for developers",
  "profile": "WRITING",       // GENERALIST | CODING | DATA_ANALYSIS | WRITING | RESEARCH | PRODUCT | BUSINESS
  "engine": "RULE",           // RULE | LLM
  "includeExamples": true,
  "strict": false,            // template mode: keep placeholders for missing sections
  "provider": null,           // anthropic | openai | gemini (LLM engine only)
  "model": null               // optional model id override
}
```

Response:

```jsonc
{
  "original": "...",
  "reshapedPrompt": "## Role\nYou are a senior writer ...",
  "templatePrompt": "## Role\nYou are <expert persona and seniority> ...",
  "questions": ["Who should the AI be? ..."],
  "analysis": {
    "score": 52,
    "grade": "Fair",
    "wordCount": 21,
    "taskType": "WRITING",
    "detectedAudience": "developers",
    "dimensions": [{ "dimension": { "label": "Task", "weight": 0.2 }, "score": 1.0, "present": true }],
    "diagnostics": [{ "code": "NO_OUTPUT_FORMAT", "severity": "CRITICAL", "title": "No output format specified" }]
  },
  "engineUsed": "Rule-based",
  "notes": []
}
```

### `GET /api/meta`

Profiles, the dimension anatomy, and which LLM providers are configured.

### `GET /api/health`

Liveness probe.

## The two engines

### 1. Local rule-based engine (default)

Pure Java, deterministic, offline, no API key, no cost, no prompt data leaving the machine.
It is a pattern-matching analyzer (`PromptAnalyzer`) plus a template-driven rewriter
(`PromptRewriter`):

- strips politeness frames (`can you…`, `I want you to…`);
- detects role, audience, stack, goal, constraints and output format;
- flags vagueness (`professional`, `good`, `etc.`), conflicting scopes (`simple` + `comprehensive`)
  and chained or multi-deliverable requests;
- infers the task type and injects the matching defaults for role, format and acceptance criteria.

### 2. LLM engine (optional)

Set one key and switch the engine to `LLM`. The analyzer still runs first — the findings, the
detected task type and the draft template are handed to the model, so the rewrite is *grounded in
concrete analysis* rather than a blind "make this better".

| Provider  | Environment variable | Default model        |
|-----------|----------------------|----------------------|
| Anthropic | `ANTHROPIC_API_KEY`  | `claude-sonnet-5-5`  |
| OpenAI    | `OPENAI_API_KEY`     | `gpt-5.5`            |
| Gemini    | `GEMINI_API_KEY`     | `gemini-3.8-flash`   |

Optional overrides: `LLM_PROVIDER` (`auto|anthropic|openai|gemini`), `ANTHROPIC_MODEL`,
`OPENAI_MODEL`, `GEMINI_MODEL`, `LLM_MAX_TOKENS`, `LLM_TIMEOUT_SECONDS`, `SERVER_PORT`.

If the provider call fails or returns unparsable output, the service **falls back to the local
engine** and says so in `notes` — the request never fails because of a provider outage.

## Project layout

```
src/main/java/com/promptforge/
├── PromptForgeApplication.java
├── core/
│   ├── engine/          # the local engine - no Spring, no I/O, fully unit testable
│   │   ├── PromptAnalyzer.java     # scoring + diagnostics
│   │   ├── PromptRewriter.java     # reshaped prompt + template + questions
│   │   ├── PromptTemplates.java    # per-domain defaults
│   │   ├── DomainLexicon.java      # vocabulary: roles, verbs, vagueness, conflicts
│   │   └── TextUtil.java
│   └── model/           # records + enums (Dimension, Severity, TaskType, Profile, ...)
├── llm/                 # optional Anthropic / OpenAI / Gemini clients + gateway
├── service/             # orchestration, rule engine first, LLM upgrade second
└── web/                 # REST controller + error handling
src/main/resources/static/   # single-page UI (plain HTML/CSS/JS, no build step)
src/test/java/                # 27 tests over the engine and the HTTP layer
```

## Testing

```bash
mvnw test
```

Covers the analyzer (scoring, politeness stripping, contradiction detection, extraction),
the rewriter (section completeness, placeholder handling, constraint carry-over) and the HTTP
layer (success path, validation error, metadata).

## Design notes

- **The engine is pure.** `PromptAnalyzer` and `PromptRewriter` have no Spring dependencies and
  no I/O, so they are fast to test and easy to port to another stack.
- **Nothing is invented silently.** The reshaped prompt fills gaps with *labelled defaults*; the
  template keeps the gaps visible as `<placeholders>`, and the questions list says which ones
  matter.
- **The LLM path is additive.** It refines the rule-based output; it never becomes a hard
  dependency.
