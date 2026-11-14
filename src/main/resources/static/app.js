/* PromptForge front-end - plain JavaScript, no build step. */
(function () {
    "use strict";

    var $ = function (id) { return document.getElementById(id); };

    var SAMPLES = {
        vague: "can you write me a good post about our new api, it should be professional and not too long, for developers",
        code: "I need you to write a function in java that takes a list and sorts it",
        analysis: "analyze our sales csv and tell me what's going on with revenue",
        writing: "help me write an email to the team about the deadline moving"
    };

    var state = { meta: null, lastResult: null };

    /* ------------------------------------------------------------------ helpers */
    function escapeHtml(text) {
        return String(text == null ? "" : text)
            .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
    }

    function scoreColor(score) {
        if (score >= 85) { return "var(--good)"; }
        if (score >= 70) { return "#7fd6a8"; }
        if (score >= 50) { return "var(--warn)"; }
        return "var(--crit)";
    }

    function postJson(url, body) {
        return fetch(url, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        }).then(function (response) {
            return response.json().then(function (data) {
                if (!response.ok) {
                    var message = data && data.error ? data.error : "Request failed (" + response.status + ")";
                    if (data && data.fields) {
                        var parts = [];
                        Object.keys(data.fields).forEach(function (key) {
                            parts.push(key + ": " + data.fields[key]);
                        });
                        message += " - " + parts.join(", ");
                    }
                    throw new Error(message);
                }
                return data;
            });
        });
    }

    /* ------------------------------------------------------------------ bootstrap */
    function init() {
        loadMeta();
        bindEvents();
        updateWordCount();
    }

    function loadMeta() {
        fetch("/api/meta")
            .then(function (response) { return response.json(); })
            .then(function (meta) {
                state.meta = meta;
                var profileSelect = $("profile");
                meta.profiles.forEach(function (profile) {
                    var option = document.createElement("option");
                    option.value = profile.id;
                    option.textContent = profile.label;
                    profileSelect.appendChild(option);
                });

                var providerSelect = $("provider");
                meta.llmProviders.forEach(function (provider) {
                    var option = document.createElement("option");
                    option.value = provider;
                    option.textContent = provider;
                    providerSelect.appendChild(option);
                });

                var badge = $("llm-badge");
                if (meta.llmAvailable) {
                    badge.textContent = "LLM: " + meta.llmProviders.join(", ");
                    badge.classList.add("live");
                } else {
                    badge.textContent = "LLM: not configured";
                }
            })
            .catch(function () {
                $("llm-badge").textContent = "LLM status unknown";
            });
    }

    function bindEvents() {
        $("prompt").addEventListener("input", updateWordCount);

        $("reshape").addEventListener("click", reshape);
        $("clear").addEventListener("click", function () {
            $("prompt").value = "";
            updateWordCount();
            $("prompt").focus();
        });

        $("engine").addEventListener("change", function () {
            var llm = this.value === "LLM";
            $("provider-field").hidden = !llm;
            $("engine-badge").textContent = llm ? "LLM engine" : "Local engine";
        });

        document.querySelectorAll(".sample").forEach(function (button) {
            button.addEventListener("click", function () {
                var prompt = $("prompt");
                prompt.value = SAMPLES[button.dataset.sample] || "";
                updateWordCount();
                prompt.focus();
            });
        });

        document.querySelectorAll(".tab").forEach(function (tab) {
            tab.addEventListener("click", function () {
                document.querySelectorAll(".tab").forEach(function (other) { other.classList.remove("active"); });
                document.querySelectorAll(".tab-panel").forEach(function (panel) { panel.hidden = true; });
                tab.classList.add("active");
                $("tab-" + tab.dataset.tab).hidden = false;
            });
        });

        document.querySelectorAll("[data-copy]").forEach(function (button) {
            button.addEventListener("click", function () {
                copyText(button);
            });
        });

        document.addEventListener("keydown", function (event) {
            if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
                event.preventDefault();
                reshape();
            }
        });
    }

    function updateWordCount() {
        var value = $("prompt").value.trim();
        var words = value ? value.split(/\s+/).length : 0;
        $("word-count").textContent = words;
    }

    function copyText(button) {
        var target = $(button.dataset.copy);
        var text = target ? target.textContent : "";
        var done = function () {
            var original = button.textContent;
            button.textContent = "Copied";
            button.classList.add("done");
            setTimeout(function () {
                button.textContent = original;
                button.classList.remove("done");
            }, 1400);
        };
        if (navigator.clipboard && navigator.clipboard.writeText) {
            navigator.clipboard.writeText(text).then(done).catch(function () { fallbackCopy(text, done); });
        } else {
            fallbackCopy(text, done);
        }
    }

    function fallbackCopy(text, done) {
        var area = document.createElement("textarea");
        area.value = text;
        document.body.appendChild(area);
        area.select();
        try { document.execCommand("copy"); } catch (ignore) { /* noop */ }
        document.body.removeChild(area);
        done();
    }

    /* ------------------------------------------------------------------ actions */
    function reshape() {
        var prompt = $("prompt").value.trim();
        if (!prompt) {
            showError("Write or paste a prompt first.");
            $("prompt").focus();
            return;
        }

        var button = $("reshape");
        button.disabled = true;
        button.textContent = "Reshaping...";
        clearError();

        var payload = {
            prompt: prompt,
            profile: $("profile").value,
            engine: $("engine").value,
            includeExamples: $("include-examples").checked,
            strict: $("strict").checked
        };
        if ($("engine").value === "LLM" && $("provider").value) {
            payload.provider = $("provider").value;
        }

        postJson("/api/reshape", payload)
            .then(function (result) {
                state.lastResult = result;
                render(result);
            })
            .catch(function (error) {
                showError(error.message);
            })
            .finally(function () {
                button.disabled = false;
                button.textContent = "Reshape prompt";
            });
    }

    function showError(message) {
        var existing = document.querySelector(".error-toast");
        if (existing) { existing.remove(); }
        var toast = document.createElement("div");
        toast.className = "error-toast";
        toast.textContent = message;
        $("results-panel").prepend(toast);
    }

    function clearError() {
        var existing = document.querySelector(".error-toast");
        if (existing) { existing.remove(); }
    }

    /* ------------------------------------------------------------------ rendering */
    function render(result) {
        var analysis = result.analysis;

        $("empty-state").hidden = true;
        $("result").hidden = false;
        $("result-meta").textContent = analysis.wordCount + " words in - " + result.engineUsed;

        var score = analysis.score;
        $("score").textContent = score;
        $("grade").textContent = analysis.grade;
        $("grade").style.color = scoreColor(score);
        $("score-hint").textContent = scoreHint(score);

        var circumference = 2 * Math.PI * 52;
        var ring = $("ring-fg");
        ring.style.stroke = scoreColor(score);
        ring.style.strokeDasharray = circumference;
        ring.style.strokeDashoffset = circumference * (1 - score / 100);

        renderDimensions(analysis.dimensions);
        renderNotes(result.notes);

        $("reshaped-prompt").textContent = result.reshapedPrompt;
        $("template-prompt").textContent = result.templatePrompt;
        $("reshaped-note").textContent = "Ready to paste - sections: role, task, context, constraints, format, acceptance.";

        var questions = $("questions");
        questions.innerHTML = "";
        (result.questions || []).forEach(function (question) {
            var item = document.createElement("li");
            item.textContent = question;
            questions.appendChild(item);
        });

        var findings = $("findings");
        findings.innerHTML = "";
        (analysis.diagnostics || []).forEach(function (diagnostic) {
            findings.appendChild(buildFinding(diagnostic));
        });
    }

    function scoreHint(score) {
        if (score >= 85) { return "This prompt already follows the anatomy well."; }
        if (score >= 70) { return "Solid start - a few gaps remain."; }
        if (score >= 50) { return "Usable, but the model has to guess a lot."; }
        return "Too underspecified - the reshaped version fills the gaps.";
    }

    function renderDimensions(dimensions) {
        var container = $("dimensions");
        container.innerHTML = "";
        (dimensions || []).forEach(function (dimension) {
            var row = document.createElement("div");
            row.className = "dim";

            var label = document.createElement("div");
            label.className = "dim-label";
            label.innerHTML = escapeHtml(dimension.dimension.label)
                + "<small>" + Math.round(dimension.dimension.weight * 100) + "% weight</small>";

            var bar = document.createElement("div");
            bar.className = "dim-bar";
            bar.title = dimension.note || "";
            var fill = document.createElement("div");
            fill.className = "dim-fill" + (dimension.score >= 1 ? "" : (dimension.score > 0 ? " partial" : " missing"));
            fill.style.width = Math.round(dimension.score * 100) + "%";
            bar.appendChild(fill);

            var value = document.createElement("div");
            value.className = "dim-val";
            value.textContent = Math.round(dimension.score * 100) + "%";

            row.appendChild(label);
            row.appendChild(bar);
            row.appendChild(value);
            container.appendChild(row);
        });
    }

    function renderNotes(notes) {
        var existing = document.querySelector(".notes");
        if (existing) { existing.remove(); }
        if (!notes || !notes.length) { return; }
        var box = document.createElement("div");
        box.className = "notes";
        box.textContent = notes.join(" ");
        $("result").prepend(box);
    }

    function buildFinding(diagnostic) {
        var item = document.createElement("li");
        item.className = "finding " + diagnostic.severity;

        var head = document.createElement("div");
        head.className = "finding-head";
        head.innerHTML = "<span class=\"sev " + diagnostic.severity + "\">" + escapeHtml(diagnostic.severity) + "</span>"
            + "<span class=\"finding-title\">" + escapeHtml(diagnostic.title) + "</span>"
            + (diagnostic.autoFixed ? "<span class=\"sev INFO\">auto-fixed</span>" : "");
        item.appendChild(head);

        var detail = document.createElement("p");
        detail.className = "finding-detail";
        detail.textContent = diagnostic.detail;
        item.appendChild(detail);

        var fix = document.createElement("p");
        fix.className = "finding-fix";
        fix.innerHTML = "<b>Fix:</b> " + escapeHtml(diagnostic.suggestion);
        item.appendChild(fix);

        return item;
    }

    document.addEventListener("DOMContentLoaded", init);
})();
