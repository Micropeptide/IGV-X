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

package org.broad.igv.ui.util;

/**
 * IGV-X: options that control snapshot/export rendering. These are read by the
 * snapshot pipeline (SnapshotUtilities, DataPanelPainter, TrackPanelScrollPane)
 * during an export and reset after each export completes.
 */
public class SnapshotOptions {

    /**
     * Export only tracks that are currently selected in the UI.
     */
    private static boolean selectedTracksOnly = false;

    /**
     * Output DPI for raster exports. 96 = 1x screen resolution.
     */
    private static int dpi = 96;

    /**
     * Publication mode: white background, no ROI bars, no group gaps, full height.
     */
    private static boolean publicationMode = false;

    private SnapshotOptions() {
    }

    public static boolean isSelectedTracksOnly() {
        return selectedTracksOnly;
    }

    public static void setSelectedTracksOnly(boolean selectedTracksOnly) {
        SnapshotOptions.selectedTracksOnly = selectedTracksOnly;
    }

    public static int getDpi() {
        return dpi;
    }

    public static void setDpi(int dpi) {
        SnapshotOptions.dpi = dpi > 0 ? dpi : 96;
    }

    public static boolean isPublicationMode() {
        return publicationMode;
    }

    public static void setPublicationMode(boolean publicationMode) {
        SnapshotOptions.publicationMode = publicationMode;
    }

    public static void reset() {
        selectedTracksOnly = false;
        dpi = 96;
        publicationMode = false;
    }
}
