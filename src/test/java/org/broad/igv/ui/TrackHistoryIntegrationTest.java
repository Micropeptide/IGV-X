package org.broad.igv.ui;

import org.broad.igv.track.Track;
import org.broad.igv.ui.AbstractHeadedTest;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * IGV-X: integration test for undo/redo of track-list mutations against a
 * real IGV instance.  Headed only (skipped when the test JVM is headless).
 */
public class TrackHistoryIntegrationTest extends AbstractHeadedTest {

    private static Track trackA;
    private static Track trackB;

    @BeforeClass
    public static void setUpClass() throws Exception {
        AbstractHeadedTest.setUpClass();
        IGV igv = IGV.getInstance();
        igv.newSession();

        // Use lightweight mock tracks so the test does not depend on real data files.
        trackA = new org.broad.igv.track.MockTrack("mockA");
        trackB = new org.broad.igv.track.MockTrack("mockB");
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        AbstractHeadedTest.tearDownClass();
    }

    @Test
    public void testUndoRedoAddAndRemove() {
        IGV igv = IGV.getInstance();
        TrackHistoryManager history = igv.getTrackHistory();
        history.reset();
        assertFalse(history.canUndo());

        // Add two tracks (single recorded operation)
        igv.addTracks(Arrays.asList(trackA, trackB));
        assertTrue(history.canUndo());
        assertEquals(2, igv.getAllTracks().size());

        // Undo the add -> tracks gone
        String undone = history.undo();
        assertNotNull(undone);
        assertTrue(history.canRedo());
        assertEquals(0, igv.getAllTracks().size());

        // Redo -> tracks back
        String redone = history.redo();
        assertNotNull(redone);
        assertEquals(2, igv.getAllTracks().size());
        assertTrue(history.canUndo());
    }

    @Test
    public void testUndoDeleteRestoresTracks() {
        IGV igv = IGV.getInstance();
        TrackHistoryManager history = igv.getTrackHistory();
        history.reset();

        igv.addTracks(Arrays.asList(trackA));
        assertEquals(1, igv.getAllTracks().size());
        assertTrue(history.canUndo());

        // Now remove the track via deleteTracks (recorded), then undo.
        history.reset();
        igv.deleteTracks(Collections.singleton(trackA));
        assertEquals(0, igv.getAllTracks().size());
        assertTrue(history.canUndo());

        history.undo();
        List<Track> restored = igv.getAllTracks();
        assertEquals(1, restored.size());
        assertSame(trackA, restored.get(0));
    }

    @Test
    public void testNoUndoAfterReset() {
        IGV igv = IGV.getInstance();
        igv.getTrackHistory().reset();
        assertFalse(igv.getTrackHistory().canUndo());
        assertFalse(igv.getTrackHistory().canRedo());
    }
}
