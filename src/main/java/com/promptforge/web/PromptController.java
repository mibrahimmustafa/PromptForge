package com.promptforge.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.promptforge.core.engine.PromptAnalyzer;
import com.promptforge.core.model.Dimension;
import com.promptforge.core.model.PromptProfile;
import com.promptforge.core.model.PromptRequest;
import com.promptforge.core.model.PromptResult;
import com.promptforge.llm.LlmGateway;
import com.promptforge.service.PromptReshapeService;

import jakarta.validation.Valid;

/**
 * HTTP API of PromptForge.
 */
@RestController
@RequestMapping("/api")
public class PromptController {

    private final PromptReshapeService service;
    private final LlmGateway gateway;

    public PromptController(PromptReshapeService service, LlmGateway gateway) {
        this.service = service;
        this.gateway = gateway;
    }

    /**
     * Reshapes a prompt.
     *
     * @param request the request payload
     * @return the reshaped prompt, the template, questions and the full analysis
     */
    @PostMapping("/reshape")
    public ResponseEntity<PromptResult> reshape(@Valid @RequestBody PromptRequest request) {
        return ResponseEntity.ok(service.reshape(request));
    }

    /**
     * Metadata the UI needs: profiles, the prompt anatomy, and which engines are usable.
     *
     * @return metadata payload
     */
    @GetMapping("/meta")
    public ResponseEntity<Map<String, Object>> meta() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("profiles", List.of(PromptProfile.values()).stream()
                .map(profile -> Map.of(
                        "id", profile.name(),
                        "label", profile.label(),
                        "taskType", profile.taskType().label()))
                .toList());
        payload.put("dimensions", List.of(Dimension.values()).stream()
                .map(dimension -> Map.of(
                        "id", dimension.name(),
                        "label", dimension.label(),
                        "weight", dimension.weight(),
                        "description", dimension.description()))
                .toList());
        payload.put("llmProviders", gateway.availableProviders());
        payload.put("llmAvailable", !gateway.availableProviders().isEmpty());
        payload.put("anatomy", PromptAnalyzer.dimensions().stream().map(Dimension::label).toList());
        return ResponseEntity.ok(payload);
    }

    /**
     * @return simple health payload
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
