/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.broad.igv.ui.action;

//~--- non-JDK imports --------------------------------------------------------

import org.broad.igv.DirectoryManager;
import org.broad.igv.logging.*;
import org.broad.igv.prefs.Constants;
import org.broad.igv.prefs.PreferencesManager;
import org.broad.igv.session.Session;
import org.broad.igv.session.SessionWriter;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.UIConstants;
import org.broad.igv.ui.WaitCursorManager;
import org.broad.igv.ui.util.FileDialogUtils;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;

/**
 * @author jrobinso
 */
public class SaveSessionMenuAction extends MenuAction {

    static Logger log = LogManager.getLogger(SaveSessionMenuAction.class);
    IGV igv;

    /**
     *
     *
     * @param label
     * @param mnemonic
     * @param igv
     */
    public SaveSessionMenuAction(String label, int mnemonic, IGV igv) {
        super(label, null, mnemonic);
        this.igv = igv;
    }

    /**
     * Method description
     *
     * @param e
     */
    @Override
    public void actionPerformed(ActionEvent e) {

        File sessionFile = null;

        String currentSessionFilePath = igv.getSession().getPath();

        // IGV-X: save-without-prompt. When this session already has a real path
        // (and is not the autosave file), write straight to it instead of
        // showing the file chooser every time. The chooser is only shown for
        // new sessions (Save As) or sessions that live in the autosave directory.
        File parentDir = currentSessionFilePath == null ? null : new File(new File(currentSessionFilePath).getParent());
        boolean hasRealPath = currentSessionFilePath != null
                && !parentDir.equals(DirectoryManager.getAutosaveDirectory());
        if (hasRealPath) {
            sessionFile = new File(currentSessionFilePath);

            // IGV-X: this is the silent (no file chooser) overwrite path -- the
            // one case where the user never sees a native "replace this file?"
            // prompt. Warn if the file changed on disk since IGV-X last loaded
            // or saved it (hand edit, git checkout, a cloud-sync conflict copy,
            // etc), so a silent save can't clobber it without at least asking.
            long expected = igv.getLastKnownSessionFileMtime();
            if (expected != 0 && sessionFile.exists() && sessionFile.lastModified() != expected) {
                int choice = JOptionPane.showConfirmDialog(igv.getMainFrame(),
                        "This session file changed on disk since it was opened " +
                                "(edited elsewhere, or a sync conflict copy).\n\n" +
                                "Overwrite it anyway?",
                        "Session File Changed", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) {
                    igv.resetStatusMessage();
                    return;
                }
            }
        } else {
            // Get the parent dir of the session file so we can check if it's in the autosave directory

            // If the filepath is null or the file is in the autosave dir, use the default session file name
            String initFile = currentSessionFilePath == null ?
                    UIConstants.DEFAULT_SESSION_FILE : currentSessionFilePath;
            sessionFile = FileDialogUtils.chooseFile("Save Session",
                    PreferencesManager.getPreferences().getLastTrackDirectory(),
                    new File(initFile),
                    FileDialogUtils.SAVE);
        }

        if (sessionFile == null) {
            igv.resetStatusMessage();
            return;
        }

        String filePath = sessionFile.getAbsolutePath();
        if (!hasRealPath && !filePath.toLowerCase().endsWith(".xml")) {
            sessionFile = new File(filePath + ".xml");
        }

        igv.setStatusBarMessage("Saving session to " + sessionFile.getAbsolutePath());

        final File sf = sessionFile;
        WaitCursorManager.CursorToken token = WaitCursorManager.showWaitCursor();
        try {
            igv.saveSession(sf);

        } catch (Exception e2) {
            JOptionPane.showMessageDialog(igv.getMainFrame(), "There was an error writing to " + sf.getName() + "(" + e2.getMessage() + ")");
            log.error("Failed to save session <br/>" + e2.getMessage(), e2);
        } finally {
            WaitCursorManager.removeWaitCursor(token);
            igv.resetStatusMessage();
        }
    }

}
