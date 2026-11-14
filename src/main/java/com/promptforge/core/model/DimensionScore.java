package com.promptforge.core.model;

/**
 * Score of a single prompt dimension.
 *
 * @param dimension the dimension
 * @param score     0.0 - 1.0 coverage of that dimension
 * @param present   whether the original prompt contained usable material for it
 * @param note      short explanation of the score
 */
public record DimensionScore(
        Dimension dimension,
        double score,
        boolean present,
        String note) {
}
