/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2026 Broad Institute, IGV-X contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 */
package org.broad.igv.diagnostic;

/**
 * IGV-X: categories used by the Diagnose Track/Session subsystem. Each finding
 * belongs to exactly one category so the report can group root causes.
 */
public enum DiagnosticCategory {
    RESOURCE("Resource"),
    FILE_FORMAT("File format"),
    INDEX("Index"),
    GENOME("Genome / chromosome"),
    NETWORK("Network"),
    MEMORY("Memory"),
    RENDERING("Rendering");

    private final String label;

    DiagnosticCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
