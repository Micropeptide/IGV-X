/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2026 Broad Institute, IGV-X contributors
 */
package org.broad.igv.diagnostic;

/**
 * IGV-X: a single diagnostic finding for the Diagnose Track/Session feature.
 * Each finding carries a severity, a category, a short title and a detail
 * message with actionable guidance.
 */
public class DiagnosticFinding {

    public enum Severity {
        OK("OK"),
        INFO("INFO"),
        WARNING("WARNING"),
        ERROR("ERROR");

        private final String label;

        Severity(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final Severity severity;
    private final DiagnosticCategory category;
    private final String title;
    private final String detail;

    public DiagnosticFinding(Severity severity, DiagnosticCategory category, String title, String detail) {
        this.severity = severity;
        this.category = category;
        this.title = title;
        this.detail = detail;
    }

    public Severity getSeverity() {
        return severity;
    }

    public DiagnosticCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    /** Render a compact single-line form for text reports. */
    public String toLine() {
        return String.format("[%s] [%s] %s%n        %s",
                severity.getLabel(), category.getLabel(), title, detail);
    }
}
