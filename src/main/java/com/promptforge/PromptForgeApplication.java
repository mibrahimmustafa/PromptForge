package com.promptforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * PromptForge - application entry point.
 *
 * <p>Reshapes rough, underspecified prompts into best-practice prompts using a local
 * deterministic analysis/rewrite engine, with an optional LLM upgrade path.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PromptForgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromptForgeApplication.class, args);
    }
}
