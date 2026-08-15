/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
 * Copyright (c) 2026 IGV-X contributors
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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.broad.igv.ui.util;

import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.session.SessionReader;
import org.broad.igv.ui.IGV;
import org.broad.igv.util.HttpUtils;
import org.broad.igv.util.LongRunningTask;
import org.broad.igv.util.ResourceLocator;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Central helpers for the IGV-X recent-files history: routing a recent entry
 * to the correct IGV code path (session vs track), recording entries from
 * every open path, and pruning stale local entries.
 * <p>
 * IGV-X: sessions must NEVER be handed to loadTracks() (they would be parsed
 * as data); tracks must never be handed to loadSession().  All UI surfaces
 * that open a recent entry should go through {@link #open(IGV, ResourceLocator)}
 * so the routing stays correct in one place.
 */
public class RecentFiles {

    private static final Logger log = LogManager.getLogger(RecentFiles.class);

    public enum Kind {
        TRACK,
        SESSION
    }

    /**
     * True for anything IGV can load as a session.  Delegates to the canonical
     * {@link org.broad.igv.session.SessionMetadata#isSessionFile(String)} test,
     * which covers stock IGV sessions (.xml, .php, .php3) and IGV-X native
     * sessions (.igvx, .session, .session.txt, .idxsession, .idxsession.txt).
     */
    public static boolean isSessionFile(String path) {
        return org.broad.igv.session.SessionMetadata.isSessionFile(path);
    }

    /**
     * Session files must be routed to loadSession(); everything else is a track.
     */
    public static Kind kindOf(String path) {
        return isSessionFile(path) ? Kind.SESSION : Kind.TRACK;
    }

    /**
     * Open a recent entry through the correct IGV code path, keeping history
     * fresh (the entry moves to the front of its list).  Safe to call from the
     * EDT: session loads are dispatched to a background task, track loads use
     * IGV.loadTracks which is already asynchronous.
     */
    public static void open(IGV igv, ResourceLocator locator) {
        if (igv == null || locator == null) {
            return;
        }
        if (isSessionFile(locator.getPath())) {
            LongRunningTask.submit(() -> {
                try {
                    igv.loadSession(locator.getPath(), null);
                } catch (Exception e) {
                    log.error("Error opening recent session " + locator.getPath(), e);
                }
            });
        } else {
            List<ResourceLocator> resource = List.of(locator);
            igv.addToRecentUrls(resource);
            igv.loadTracks(resource);
        }
    }

    /**
     * Record a file as recently opened.  Sessions go to the recent-sessions
     * list, everything else to the recent-URLs list.  Idempotent for sessions
     * (the backing set de-duplicates); used by load paths that do not already
     * record history (drag-and-drop, command line, batch commands).
     */
    public static void record(IGV igv, ResourceLocator locator) {
        if (igv == null || locator == null) {
            return;
        }
        if (isSessionFile(locator.getPath())) {
            igv.getRecentSessionList().add(locator.getPath());
        } else {
            igv.addToRecentUrls(List.of(locator));
        }
    }

    /**
     * Filter a collection of paths down to entries that should still be shown:
     * non-null, non-blank, and either a remote URL or an existing local file.
     * Used by the welcome panel and any surface that lists history, so stale
     * local paths do not accumulate in the UI.
     */
    public static List<String> pruneMissingFiles(Collection<String> paths) {
        List<String> result = new ArrayList<>();
        if (paths == null) {
            return result;
        }
        for (String path : paths) {
            if (path == null) {
                continue;
            }
            String trimmed = path.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (HttpUtils.isRemoteURL(trimmed) || new File(trimmed).exists()) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
