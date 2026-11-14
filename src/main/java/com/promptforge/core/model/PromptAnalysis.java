package com.promptforge.core.model;

import java.util.List;

/**
 * The structured result of analysing the original prompt.
 *
 * @param original          the prompt as submitted
 * @param wordCount         word count of the original
 * @param score             overall quality score, 0 - 100
 * @param grade             human readable grade, e.g. "Good"
 * @param dimensions        per-dimension scores
 * @param diagnostics       ordered list of findings (critical first)
 * @param detectedRole      role found in the prompt, if any
 * @param detectedAudience  audience found in the prompt, if any
 * @param detectedStack     technologies/domains found in the prompt, if any
 * @param detectedGoal      goal/purpose clause found in the prompt, if any
 * @param extractedConstraints constraint sentences lifted out of the prompt
 * @param extractedFormat   output format found in the prompt, if any
 * @param extractedExamples example lines lifted out of the prompt
 * @param cleanedRequest    the request with politeness wrappers removed
 * @param taskType          inferred type of work
 * @param taskStatement     the actionable core of the prompt
 */
public record PromptAnalysis(
        String original,
        int wordCount,
        int score,
        String grade,
        List<DimensionScore> dimensions,
        List<Diagnostic> diagnostics,
        String detectedRole,
        String detectedAudience,
        String detectedStack,
        String detectedGoal,
        List<String> extractedConstraints,
        String extractedFormat,
        List<String> extractedExamples,
        String cleanedRequest,
        TaskType taskType,
        String taskStatement) {

    /**
     * @param dimension the dimension to look up
     * @return the score of that dimension, or 0 when it was not scored
     */
    public double scoreOf(Dimension dimension) {
        return dimensions.stream()
                .filter(score -> score.dimension() == dimension)
                .mapToDouble(DimensionScore::score)
                .findFirst()
                .orElse(0.0);
    }

    /**
     * @param dimension the dimension to look up
     * @return true when the prompt contained usable material for that dimension
     */
    public boolean has(Dimension dimension) {
        return dimensions.stream()
                .anyMatch(score -> score.dimension() == dimension && score.present());
    }
}
