# Software Requirements Document — PromptForge

| Field | Value |
|---|---|
| **Document ID** | SRD-PF-001 |
| **Product** | PromptForge |
| **Version** | 1.0.0 |
| **Status** | Implemented (as-built) |
| **Date** | 2026-11-14 |
| **Owner** | PromptForge engineering |
| **Related documents** | `README.md`, `architecture.json` |

---

## 1. Introduction

### 1.1 Purpose

This document specifies the functional and non-functional requirements of **PromptForge**, an
application that re-shapes any given prompt into a best-practice prompt. It is written as an
*as-built* specification: every requirement below is implemented, and the traceability matrix
in section 11 points at the code and the test that proves it.

### 1.2 Scope

**In scope.** Analysing a prompt against a defined best-practice anatomy, scoring it, explaining
the gaps, producing a rewritten prompt, producing a fill-in-the-blank template, and producing the
clarifying questions that remain open. An optional LLM path that upgrades the rewrite.

**Out of scope (v1.0).** Prompt storage or history, user accounts, prompt sharing or
collaboration, prompt versioning, A/B testing of prompts, evaluation against a live model, and
any write-back to external systems.

### 1.3 Definitions and acronyms

| Term | Meaning |
|---|---|
| **Prompt anatomy** | The seven dimensions a well-formed prompt is built from (role, task, context, constraints, output format, examples, acceptance criteria). |
| **Dimension** | One element of the anatomy, scored 0.0–1.0 with a fixed weight. |
| **Diagnostic** | A named finding about the original prompt, with a severity and a concrete fix. |
| **Reshaped prompt** | A complete, immediately usable prompt with labelled defaults filling the gaps. |
| **Template** | The same skeleton with `<placeholders>` wherever information is still missing. |
| **Frame** | A politeness wrapper such as `can you …` or `I need you to …` around the real request. |
| **Engine** | The component that produces the rewrite: the local rule engine or an LLM. |
| **Grade** | The qualitative band of the numeric score. |

### 1.4 Reference documents

- `architecture.json` — machine-readable architecture description (components, layers, API, domain model).
- `README.md` — operator guide and quick start.

---

## 2. Product overview

### 2.1 Problem statement

Prompt failures are usually *specification* failures rather than model failures: no role, no
deliverable, no output format, no limits, no definition of done. The user cannot see those gaps
and the model silently fills them with assumptions.

### 2.2 Product vision

Make prompt quality **measurable and fixable in seconds**, offline, without sending the user's
text to a third party.

### 2.3 Personas

| Persona | Need | Primary touchpoint |
|---|---|---|
| **Developer** | Turn a vague ticket or idea into a prompt that returns compilable, correctly shaped code. | Coding profile, code-oriented defaults |
| **Analyst** | Turn a question about data into a prompt with traceable numbers and a defined output shape. | Data analysis profile |
| **Writer / marketer** | Turn a rough brief into a prompt with a clear audience, tone and format. | Writing profile |
| **Prompt engineer** | Audit a prompt, see the weighted score, and know exactly which dimensions to fix. | Score, dimension bars, findings |

### 2.4 User stories

| ID | As a… | I want… | So that… |
|---|---|---|---|
| US-01 | developer | to paste the prompt exactly as I would type it | I see my real gap, not a polished paraphrase of it |
| US-02 | developer | to get a quality score with a per-dimension breakdown | I know which part of the prompt to fix first |
| US-03 | developer | to get a rewritten prompt I can paste immediately | I do not have to assemble the sections myself |
| US-04 | developer | to see a template with the unknown parts marked | I know what I still have to decide |
| US-05 | developer | to get the list of questions my prompt still needs answered | I close the gaps in one pass |
| US-06 | privacy-conscious user | to use the tool with no API key and no network | my prompt text never leaves my machine |
| US-07 | power user | to have an LLM rewrite grounded in the analyzer's findings | the rewrite is better than a blind "improve this" |

---

## 3. System overview

PromptForge is a single stateless Spring Boot process serving a REST API and a static
single-page UI.

```
Browser ──HTTP──► PromptController ──► PromptReshapeService ──┬─► PromptAnalyzer ─► PromptRewriter ─► result
                                                          │
                                                          └─► LlmGateway ─► Anthropic / OpenAI / Gemini
                                                                       (optional, falls back on failure)
```

The analysis core (`com.promptforge.core.*`) contains no Spring imports and performs no I/O.
The LLM path is strictly additive: no provider failure can fail a request.

---

## 4. Functional requirements

Priority: **M** = must, **S** = should, **C** = could.

### 4.1 Input handling

| ID | Requirement | Priority | Acceptance |
|---|---|---|---|
| FR-001 | The system shall accept a prompt of arbitrary length (1 to 100 000 characters) and analyse it. | M | A 1-character and a 100 000-character prompt both return a 200 response. |
| FR-002 | The system shall reject a blank prompt with HTTP 400 and a field-level error message. | M | `{"prompt":""}` → 400 with `fields.prompt = "prompt must not be blank"`. |
| FR-003 | The system shall accept an optional domain profile and apply its defaults when the prompt does not specify them. | M | Profile `WRITING` on an unspecified prompt yields writing-oriented role, format and acceptance criteria. |
| FR-004 | The system shall accept an optional engine selector (`RULE`, `LLM`) and default to `RULE`. | M | Omitted `engine` → `engineUsed = "Rule-based"`. |
| FR-005 | The system shall accept optional per-request overrides for the LLM provider and model id. | M | `provider=gemini` resolves the Gemini client; an unknown provider resolves nothing and triggers fallback. |
| FR-006 | The system shall accept an `includeExamples` flag controlling the presence of an Examples section. | S | `includeExamples=false` → no `## Examples` heading in the output. |
| FR-007 | The system shall accept a `strict` flag that keeps the placeholders in the reshaped prompt, so the user fills the gaps instead of accepting the defaults. | S | `strict=true` → the reshaped prompt contains `<…>` placeholders; `strict=false` → it contains none. |

### 4.2 Analysis

| ID | Requirement | Priority | Acceptance |
|---|---|---|---|
| FR-010 | The system shall score the prompt against the seven anatomy dimensions, each 0.0–1.0 with the defined weights. | M | A fully specified prompt scores ≥ 80; an empty prompt scores 0. |
| FR-011 | The system shall compute an overall score as the weighted sum, expressed 0–100, and a matching grade. | M | Score 86 → grade "Excellent"; score 52 → grade "Fair". |
| FR-012 | The system shall detect the role when the prompt states one (`you are …`, `act as …`, `as a/an <role> …`). | M | `"You are a senior data analyst. …"` → detectedRole contains "senior data analyst". |
| FR-013 | The system shall detect an actionable task, treating an imperative verb, a question, or a sentence containing an action verb as a task. | M | `"You are … Write a REST endpoint …"` yields a task statement, not the NO_TASK finding. |
| FR-014 | The system shall strip politeness frames from the extracted task. | M | `"I need you to write a function …"` → task starts with "Write". |
| FR-015 | The system shall detect audience, stack, goal, constraints, output format and examples when present. | M | Each appears in the corresponding `analysis.*` field, and drives the matching dimension score. |
| FR-016 | The system shall extract constraint clauses rather than whole sentences, keeping the noun phrase the constraint applies to. | M | `"Use only Spring Boot and JPA, no new dependencies."` yields "Use only Spring Boot and JPA" and "no new dependencies". |
| FR-017 | The system shall not mistake an input file name for a requested output format. | M | `"analyze our sales csv …"` → extractedFormat is null, NO_OUTPUT_FORMAT is raised. |
| FR-018 | The system shall detect a requested output format from an explicit phrase, an output context, or a format word next to an output verb. | M | `"Summarise the findings in JSON."` → extractedFormat = "JSON". |
| FR-019 | The system shall de-duplicate the detected stack so a generic token is dropped when a specific one covers it. | M | Stack for Spring Boot + Spring reads "Spring Boot", not "Spring Boot, Spring". |
| FR-020 | The system shall raise findings for every missing dimension and every quality problem it detects, ordered by severity. | M | Findings are ordered Critical → Warning → Info. |
| FR-021 | Each finding shall carry a prompt-specific fix, not generic advice. | M | CONFLICTING_SCOPES names the two conflicting words. |
| FR-022 | The system shall detect unmeasurable wording and name the terms it found. | M | "professional" in the prompt → VAGUE_TERMS naming "professional". |
| FR-023 | The system shall detect conflicting scopes that cannot both be satisfied. | M | "simple … comprehensive" → CONFLICTING_SCOPES. |
| FR-024 | The system shall award partial credit for a dimension that is only partly specified (for example a known stack with no audience). | S | Stack-only prompt → CONTEXT score 0.5. |
| FR-025 | The system shall mark a finding as auto-fixed when the rewriter already resolved it. | S | NO_ROLE is auto-fixed when a role was inferred from the stack. |

### 4.3 Rewriting

| ID | Requirement | Priority | Acceptance |
|---|---|---|---|
| FR-030 | The system shall produce a reshaped prompt containing the sections Role, Task, Context, Constraints, Output Format, Acceptance Criteria, and optionally Examples. | M | All headings present in `reshapedPrompt`. |
| FR-031 | The reshaped prompt shall use the user's own words for the task rather than paraphrasing it. | M | Task section matches the user's phrasing, minus the politeness frame. |
| FR-032 | The reshaped prompt shall preserve every explicit constraint from the original. | M | "Do not use any ORM" survives into the Constraints section. |
| FR-033 | The reshaped prompt shall use the role stated by the user when there is one, and infer a domain-appropriate role otherwise. | M | Stated role preserved verbatim; otherwise a role matching the detected domain is used. |
| FR-034 | The reshaped prompt shall supply a default output format when the original does not specify one. | M | A format section is always present. |
| FR-035 | The reshaped prompt shall supply checkable acceptance criteria appropriate to the task type. | M | Code tasks get "The solution compiles and is complete"; no phrase is unfalsifiable. |
| FR-036 | The reshaped prompt shall preserve any example the user supplied, and otherwise instruct for one. | M | A user example appears under Examples; a request for an example is not echoed as one. |
| FR-037 | When no actionable task can be extracted, the reshaped prompt shall quote the user's original request so the intent is never lost. | M | `"spring boot pagination for orders"` appears verbatim in the Task section. |
| FR-038 | The system shall produce a template with `<placeholders>` for every unknown section and the known values written out in full. | M | Known values unwrapped; only genuine gaps wrapped in `<…>`. |
| FR-039 | The system shall produce the list of clarifying questions needed to close the remaining gaps. | M | A prompt missing a role yields "Who should the AI be? …". |

### 4.4 LLM upgrade path

| ID | Requirement | Priority | Acceptance |
|---|---|---|---|
| FR-040 | The system shall support an LLM engine that receives the analyzer findings, the detected task type and the draft template. | M | The user message contains the findings and the template. |
| FR-041 | The system shall run the local analyzer before any provider call, so the LLM output is grounded in analysis. | M | The analysis is present in the response regardless of engine. |
| FR-042 | The system shall fall back to the local engine when no provider key is configured, and say so in `notes`. | M | `engineUsed = "Rule-based (fallback)"` and a note naming the missing environment variable. |
| FR-043 | The system shall fall back to the local engine when the provider call fails or the response cannot be parsed, and say so in `notes`. | M | A failed call produces a 200 response with the rule-based result and an explanatory note. |
| FR-044 | The system shall request a JSON object from the model and tolerate code fences around it. | M | A fenced JSON answer is parsed successfully. |
| FR-045 | The system shall support Anthropic, OpenAI and Gemini, selecting by key availability when the provider is `auto`. | S | All three clients implement the same contract; auto-detect prefers anthropic, then openai, then gemini. |
| FR-046 | The system shall read no provider credential from source control; keys come from environment variables only. | M | No key literal exists in the repository. |

### 4.5 API and UI

| ID | Requirement | Priority | Acceptance |
|---|---|---|---|
| FR-050 | The system shall expose `POST /api/reshape`, `GET /api/meta` and `GET /api/health`. | M | All three respond 200 with the documented shapes. |
| FR-051 | The system shall return a consistent JSON error shape for validation and unexpected failures. | M | `{error, timestamp}` plus `fields` for validation failures. |
| FR-052 | The UI shall let the user type a prompt, pick a profile and an engine, and trigger a reshape. | M | End-to-end flow works in the browser. |
| FR-053 | The UI shall show the score, the grade, and a per-dimension bar with its weight. | M | Seven bars, each with a percentage and its weight. |
| FR-054 | The UI shall present the reshaped prompt, the template, the clarifying questions and the findings in separate tabs. | M | Four tabs, each populated after a reshape. |
| FR-055 | The UI shall offer copy-to-clipboard for the reshaped prompt, the template and the questions. | M | Each copy button reports "Copied". |
| FR-056 | The UI shall show which LLM providers are configured. | S | The header badge reads "LLM: not configured" when no key is present. |
| FR-057 | The UI shall offer one-click sample prompts and a keyboard shortcut. | C | Four sample chips; Ctrl+Enter triggers the reshape. |
| FR-058 | The UI shall require no build step and no third-party CDN. | M | The page works fully offline. |

---

## 5. Non-functional requirements

| ID | Requirement | Target | How it is met |
|---|---|---|---|
| NFR-001 | **Determinism** — the local engine returns the same output for the same input on every platform. | 100% | Pure functions, no clock, no randomness, no I/O in the core. |
| NFR-002 | **Offline capability** — the full feature set works with no API key and no network. | 100% | The LLM path is optional and additive. |
| NFR-003 | **Privacy** — prompt content never leaves the machine unless the caller selects the LLM engine. | 100% | No telemetry, no analytics, no outbound call on the RULE path. |
| NFR-004 | **Latency** — a local reshape completes fast enough to feel instantaneous. | < 250 ms for a 1 000-word prompt | Single-pass regex analysis; no network on the RULE path. |
| NFR-005 | **Resilience** — a provider outage degrades the result rather than failing the request. | 100% | Catch-and-fallback in `PromptReshapeService`. |
| NFR-006 | **Portability** — the analysis core can be reused outside Spring Boot. | No Spring imports in `com.promptforge.core` | The core is a plain library. |
| NFR-007 | **Maintainability** — the catalogue of findings, dimensions and defaults lives in data, not in scattered literals. | Named constants and enums only | `DomainLexicon`, `PromptTemplates`, `Dimension`. |
| NFR-008 | **Testability** — every requirement above is covered by an automated test. | ≥ 90% of requirements traced | 32 tests; see the traceability matrix in section 11. |
| NFR-009 | **Portability of the build** — the project builds with only a JDK installed. | No local Maven required | The Maven Wrapper is committed. |
| NFR-010 | **Operational simplicity** — no database, no migrations, no external state. | Single process | Stateless service. |
| NFR-011 | **Accessibility of output** — generated prompts use plain Markdown headings and lists. | 100% | The rewriter emits a fixed section skeleton. |
| NFR-012 | **Forward compatibility** — provider and model ids are configurable without a code change. | 3 providers | Environment-variable overrides. |

---

## 6. Data model

All payloads are immutable records. There is no persistence.

### 6.1 `PromptRequest` (input)

| Field | Type | Required | Default | Notes |
|---|---|---|---|---|
| `prompt` | String | yes | — | Must not be blank. |
| `profile` | String | no | `GENERALIST` | One of the seven profiles; unknown values fall back to GENERALIST. |
| `engine` | String | no | `RULE` | `RULE` or `LLM`. |
| `strict` | Boolean | no | `false` | Template mode: the reshaped prompt keeps its `<placeholders>` instead of the defaults. |
| `includeExamples` | Boolean | no | `true` | Adds the Examples section. |
| `provider` | String | no | — | `anthropic`, `openai`, `gemini`. |
| `model` | String | no | — | Provider model id override. |

### 6.2 `PromptResult` (output)

| Field | Type | Meaning |
|---|---|---|
| `original` | String | The prompt as submitted. |
| `reshapedPrompt` | String | The complete, usable prompt. |
| `templatePrompt` | String | The skeleton with `<placeholders>` for genuine gaps. |
| `questions` | List\<String\> | The clarifying questions still open. |
| `analysis` | `PromptAnalysis` | The full analysis of the original. |
| `engineUsed` | String | Which engine produced the result. |
| `notes` | List\<String\> | Engine notes, e.g. a fallback explanation. |

### 6.3 `PromptAnalysis`

| Field | Type | Meaning |
|---|---|---|
| `original` | String | The prompt as submitted. |
| `wordCount` | int | Word count of the original. |
| `score` | int | Weighted quality score, 0–100. |
| `grade` | String | Excellent / Good / Fair / Weak / Poor. |
| `dimensions` | List\<`DimensionScore`\> | Per-dimension score, presence flag and explanation. |
| `diagnostics` | List\<`Diagnostic`\> | Findings, most severe first. |
| `detectedRole` | String | The role found, if any. |
| `detectedAudience` | String | The audience found, if any. |
| `detectedStack` | String | The technologies found, if any. |
| `detectedGoal` | String | The goal clause found, if any. |
| `extractedConstraints` | List\<String\> | Constraint clauses lifted from the prompt. |
| `extractedFormat` | String | The requested output format, if any. |
| `extractedExamples` | List\<String\> | Example lines lifted from the prompt. |
| `cleanedRequest` | String | The request with politeness wrappers removed. |
| `taskType` | Enum | `CODE`, `DATA`, `WRITING`, `RESEARCH`, `PRODUCT`, `BUSINESS`, `GENERAL`. |
| `taskStatement` | String | The actionable core of the prompt. |

### 6.4 `Dimension` (enum, serialised as an object)

| Field | Type | Meaning |
|---|---|---|
| `id` | String | Enum name. |
| `label` | String | Display label. |
| `weight` | double | Contribution to the overall score. |
| `description` | String | What the dimension is for. |

| Dimension | Weight |
|---|---|
| ROLE | 0.10 |
| TASK | 0.20 |
| CONTEXT | 0.16 |
| CONSTRAINTS | 0.16 |
| OUTPUT_FORMAT | 0.16 |
| EXAMPLES | 0.08 |
| ACCEPTANCE | 0.14 |

### 6.5 `Diagnostic`

| Field | Type | Meaning |
|---|---|---|
| `code` | String | Stable machine-readable identifier. |
| `title` | String | Short headline. |
| `detail` | String | Why it matters for this prompt. |
| `severity` | Enum | `CRITICAL`, `WARNING`, `INFO`. |
| `dimension` | `Dimension` | The dimension affected, if any. |
| `suggestion` | String | The concrete fix. |
| `autoFixed` | boolean | True when the rewriter already handled it. |

### 6.6 Diagnostic catalogue

| Code | Severity | Dimension |
|---|---|---|
| `EMPTY_PROMPT` | Critical | — |
| `NO_TASK` | Critical | Task |
| `NO_OUTPUT_FORMAT` | Critical | Output format |
| `TOO_SHORT` | Critical | — |
| `NO_ROLE` | Warning | Role |
| `NO_CONTEXT` | Warning | Context |
| `NO_CONSTRAINTS` | Warning | Constraints |
| `NO_ACCEPTANCE` | Warning | Acceptance criteria |
| `VAGUE_TERMS` | Warning | — |
| `CONFLICTING_SCOPES` | Warning | — |
| `MULTIPLE_DELIVERABLES` | Warning | — |
| `POLITE_FRAME` | Info | Task |
| `NO_EXAMPLES` | Info | Examples |
| `TOO_LONG` | Info | — |
| `WALL_OF_TEXT` | Info | — |
| `CHAINED_REQUESTS` | Info | — |

---

## 7. API specification

### 7.1 `POST /api/reshape`

**Request** — `application/json`, body is a `PromptRequest` (section 6.1).

**Response 200** — `application/json`, body is a `PromptResult` (section 6.2).

**Errors**

| Status | Condition | Body |
|---|---|---|
| 400 | `prompt` blank | `{error, timestamp, fields:{prompt}}` |
| 500 | unexpected failure | `{error, timestamp}` |

**Example**

```http
POST /api/reshape
Content-Type: application/json

{ "prompt": "can you write me a good post about our new api, professional, for developers",
  "profile": "WRITING", "engine": "RULE" }
```

```json
{
  "original": "can you write me a good post ...",
  "reshapedPrompt": "## Role\nYou are a senior writer who adapts the message to the reader.\n\n## Task\n...",
  "templatePrompt": "## Role\nYou are <expert persona and seniority>\n\n...",
  "questions": ["Who should the AI be? Name the persona and its seniority ..."],
  "analysis": {
    "score": 52,
    "grade": "Fair",
    "wordCount": 21,
    "taskType": "WRITING",
    "detectedAudience": "developers",
    "dimensions": [{ "dimension": { "id": "TASK", "label": "Task", "weight": 0.2 }, "score": 1.0, "present": true }],
    "diagnostics": [{ "code": "NO_OUTPUT_FORMAT", "severity": "CRITICAL", "title": "No output format specified" }]
  },
  "engineUsed": "Rule-based",
  "notes": []
}
```

### 7.2 `GET /api/meta`

Returns the profiles, the dimension anatomy with weights, the configured LLM providers, and the
`llmAvailable` flag the UI uses to show or hide the provider selector.

### 7.3 `GET /api/health`

Returns `{"status":"UP"}`. Suitable as a container liveness probe.

---

## 8. User interface requirements

| ID | Requirement |
|---|---|
| UI-01 | Two-column layout: input on the left, analysis and result on the right; single column below 1080 px. |
| UI-02 | The prompt textarea shows a live word count. |
| UI-03 | Domain profile and engine are selected from dropdowns; the provider dropdown appears only when the LLM engine is selected. |
| UI-04 | The score is shown as a ring with the numeric value and the grade, colour-coded by band. |
| UI-05 | Each dimension renders as a labelled bar with its weight; missing dimensions are red, partial ones amber. |
| UI-06 | Findings render as cards with a severity badge, an explanation and the fix; auto-fixed findings are additionally tagged. |
| UI-07 | Results are presented in four tabs: Reshaped prompt, Template, Clarifying questions, Findings. |
| UI-08 | Copy buttons for the reshaped prompt, the template and the questions, with a transient confirmation. |
| UI-09 | A banner states whether an LLM provider is configured. |
| UI-10 | Four sample prompts are offered for one-click demonstration. |
| UI-11 | Ctrl+Enter triggers a reshape from anywhere on the page. |
| UI-12 | The UI is dark-themed, self-contained (no CDN, no web fonts) and fully usable offline. |

---

## 9. Configuration and deployment

### 9.1 Environment variables

| Variable | Purpose | Default |
|---|---|---|
| `SERVER_PORT` | HTTP port | `8080` |
| `LLM_PROVIDER` | Preferred provider: `auto`, `anthropic`, `openai`, `gemini` | `auto` |
| `ANTHROPIC_API_KEY` | Anthropic credential | empty |
| `ANTHROPIC_MODEL` | Anthropic model id | `claude-sonnet-5-5` |
| `OPENAI_API_KEY` | OpenAI credential | empty |
| `OPENAI_MODEL` | OpenAI model id | `gpt-5.5` |
| `GEMINI_API_KEY` | Gemini credential | empty |
| `GEMINI_MODEL` | Gemini model id | `gemini-3.8-flash` |
| `LLM_MAX_TOKENS` | Maximum tokens requested from the model | `4000` |
| `LLM_TIMEOUT_SECONDS` | Provider request timeout | `60` |

### 9.2 Deployment

| Aspect | Requirement |
|---|---|
| Packaging | Executable jar via the Spring Boot repackage goal, and a multi-stage Docker image. |
| Run | `java -jar target/prompt-forge-1.0.0.jar`, `docker compose up -d`, or the supplied systemd unit. |
| Development | `mvnw spring-boot:run`. |
| State | None — no database, no session store, no migration step. |
| Scaling | Horizontally scalable; instances are interchangeable and stateless. |
| Health | `GET /api/health`, wired into the container health check and the Kubernetes probes. |
| Process identity | The container runs as the non-root user `prompt-forge` (uid 1001) with a read-only root filesystem. |
| Reverse proxy | nginx configuration supplied for TLS, security headers, rate limiting and client-IP forwarding. |
| Secret handling | Provider keys are read from environment variables only; the filled-in env file is git-ignored and mode 600. |
| Authentication | Not implemented in v1.0 — the deployment guide requires an external control (VPN, basic auth, or an identity-aware proxy) before public exposure. |
| Reference | [`DEPLOYMENT.md`](../DEPLOYMENT.md) |

---

## 10. Test strategy

| Level | Scope | Technique |
|---|---|---|
| Domain | `PromptAnalyzer` | Behavioural assertions over the public `analyze` method: scoring bands, politeness stripping, role and audience detection, constraint clause extraction, format detection, stack de-duplication, contradiction and vagueness detection. |
| Domain | `PromptRewriter` | Assertions over the rewrite artefacts: section completeness, placeholder policy, constraint carry-over, preservation of the original request, preservation of user examples. |
| Web | `PromptController` | MockMvc over the full application context: success path, blank-prompt validation, metadata endpoint, health endpoint. |
| Manual | UI | Browser-driven walkthrough of every tab, the copy actions, the sample prompts and the fallback notice. |

**Test inventory (32 tests)**

| Suite | Tests |
|---|---|
| `PromptAnalyzerTest` | 17 |
| `PromptRewriterTest` | 9 |
| `PromptControllerTest` | 6 |

---

## 11. Traceability matrix

| Requirement | Implementation | Verified by |
|---|---|---|
| FR-001, FR-010, FR-011 | `PromptAnalyzer.analyze` | `PromptAnalyzerTest.wellFormedPromptScoresHigh`, `PromptAnalyzerTest.emptyPromptIsCritical` |
| FR-002, FR-050, FR-051 | `PromptController`, `ApiExceptionHandler`, `PromptRequest` | `PromptControllerTest.blankPromptIsRejected` |
| FR-003 | `PromptProfile`, `PromptTemplates` | `PromptRewriterTest.reshapedPromptHasAllSections` |
| FR-004, FR-005 | `PromptRequest.engineOrDefault`, `LlmGateway.resolve` | `PromptControllerTest.reshapeReturnsResult` |
| FR-012 | `PromptAnalyzer.findRole` | `PromptAnalyzerTest.roleIsDetected` |
| FR-013 | `PromptAnalyzer.findTask` | `PromptAnalyzerTest.wellFormedPromptScoresHigh` |
| FR-014 | `PromptAnalyzer.stripFrames` | `PromptAnalyzerTest.politeWrapperIsStripped`, `PromptAnalyzerTest.needYouToWrapperIsStripped`, `PromptAnalyzerTest.wantToWrapperIsStripped` |
| FR-015 | `PromptAnalyzer.findAudience/findStack/findGoal/findExamples` | `PromptAnalyzerTest.constraintsAndStackAreExtracted`, `PromptAnalyzerTest.userExamplesAreCaptured` |
| FR-016 | `PromptAnalyzer.splitClauses` | `PromptAnalyzerTest.constraintKeepsItsNounPhrase` |
| FR-017, FR-018 | `PromptAnalyzer.findFormat` | `PromptAnalyzerTest.inputFileIsNotAnOutputFormat`, `PromptAnalyzerTest.formatWordInOutputContextIsDetected`, `PromptAnalyzerTest.outputFormatIsCaptured` |
| FR-019 | `PromptAnalyzer.findStack` | `PromptAnalyzerTest.genericStackTokenIsDropped` |
| FR-020, FR-021, FR-022, FR-023 | `PromptAnalyzer` diagnostics | `PromptAnalyzerTest.vagueRequestIsFlagged`, `PromptAnalyzerTest.conflictingScopesAreReported` |
| FR-024 | `PromptAnalyzer.scoreContext` | `PromptAnalyzerTest.knownStackEarnsPartialContextCredit` |
| FR-030, FR-031 | `PromptRewriter.rewrite` | `PromptRewriterTest.reshapedPromptHasAllSections`, `PromptRewriterTest.reshapedPromptIsNeverEmpty` |
| FR-032 | `PromptRewriter` constraint merge | `PromptRewriterTest.originalConstraintsAreCarriedOver` |
| FR-033 | `PromptRewriter.resolveRole` | `PromptRewriterTest.existingRoleIsPreserved` |
| FR-034, FR-035 | `PromptTemplates` | `PromptRewriterTest.reshapedPromptHasAllSections` |
| FR-036 | `PromptAnalyzer.findExamples` | `PromptAnalyzerTest.exampleRequestIsNotAnExample` |
| FR-037 | `PromptRewriter.resolveTask` | `PromptRewriterTest.originalRequestSurvivesWithoutATask`, `PromptRewriterTest.politenessWrapperIsRemovedFromTheQuotedRequest` |
| FR-038 | `PromptRewriter.orPlaceholder` | `PromptRewriterTest.templateKeepsPlaceholders`, `PromptRewriterTest.templateWritesKnownValuesInFull` |
| FR-039 | `PromptRewriter.buildQuestions` | `PromptRewriterTest.vaguePromptProducesQuestions` |
| FR-040, FR-041, FR-044 | `PromptReshapeService.userPrompt/parse` | `PromptControllerTest.reshapeReturnsResult` (engine coverage via configuration) |
| FR-042, FR-043 | `PromptReshapeService.reshapeWithLlm` | Manual: LLM requested without a key → `Rule-based (fallback)` + note |
| FR-045 | `AnthropicLlmClient`, `OpenAiLlmClient`, `GeminiLlmClient` | Manual: provider resolution and badge in the UI |
| FR-006, FR-007 | `PromptReshapeService.reshape`, `PromptRequest.strictOrDefault` | `PromptControllerTest.strictModeKeepsPlaceholders`, `PromptControllerTest.defaultModeFillsTheGaps` |
| FR-050 | `PromptController` | `PromptControllerTest.reshapeReturnsResult`, `PromptControllerTest.metaExposesProfiles`, `PromptControllerTest.healthIsUp` |
| FR-052 – FR-058 | `index.html`, `app.js`, `styles.css` | Manual browser walkthrough |
| NFR-001 – NFR-012 | Architecture | `architecture.json`; determinism, offline behaviour and fallback verified manually |

---

## 12. Risks and mitigations

| ID | Risk | Impact | Likelihood | Mitigation |
|---|---|---|---|---|
| R-01 | Heuristic detection misfires on unusual phrasing. | A wrong score or a spurious finding. | Medium | Findings always explain themselves and name the evidence; the reshaped prompt never depends on a detection being right, because the user's own words are carried through. |
| R-02 | The rewrite defaults are wrong for a domain not represented in the lexicon. | The reshaped prompt carries an unsuitable role or format. | Medium | Seven profiles cover the common cases; the template keeps the gap visible and the questions ask for it. |
| R-03 | A provider changes its API or model ids. | The LLM path fails. | Medium | Failures fall back to the local engine with a note; model ids are environment-variable configurable. |
| R-04 | A provider returns plausible but unparsable output. | A malformed rewrite is shown. | Medium | The JSON parser tolerates code fences and falls back to the rule-based result. |
| R-05 | Prompt content reaches a third party unintentionally. | A privacy incident. | Low | The RULE path performs no outbound call; the LLM path must be selected explicitly, and the UI labels the engine state in the header. |
| R-06 | The score is treated as an absolute truth. | Users over-trust a number. | Medium | The score is presented as a weighted heuristic with a visible per-dimension breakdown, never as a verdict. |

---

## 13. Future scope

| Item | Rationale |
|---|---|
| Prompt history and comparison | Users reshape the same prompt repeatedly; a diff between versions is the natural next step. |
| Custom anatomy weights per user | The weights are currently fixed; making them configurable would suit advanced teams. |
| Team vocabulary packs | Domain lexicons (in-house stack names, house style) would sharpen role and stack detection. |
| Browser extension | One-click "reshape this prompt" in the tool where the prompt is actually written. |
| Prompt test harness | Running a prompt against a model and scoring the output against the acceptance criteria it declares. |

---

## 14. Approval

| Role | Name | Date | Status |
|---|---|---|---|
| Author | PromptForge engineering | 2026-11-14 | Implemented |
| Reviewer | — | — | Pending |
| Approver | — | — | Pending |
