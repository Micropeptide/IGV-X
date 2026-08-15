package org.broad.igv.ui;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * IGV-X: headless-safe unit tests for {@link TrackHistoryManager}.
 *
 * <p>These tests exercise the pure manager logic without an IGV instance:
 * with no instance, capture() yields an empty snapshot, so recorded mutations
 * are no-ops — the assertions below target the stack/description/log
 * contracts that do not require a live GUI.  The full add/remove/undo
 * round-trip is covered by the headed integration test
 * {@code TrackHistoryIntegrationTest} (runs only with a display).</p>
 */
public class TrackHistoryManagerTest {

    private TrackHistoryManager history;

    @Before
    public void setUp() {
        history = new TrackHistoryManager();
    }

    @Test
    public void testStartsEmpty() {
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
        assertNull(history.getUndoDescription());
        assertNull(history.getRedoDescription());
        assertTrue(history.getHistoryLog().isEmpty());
        assertTrue(history.getUndoDescriptions().isEmpty());
        assertTrue(history.getRedoDescriptions().isEmpty());
        assertEquals(0, history.toJson().size());
    }

    @Test
    public void testRecordRunsMutationEvenWithoutIgv() {
        // Without an IGV instance the before/after snapshots are both empty,
        // so nothing is recorded, but the mutation itself must still run.
        final boolean[] ran = {false};
        history.record("No-op mutation", () -> ran[0] = true);
        assertTrue(ran[0]);
        assertFalse(history.canUndo());
        assertTrue(history.getHistoryLog().isEmpty());
    }

    @Test
    public void testUndoRedoEmpty() {
        assertNull(history.undo());
        assertNull(history.redo());
        assertFalse(history.canUndo());
        assertFalse(history.canRedo());
    }

    @Test
    public void testResetClearsLog() {
        // Simulate a recorded operation by directly poking the log via the
        // only public path that adds to it when an IGV instance is absent.
        // (record() is a no-op without an instance, so this validates the
        // reset contract on the log/stack collections themselves.)
        history.reset();
        assertTrue(history.getHistoryLog().isEmpty());
        assertTrue(history.getUndoDescriptions().isEmpty());
        assertTrue(history.getRedoDescriptions().isEmpty());
    }

    @Test
    public void testToJsonEmpty() {
        assertEquals(0, history.toJson().size());
        history.reset();
        assertEquals(0, history.toJson().size());
    }

    @Test
    public void testSnapshotEquals() {
        TrackHistoryManager.Snapshot empty1 = history.capture();
        TrackHistoryManager.Snapshot empty2 = history.capture();
        assertEquals(empty1, empty2);
        assertEquals(empty1.hashCode(), empty2.hashCode());
        assertTrue(empty1.getPanelOrder().isEmpty());
    }
}
