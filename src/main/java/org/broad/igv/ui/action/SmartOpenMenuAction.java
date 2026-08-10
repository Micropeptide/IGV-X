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

import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.prefs.IGVPreferences;
import org.broad.igv.prefs.PreferencesManager;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.util.FileDialogUtils;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.ui.util.RecentFiles;
import org.broad.igv.util.LongRunningTask;
import org.broad.igv.util.ResourceLocator;

import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * IGV-X unified "Open..." action.  A single entry point for both data files
 * and session files: each selected file is routed automatically (session files
 * go to loadSession(), everything else to loadTracks()).  Replaces the old
 * split where "Load from File..." rejected session files and told the user to
 * use a separate "Open Session..." item.
 */
public class SmartOpenMenuAction extends MenuAction {

    private static final Logger log = LogManager.getLogger(SmartOpenMenuAction.class);
    private final IGV igv;

    public SmartOpenMenuAction(String label, int mnemonic, IGV igv) {
        super(label, null, mnemonic);
        this.igv = igv;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        File[] files = chooseFiles();
        if (files != null && files.length > 0) {
            openFiles(files);
        }
    }

    private File[] chooseFiles() {
        File lastDirectoryFile = PreferencesManager.getPreferences().getLastTrackDirectory();
        File[] files = FileDialogUtils.chooseMultiple("Open", lastDirectoryFile, null);
        if (files != null && files.length > 0 && files[0] != null) {
            PreferencesManager.getPreferences().setLastTrackDirectory(files[0]);
        }
        igv.resetStatusMessage();
        return files;
    }

    private void openFiles(File[] files) {
        final List<File> validFiles = new ArrayList<>();
        final List<File> missingFiles = new ArrayList<>();
        for (File file : files) {
            if (!file.exists()) {
                missingFiles.add(file);
            } else {
                validFiles.add(file);
            }
        }
        if (!missingFiles.isEmpty()) {
            String msg = missingFiles.stream()
                    .map(File::getAbsolutePath)
                    .collect(Collectors.joining("\n\t", "File(s) not found: \n\t", ""));
            log.error(msg);
            MessageUtils.showMessage(msg);
        }
        if (!validFiles.isEmpty()) {
            final List<ResourceLocator> locators = ResourceLocator.getLocators(validFiles);
            final List<ResourceLocator> sessions = new ArrayList<>();
            final List<ResourceLocator> tracks = new ArrayList<>();
            for (ResourceLocator locator : locators) {
                if (RecentFiles.isSessionFile(locator.getPath())) {
                    sessions.add(locator);
                } else {
                    tracks.add(locator);
                }
            }
            // Record all opened entries in recent history
            for (ResourceLocator locator : locators) {
                RecentFiles.record(igv, locator);
            }
            // Sessions load in background; tracks use loadTracks (async internally)
            for (ResourceLocator locator : sessions) {
                LongRunningTask.submit(() -> {
                    try {
                        igv.loadSession(locator.getPath(), null);
                    } catch (Exception ex) {
                        log.error("Error opening session " + locator.getPath(), ex);
                    }
                });
            }
            if (!tracks.isEmpty()) {
                igv.addToRecentUrls(tracks);
                igv.loadTracks(tracks);
            }
        }
    }
}
