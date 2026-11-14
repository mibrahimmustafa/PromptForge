package com.promptforge.core.model;

/**
 * Severity of a finding produced by the analyzer.
 */
public enum Severity {

    CRITICAL("Critical", 0),
    WARNING("Warning", 1),
    INFO("Info", 2);

    private final String label;
    private final int rank;

    Severity(String label, int rank) {
        this.label = label;
        this.rank = rank;
    }

    public String label() {
        return label;
    }

    public int rank() {
        return rank;
    }
}
