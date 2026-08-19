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

package org.broad.igv.ui;

import com.google.gson.JsonArray;
import org.broad.igv.event.DataLoadedEvent;
import org.broad.igv.event.GenomeChangeEvent;
import org.broad.igv.event.IGVEvent;
import org.broad.igv.event.IGVEventBus;
import org.broad.igv.event.IGVEventObserver;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.track.BlatTrack;
import org.broad.igv.track.DataTrack;
import org.broad.igv.track.FeatureTrack;
import org.broad.igv.track.SequenceTrack;
import org.broad.igv.track.Track;
import org.broad.igv.ui.panel.FrameManager;
import org.broad.igv.ui.panel.MainPanel;
import org.broad.igv.ui.panel.TrackPanel;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * IGV-X: undo/redo for track-list mutations.
 *
 * <p>Every user-level mutation that changes which tracks are shown, their panel
 * membership, their order, or their per-track attributes (name, color, height,
 * visible) is wrapped with {@link #record(String, Runnable)}.  The manager
 * captures a full snapshot of the panel/track state before and after the
 * mutation; undo/redo re-applies those snapshots.</p>
 *
 * <p>Track objects are referenced (not copied) by snapshots, so undo of a
 * delete re-inserts the same object.  {@link org.broad.igv.track.AbstractTrack#unload()}
 * only unsubscribes the object from the event bus, it does not clear its data,
 * so a restored track is still fully usable once re-subscribed (see
 * {@link #resubscribe(Track)}).</p>
 *
 * <p>The history is session-scoped: it is cleared on new/reset session.  A
 * human-readable log of operations is available via {@link #getHistoryLog()}
 * and is persisted in the .igvx.json companion by the session writer.</p>
 */
public class TrackHistoryManager implements IGVEventObserver {

    private static final Logger log = LogManager.getLogger(TrackHistoryManager.class);

    /** Maximum number of undoable operations kept per session. */
    public static final int MAX_UNDO = 100;

    private final Deque<Entry> undoStack = new ArrayDeque<>();
    private final Deque<Entry> redoStack = new ArrayDeque<>();
    private final List<String> historyLog = new ArrayList<>();

    private boolean applying = false;
    private boolean recording = false;
    private String lastGenomeId;
    private Snapshot lastSnapshot;

    public TrackHistoryManager() {
        IGVEventBus.getInstance().subscribe(GenomeChangeEvent.class, this);
    }

    /**
     * Record a mutation: capture before/after snapshots and push an undo entry.
     * No-op operations (state unchanged) are not recorded.  The mutation always
     * runs, even when IGV is not initialized (headless safety).  Nested calls
     * (a mutation that internally performs another recorded mutation) are
     * collapsed: only the outermost record() creates an undo entry.
     */
    public void record(String description, Runnable mutation) {
        if (recording || applying) {
            mutation.run();
            return;
        }
        recording = true;
        try {
            Snapshot before = capture();
            mutation.run();
            Snapshot after = capture();
            if (before.equals(after)) {
                lastSnapshot = after;
                lastGenomeId = after.genomeId;
                return;
            }
            pushEntry(description, before, after);
            lastSnapshot = after;
            lastGenomeId = after.genomeId;
        } finally {
            recording = false;
        }
    }

    private void pushEntry(String description, Snapshot before, Snapshot after) {
        undoStack.push(new Entry(description, before, after));
        while (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast();
        }
        redoStack.clear();
        historyLog.add(description);
        if (historyLog.size() > MAX_UNDO * 4) {
            historyLog.subList(0, historyLog.size() - MAX_UNDO * 4).clear();
        }
        if (IGV.hasInstance()) {
            IGV.getInstance().setSessionModified(true);
        }
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public String getUndoDescription() {
        Entry e = undoStack.peek();
        return e == null ? null : e.description;
    }

    public String getRedoDescription() {
        Entry e = redoStack.peek();
        return e == null ? null : e.description;
    }

    /**
     * Apply the most recent undo entry.  Returns the description of the
     * operation that was undone, or null when there is nothing to undo.
     */
    public String undo() {
        if (undoStack.isEmpty()) {
            return null;
        }
        Entry e = undoStack.peek();
        if (!applySnapshot(e.before)) {
            return null;
        }
        undoStack.pop();
        redoStack.push(e);
        return e.description;
    }

    /**
     * Re-apply the most recently undone operation.  Returns the description of
     * the operation that was redone, or null when there is nothing to redo.
     */
    public String redo() {
        if (redoStack.isEmpty()) {
            return null;
        }
        Entry e = redoStack.peek();
        if (!applySnapshot(e.after)) {
            return null;
        }
        redoStack.pop();
        undoStack.push(e);
        return e.description;
    }

    /** Clear all undo/redo state and the operation log. */
    public void reset() {
        undoStack.clear();
        redoStack.clear();
        historyLog.clear();
    }

    /** Read-only copy of the operation log (oldest first). */
    public List<String> getHistoryLog() {
        return new ArrayList<>(historyLog);
    }

    /** Descriptions of currently-undoable operations, oldest first. */
    public List<String> getUndoDescriptions() {
        List<String> out = new ArrayList<>();
        for (Entry e : undoStack) {
            out.add(e.description);
        }
        Collections.reverse(out);
        return out;
    }

    /** Descriptions of currently-redoable operations, newest first. */
    public List<String> getRedoDescriptions() {
        List<String> out = new ArrayList<>();
        for (Entry e : redoStack) {
            out.add(e.description);
        }
        return out;
    }

    // ---------------------------------------------------------------------
    // Snapshot capture / apply
    // ---------------------------------------------------------------------

    /**
     * Capture the full panel/track state.  Safe to call when IGV is not
     * initialized (returns an empty snapshot), which keeps unit tests headless.
     */
    public Snapshot capture() {
        String genomeId = GenomeManager.getInstance().getGenomeId();
        if (!IGV.hasInstance()) {
            return new Snapshot(Collections.emptyList(), Collections.emptyMap(), null, genomeId);
        }
        IGV igv = IGV.getInstance();
        List<TrackPanel> panels = igv.getTrackPanels();
        List<String> panelOrder = new ArrayList<>(panels.size());
        Map<String, List<TrackState>> panelTracks = new LinkedHashMap<>();
        for (TrackPanel tp : panels) {
            String name = tp.getName();
            panelOrder.add(name);
            List<TrackState> states = new ArrayList<>();
            for (Track t : tp.getTracks()) {
                states.add(TrackState.of(t));
            }
            panelTracks.put(name, states);
        }
        return new Snapshot(panelOrder, panelTracks, igv.getGroupByAttribute(), genomeId);
    }

    @Override
    public void receiveEvent(IGVEvent event) {
        if (!(event instanceof GenomeChangeEvent genomeEvent) || applying) {
            return;
        }
        Genome genome = genomeEvent.genome();
        String newGenomeId = genome == null ? null : genome.getId();
        if (Objects.equals(newGenomeId, lastGenomeId)) {
            lastSnapshot = capture();
            return;
        }

        // GenomeManager resets the current session before publishing this event.
        // Keep the prior panel snapshot so undo can restore the tracks belonging
        // to the previous genome rather than only the newly-reset layout.
        Snapshot after = capture();
        if (lastSnapshot != null && lastGenomeId != null) {
            Snapshot before = lastSnapshot.withGenomeId(lastGenomeId);
            pushEntry("Load genome: " + (genome == null ? newGenomeId : genome.getDisplayName()), before, after);
        }
        lastSnapshot = after;
        lastGenomeId = newGenomeId;
    }

    /**
     * Rebuild the panel/track state to match the given snapshot.  Uses the
     * low-level TrackPanel/MainPanel operations directly so that applying a
     * snapshot never re-enters {@link #record}.
     */
    public void apply(Snapshot target) {
        applySnapshot(target);
    }

    private boolean applySnapshot(Snapshot target) {
        if (!IGV.hasInstance()) {
            return true;
        }
        applying = true;
        try {
            IGV igv = IGV.getInstance();

            String currentGenomeId = GenomeManager.getInstance().getGenomeId();
            if (!Objects.equals(target.genomeId, currentGenomeId)) {
                if (target.genomeId == null) {
                    log.warn("Cannot restore undo snapshot without a genome ID while genome " + currentGenomeId + " is loaded");
                    return false;
                }
                try {
                    boolean loaded = GenomeManager.getInstance().loadGenomeById(target.genomeId);
                    if (!loaded || !Objects.equals(target.genomeId, GenomeManager.getInstance().getGenomeId())) {
                        log.warn("Unable to restore genome for undo snapshot: " + target.genomeId);
                        return false;
                    }
                } catch (IOException e) {
                    log.warn("Unable to restore genome for undo snapshot: " + target.genomeId, e);
                    return false;
                }
            }

            // 1. Remove panels that should not exist, clearing their tracks first.
            Set<String> targetPanelNames = new LinkedHashSet<>(target.panelOrder);
            for (TrackPanel tp : new ArrayList<>(igv.getTrackPanels())) {
                if (!targetPanelNames.contains(tp.getName())) {
                    tp.removeAllTracks();
                    igv.removeDataPanel(tp.getName());
                }
            }

            // 2. For each target panel: ensure it exists, then rebuild its
            //    ordered track list from the snapshot (same object references).
            for (String name : target.panelOrder) {
                TrackPanel tp = igv.getTrackPanel(name);
                List<TrackState> states = target.panelTracks.get(name);
                if (states == null) {
                    states = Collections.emptyList();
                }
                tp.removeAllTracks();
                for (TrackState ts : states) {
                    tp.addTrack(ts.track);
                    resubscribe(ts.track);
                    ts.restore();
                }
            }

            // 3. Restore panel order.
            MainPanel mainPanel = igv.getMainPanel();
            if (mainPanel != null) {
                mainPanel.reorderPanels(new ArrayList<>(target.panelOrder));
            }

            // 4. Restore grouping attribute (directly, to avoid re-recording).
            if (target.groupByAttribute != null) {
                igv.getSession().setGroupByAttribute(target.groupByAttribute);
                igv.resetGroups();
            }

            igv.revalidateTrackPanels();
            igv.repaint();
            igv.setSessionModified(true);
            lastSnapshot = capture();
            lastGenomeId = GenomeManager.getInstance().getGenomeId();
            return true;
        } finally {
            applying = false;
        }
    }

    /**
     * Re-subscribe a restored track to the event-bus events its constructor
     * originally subscribed to.  AbstractTrack.unload() unsubscribes observers,
     * so undo-of-delete must put them back for the track to keep working
     * (frame changes, data-loaded notifications, etc.).
     */
    private static void resubscribe(Track t) {
        if (!(t instanceof IGVEventObserver)) {
            return;
        }
        IGVEventObserver observer = (IGVEventObserver) t;
        if (t instanceof DataTrack || t instanceof SequenceTrack) {
            IGVEventBus.getInstance().subscribe(FrameManager.ChangeEvent.class, observer);
        }
        if (t instanceof FeatureTrack || t instanceof BlatTrack) {
            IGVEventBus.getInstance().subscribe(DataLoadedEvent.class, observer);
        }
    }

    /** True while a snapshot is being applied (suppresses re-recording). */
    public boolean isApplying() {
        return applying;
    }

    // ---------------------------------------------------------------------
    // Snapshot value classes
    // ---------------------------------------------------------------------

    /** An undoable operation: description plus before/after snapshots. */
    static class Entry {
        final String description;
        final Snapshot before;
        final Snapshot after;

        Entry(String description, Snapshot before, Snapshot after) {
            this.description = description;
            this.before = before;
            this.after = after;
        }
    }

    /** Full panel/track layout at one point in time. */
    public static class Snapshot {
        final List<String> panelOrder;
        final Map<String, List<TrackState>> panelTracks;
        final String groupByAttribute;
        final String genomeId;

        Snapshot(List<String> panelOrder, Map<String, List<TrackState>> panelTracks, String groupByAttribute, String genomeId) {
            this.panelOrder = new ArrayList<>(panelOrder);
            this.panelTracks = new LinkedHashMap<>();
            panelTracks.forEach((name, tracks) -> this.panelTracks.put(name, new ArrayList<>(tracks)));
            this.groupByAttribute = groupByAttribute;
            this.genomeId = genomeId;
        }

        Snapshot withGenomeId(String id) {
            return new Snapshot(panelOrder, panelTracks, groupByAttribute, id);
        }

        public String getGenomeId() {
            return genomeId;
        }

        public List<String> getPanelOrder() {
            return Collections.unmodifiableList(panelOrder);
        }

        public List<TrackState> getTracksForPanel(String panelName) {
            List<TrackState> states = panelTracks.get(panelName);
            return states == null ? Collections.emptyList() : Collections.unmodifiableList(states);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Snapshot)) return false;
            Snapshot that = (Snapshot) o;
            if (!panelOrder.equals(that.panelOrder)) return false;
            if (!Objects.equals(genomeId, that.genomeId)) return false;
            if (groupByAttribute == null ? that.groupByAttribute != null
                    : !groupByAttribute.equals(that.groupByAttribute)) return false;
            if (panelTracks.size() != that.panelTracks.size()) return false;
            for (Map.Entry<String, List<TrackState>> e : panelTracks.entrySet()) {
                List<TrackState> other = that.panelTracks.get(e.getKey());
                if (other == null || !e.getValue().equals(other)) return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(panelOrder, genomeId, groupByAttribute, panelTracks);
        }
    }

    /** A track plus the per-track attributes the history restores. */
    public static class TrackState {
        final Track track;
        final String name;
        final Color color;
        final int height;
        final boolean visible;

        TrackState(Track track, String name, Color color, int height, boolean visible) {
            this.track = track;
            this.name = name;
            this.color = color;
            this.height = height;
            this.visible = visible;
        }

        static TrackState of(Track t) {
            return new TrackState(t, t.getName(), t.getColor(), t.getHeight(), t.isVisible());
        }

        /** Restore the captured attribute values onto the live track. */
        void restore() {
            track.setName(name);
            if (color != null) {
                track.setColor(color);
            }
            track.setHeight(height);
            track.setVisible(visible);
        }

        public Track getTrack() {
            return track;
        }

        public String getName() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TrackState)) return false;
            TrackState that = (TrackState) o;
            if (track != that.track) return false;
            if (height != that.height) return false;
            if (visible != that.visible) return false;
            if (name == null ? that.name != null : !name.equals(that.name)) return false;
            return color == null ? that.color == null : color.equals(that.color);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(track);
        }
    }

    // ---------------------------------------------------------------------
    // History log persistence (used by the .igvx.json companion writer)
    // ---------------------------------------------------------------------

    /** Serialize the operation log into a JSON array (oldest first). */
    public JsonArray toJson() {
        JsonArray arr = new JsonArray();
        for (String s : historyLog) {
            arr.add(s);
        }
        return arr;
    }
}
