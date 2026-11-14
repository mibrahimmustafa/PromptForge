package com.promptforge.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PromptControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/reshape returns the reshaped prompt, template and analysis")
    void reshapeReturnsResult() throws Exception {
        String body = """
                {
                  "prompt": "can you write a good post about our new api for developers",
                  "profile": "WRITING",
                  "engine": "RULE"
                }
                """;

        mockMvc.perform(post("/api/reshape").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reshapedPrompt").isNotEmpty())
                .andExpect(jsonPath("$.templatePrompt").isNotEmpty())
                .andExpect(jsonPath("$.questions").isArray())
                .andExpect(jsonPath("$.analysis.score").isNumber())
                .andExpect(jsonPath("$.analysis.diagnostics").isArray())
                .andExpect(jsonPath("$.engineUsed").value("Rule-based"));
    }

    @Test
    @DisplayName("POST /api/reshape rejects a blank prompt")
    void blankPromptIsRejected() throws Exception {
        mockMvc.perform(post("/api/reshape").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    @DisplayName("GET /api/meta exposes the profiles and the prompt anatomy")
    void metaExposesProfiles() throws Exception {
        mockMvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profiles").isArray())
                .andExpect(jsonPath("$.dimensions").isArray())
                .andExpect(jsonPath("$.llmAvailable").exists());
    }

    @Test
    @DisplayName("GET /api/health reports the service state")
    void healthIsUp() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
