/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2026 Broad Institute, IGV-X contributors
 */
package org.broad.igv.diagnostic;

import java.util.ArrayList;
import java.util.List;

/**
 * IGV-X: a collection of diagnostic findings for one track (or the whole
 * session). Produced by {@link TrackDiagnostics} and rendered by the
 * Diagnose dialog.
 */
public class DiagnosticReport {

    private final String subject;
    private final String subjectId;
    private final List<DiagnosticFinding> findings = new ArrayList<>();

    public DiagnosticReport(String subject, String subjectId) {
        this.subject = subject == null ? "(untitled)" : subject;
        this.subjectId = subjectId == null ? "" : subjectId;
    }

    public String getSubject() {
        return subject;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public void add(DiagnosticFinding finding) {
        if (finding != null) {
            findings.add(finding);
        }
    }

    public List<DiagnosticFinding> getFindings() {
        return findings;
    }

    public boolean hasError() {
        for (DiagnosticFinding f : findings) {
            if (f.getSeverity() == DiagnosticFinding.Severity.ERROR) {
                return true;
            }
        }
        return false;
    }

    public boolean hasWarning() {
        for (DiagnosticFinding f : findings) {
            if (f.getSeverity() == DiagnosticFinding.Severity.WARNING) {
                return true;
            }
        }
        return false;
    }

    /** Render the full report as text, ready for the dialog or clipboard. */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(subject).append(" ===");
        if (!subjectId.isEmpty()) {
            sb.append("  (").append(subjectId).append(")");
        }
        sb.append("\n");
        if (findings.isEmpty()) {
            sb.append("[INFO] No findings collected.\n");
        } else {
            for (DiagnosticFinding f : findings) {
                sb.append(f.toLine()).append("\n");
            }
        }
        return sb.toString();
    }
}
