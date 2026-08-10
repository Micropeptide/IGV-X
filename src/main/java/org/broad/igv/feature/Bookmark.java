/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
 * Copyright (c) 2026 IGV-X
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
 *
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */


package org.broad.igv.feature;

import java.awt.*;

/**
 * A bookmark is a named, persistent region of interest that can be highlighted.
 * Bookmarks extend {@link RegionOfInterest} and add a label, an optional color,
 * and a persistent "highlighted" state. They are serialized into IGV-X session
 * files ({@code <Bookmarks><Bookmark .../></Bookmarks>}) and survive session
 * reload, unlike plain regions of interest.
 */
public class Bookmark extends RegionOfInterest {

    private String label;
    private Color color;
    private boolean highlighted;

    public Bookmark(String chromosomeName, int start, int end, String label) {
        this(chromosomeName, start, end, label, null);
    }

    public Bookmark(String chromosomeName, int start, int end, String label, Color color) {
        super(chromosomeName, start, end, label);
        this.label = label;
        this.color = color;   // null -> use RegionOfInterest defaults
        this.highlighted = false;
    }

    public String getLabel() {
        return label != null ? label : getDescription();
    }

    public void setLabel(String label) {
        this.label = label;
        setDescription(label);
    }

    /**
     * The bookmark color, or the class default when unset.
     */
    public Color getColor() {
        return color != null ? color : RegionOfInterest.getDefaultBackgroundColor();
    }

    public void setColor(Color color) {
        this.color = color;
    }

    public boolean isHighlighted() {
        return highlighted;
    }

    public void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
    }

    /**
     * Serialize a color as a hex string, e.g. "#FF0000". Returns null for null input.
     */
    public static String colorToString(Color c) {
        if (c == null) {
            return null;
        }
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /**
     * Parse a hex color string ("#FF0000" or "FF0000") into a Color. Returns null
     * for null/empty/invalid input so session loading never throws on a bad color.
     */
    public static Color colorFromString(String s) {
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        String hex = s.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return null;
        }
        try {
            return new Color(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
