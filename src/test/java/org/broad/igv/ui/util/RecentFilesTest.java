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

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests for the IGV-X recent-files helpers:
 * session-vs-track routing must never hand a session to loadTracks (or a
 * track to loadSession), and history lists must prune stale local paths.
 */
public class RecentFilesTest {

    @Test
    public void sessionFileExtensionsAreRecognized() {
        assertTrue(RecentFiles.isSessionFile("/data/foo.xml"));
        assertTrue(RecentFiles.isSessionFile("C:\\data\\foo.xml"));
        assertTrue(RecentFiles.isSessionFile("/data/foo.session"));
        assertTrue(RecentFiles.isSessionFile("/data/foo.session.txt"));
        assertTrue(RecentFiles.isSessionFile("/data/foo.php"));
        assertTrue(RecentFiles.isSessionFile("/data/foo.php3"));
        // Case-insensitive (IGV-X convention)
        assertTrue(RecentFiles.isSessionFile("/data/FOO.XML"));
    }

    @Test
    public void dataFileExtensionsAreNotSessions() {
        assertFalse(RecentFiles.isSessionFile("/data/reads.bam"));
        assertFalse(RecentFiles.isSessionFile("/data/meth.bigWig"));
        assertFalse(RecentFiles.isSessionFile("/data/meth.bw"));
        assertFalse(RecentFiles.isSessionFile("/data/variants.vcf"));
        assertFalse(RecentFiles.isSessionFile("/data/peaks.bed"));
        assertFalse(RecentFiles.isSessionFile("/data/genes.gff"));
        assertFalse(RecentFiles.isSessionFile("/data/ref.fa"));
        assertFalse(RecentFiles.isSessionFile(null));
        assertFalse(RecentFiles.isSessionFile(""));
    }

    @Test
    public void kindOfRoutesByExtension() {
        assertEquals(RecentFiles.Kind.SESSION, RecentFiles.kindOf("/data/session.xml"));
        assertEquals(RecentFiles.Kind.SESSION, RecentFiles.kindOf("/data/session.session"));
        assertEquals(RecentFiles.Kind.TRACK, RecentFiles.kindOf("/data/track.bigWig"));
        assertEquals(RecentFiles.Kind.TRACK, RecentFiles.kindOf("/data/reads.bam"));
    }

    @Test
    public void pruneMissingFilesDropsMissingLocalButKeepsRemoteAndExisting() throws IOException {
        File existing = File.createTempFile("igvx_recent", ".bam");
        try {
            File missing = new File(existing.getParentFile(), "igvx_recent_missing_" + System.nanoTime() + ".bam");
            assertFalse(missing.exists());

            List<String> input = Arrays.asList(
                    existing.getAbsolutePath(),
                    missing.getAbsolutePath(),
                    "https://example.org/remote/track.bigWig",
                    "   ",
                    null,
                    "ftp://ftp.example.org/x.bam"
            );

            List<String> pruned = RecentFiles.pruneMissingFiles(input);
            assertTrue("existing local file must be kept", pruned.contains(existing.getAbsolutePath()));
            assertFalse("missing local file must be pruned", pruned.contains(missing.getAbsolutePath()));
            assertTrue("remote URL must be kept", pruned.contains("https://example.org/remote/track.bigWig"));
            assertTrue("ftp URL must be kept", pruned.contains("ftp://ftp.example.org/x.bam"));
            assertEquals(3, pruned.size());
        } finally {
            Files.deleteIfExists(existing.toPath());
        }
    }

    @Test
    public void pruneMissingFilesHandlesEmptyAndNull() {
        assertTrue(RecentFiles.pruneMissingFiles(null).isEmpty());
        assertTrue(RecentFiles.pruneMissingFiles(Collections.emptyList()).isEmpty());
    }
}
