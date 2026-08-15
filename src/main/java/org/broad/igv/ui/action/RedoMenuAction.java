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

package org.broad.igv.ui.action;

import org.broad.igv.ui.IGV;
import org.broad.igv.ui.TrackHistoryManager;

import java.awt.event.ActionEvent;

/**
 * IGV-X: re-apply the most recently undone track-list mutation
 * (Cmd/Ctrl+Shift+Z).
 */
public class RedoMenuAction extends MenuAction {

    private final IGV igv;

    public RedoMenuAction(String label, int mnemonic, IGV igv) {
        super(label, null, mnemonic);
        this.igv = igv;
        updateEnabledState();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        TrackHistoryManager history = igv.getTrackHistory();
        String description = history.redo();
        if (description != null) {
            igv.setStatusBarMessage("Redo " + description);
        }
        updateEnabledState();
    }

    /** Refresh the enabled state and label from the history manager. */
    public void updateEnabledState() {
        TrackHistoryManager history = igv.getTrackHistory();
        String desc = history.getRedoDescription();
        setEnabled(desc != null);
        putValue(NAME, desc == null ? "Redo" : "Redo " + desc);
    }
}
