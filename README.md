# PromptForge

Reshape any rough prompt into a **best-practice prompt** — with a score, the gaps explained, a
ready-to-paste rewrite, a fill-in-the-blank template, and the clarifying questions you still need
to answer.

Java · Spring Boot 4 (Jakarta EE) · local-first engine, optional LLM upgrade.

| Document | Purpose |
|---|---|
| **This file** | Operator guide and quick start |
| [`DEPLOYMENT.md`](DEPLOYMENT.md) | Production deployment — Docker, systemd, Kubernetes, security checklist |
| [`docs/SRD.md`](docs/SRD.md) | Software Requirements Document — functional/non-functional requirements, data model, API spec, traceability matrix |
| [`architecture.json`](architecture.json) | Machine-readable architecture — layers, components, domain model, API, algorithms, configuration |

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

The weighted total is the **prompt quality score** (0–100), banded as
`Excellent ≥ 85 · Good ≥ 70 · Fair ≥ 50 · Weak ≥ 30 · Poor`.

## What you get back

1. **Score & grade** – the weighted score plus a per-dimension breakdown.
2. **Findings** – every gap, ranked `Critical / Warning / Info`, each with a prompt-specific fix.
3. **Reshaped prompt** – a complete, usable prompt with the gaps filled in.
4. **Template** – the same skeleton with explicit `<placeholders>` where information is still
   missing, so nothing is silently invented.
5. **Clarifying questions** – the short list of answers that would close the remaining gaps.

### The findings it can raise

| Code | Severity | Fires when |
|---|---|---|
| `EMPTY_PROMPT` | Critical | There is nothing to reshape. |
| `NO_TASK` | Critical | No action verb and no question — nothing states what to produce. |
| `NO_OUTPUT_FORMAT` | Critical | No required output shape. The highest-leverage omission. |
| `TOO_SHORT` | Critical | Fewer than 12 words — too much is left to assumption. |
| `NO_ROLE` | Warning | No persona stated (auto-fixed when a role can be inferred). |
| `NO_CONTEXT` | Warning | No audience, background or goal. |
| `NO_CONSTRAINTS` | Warning | No limits or exclusions. |
| `NO_ACCEPTANCE` | Warning | No definition of done. |
| `VAGUE_TERMS` | Warning | Unfalsifiable wording (`professional`, `good`, `etc.`) — the terms are named. |
| `CONFLICTING_SCOPES` | Warning | Two terms that cannot both be satisfied (`simple` + `comprehensive`). |
| `MULTIPLE_DELIVERABLES` | Warning | Many different actions in one prompt. |
| `POLITE_FRAME` | Info | `can you …`, `I need you to …` — stripped automatically. |
| `NO_EXAMPLES` | Info | No sample of a good answer. |
| `TOO_LONG` / `WALL_OF_TEXT` | Info | Structuring would help. |
| `CHAINED_REQUESTS` | Info | Several requests chained with `and also` / `and then`. |

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
  "strict": false,            // template mode: the reshaped prompt keeps its <placeholders>
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

A blank prompt returns `400` with `{"error": "Validation failed", "fields": {"prompt": "…"}}`.

### `GET /api/meta`

Profiles, the dimension anatomy, and which LLM providers are configured.

### `GET /api/health`

Liveness probe — `{"status":"UP"}`.

## The two engines

### 1. Local rule-based engine (default)

Pure Java, deterministic, offline, no API key, no cost, and no prompt data leaving the machine.
It is a pattern-matching analyzer (`PromptAnalyzer`) plus a template-driven rewriter
(`PromptRewriter`):

- strips politeness frames (`can you…`, `I need you to…`, `I want to…`, `help me…`);
- detects role, audience, stack, goal, constraints, output format and examples;
- extracts constraint **clauses**, not whole sentences — *"Use only Spring Boot and JPA"* keeps its
  second half, while *"it should be professional and not too long"* splits into two constraints;
- ignores format words that are not output formats — *"our sales **csv**"* is an input file, not a
  requested output shape;
- de-duplicates the stack — *"Java, Spring Boot, JPA, REST"*, not *"…, Spring, …"*;
- infers the task type and injects the matching defaults for role, format and acceptance criteria;
- **never loses the request**: when no action can be extracted, the original wording is quoted into
  the Task section instead of degrading into a generic instruction.

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
src/test/java/                # 32 tests over the engine and the HTTP layer
deploy/                       # nginx config, systemd unit, env template
docs/SRD.md                   # Software Requirements Document
architecture.json             # machine-readable architecture
DEPLOYMENT.md                 # production deployment guide
```

## Deploying

```bash
# container (recommended)
docker compose up -d --build

# or the plain jar behind systemd
./mvnw clean package
sudo cp target/prompt-forge-1.0.0.jar /opt/prompt-forge/prompt-forge.jar
```

Full instructions, the security checklist and the Kubernetes manifests are in
[`DEPLOYMENT.md`](DEPLOYMENT.md).

## Testing

```bash
mvnw test
```

32 tests covering the analyzer (scoring, politeness stripping, constraint clause extraction,
format detection, stack de-duplication, contradiction and vagueness detection), the rewriter
(section completeness, placeholder policy, constraint carry-over, request preservation) and the
HTTP layer (success path, validation error, metadata, health).

## Design notes

- **The engine is pure.** `PromptAnalyzer` and `PromptRewriter` have no Spring dependencies and
  no I/O, so they are fast to test and easy to port to another stack.
- **Nothing is invented silently.** The reshaped prompt fills gaps with *labelled defaults*; the
  template keeps the gaps visible as `<placeholders>`, and the questions list says which ones
  matter.
- **The LLM path is additive.** It refines the rule-based output; it never becomes a hard
  dependency.
- **Findings explain themselves.** Every diagnostic names the evidence it saw and gives a fix you
  can apply, rather than a generic lecture.
