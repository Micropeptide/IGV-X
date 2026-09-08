/*
 * The MIT License (MIT)
 *
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

import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.prefs.PreferencesManager;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.WaitCursorManager;
import org.broad.igv.ui.util.FileDialogUtils;
import org.broad.igv.ui.util.MessageUtils;
import org.broad.igv.ui.util.UIUtilities;
import org.broad.igv.util.FileUtils;
import org.broad.igv.util.LongRunningTask;
import org.broad.igv.util.ResourceLocator;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * IGV-X: "Move Session + Data Files Into Folder..." -- copies the current
 * session's file and every local data file it references (plus each
 * resource's index/coverage/mapping companion file, if any) into a single
 * chosen folder, and writes a copy of the session there with paths relative
 * to that folder. Turns a session whose tracks are scattered across several
 * directories into one self-contained, movable/zippable bundle.
 * <p>
 * This never touches the originals (copy, not move, despite the menu label
 * matching the roadmap request verbatim -- "moving" the working session
 * itself out from under a running analysis is riskier than it's worth, and
 * copying gets the same portability result) and never mutates the currently
 * open session beyond a short-lived, always-restored change to each
 * {@link ResourceLocator}'s path fields while the new copy is being written
 * (so {@link org.broad.igv.session.SessionWriter} computes paths relative to
 * the new bundle instead of the original scattered locations).
 */
public class MoveSessionToFolderMenuAction extends MenuAction {

    private static final Logger log = LogManager.getLogger(MoveSessionToFolderMenuAction.class);
    private final IGV igv;

    /**
     * The file-bearing fields of a {@link ResourceLocator}, named once so
     * both "which files does this resource reference" (collecting files to
     * copy) and "repoint this resource's fields at their copies" (retarget/
     * restore around the save) enumerate the exact same set -- adding a new
     * file-bearing field to ResourceLocator only means adding one entry here,
     * not keeping two hand-written enumerations in sync.
     */
    private static final List<LocatorField> LOCATOR_FIELDS = List.of(
            new LocatorField(ResourceLocator::getPath, ResourceLocator::setPath),
            new LocatorField(ResourceLocator::getIndexPath, ResourceLocator::setIndexPath),
            new LocatorField(ResourceLocator::getCoverage, ResourceLocator::setCoverage),
            new LocatorField(ResourceLocator::getMappingPath, ResourceLocator::setMappingPath)
    );

    private record LocatorField(Function<ResourceLocator, String> getter, BiConsumer<ResourceLocator, String> setter) {
    }

    public MoveSessionToFolderMenuAction(String label, int mnemonic, IGV igv) {
        super(label, null, mnemonic);
        this.igv = igv;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String currentSessionPath = igv.getSession().getPath();
        if (currentSessionPath == null) {
            MessageUtils.showMessage("Save this session first (File > Save Session), then use " +
                    "\"Move Session + Data Files Into Folder...\".");
            return;
        }
        File currentSessionFile = new File(currentSessionPath);

        Set<ResourceLocator> locators = igv.getDataResourceLocators();
        LinkedHashSet<File> sourceFiles = new LinkedHashSet<>();
        // This runs on the EDT (menu actions do), and File.exists()/isFile()
        // for a resource on a slow network/cloud-synced mount can block
        // noticeably -- a wait cursor here at least gives feedback instead of
        // an unexplained freeze before the confirmation dialog appears.
        WaitCursorManager.CursorToken scanToken = WaitCursorManager.showWaitCursor();
        try {
            for (ResourceLocator rl : locators) {
                for (LocatorField field : LOCATOR_FIELDS) {
                    addIfLocalExisting(sourceFiles, field.getter().apply(rl));
                }
            }
        } finally {
            WaitCursorManager.removeWaitCursor(scanToken);
        }

        String genomeId = GenomeManager.getInstance().getGenomeId();
        boolean genomeIsLocalFile = genomeId != null && new File(genomeId).isAbsolute() && new File(genomeId).exists();

        File destDir = FileDialogUtils.chooseDirectory("Move Session + Data Files Into Folder",
                PreferencesManager.getPreferences().getLastTrackDirectory());
        if (destDir == null) {
            return;
        }

        StringBuilder confirm = new StringBuilder();
        confirm.append("<html><body style='width: 340px'>");
        confirm.append("Copy the session and <b>").append(sourceFiles.size())
                .append("</b> data file(s) into:<br><br><tt>").append(destDir.getAbsolutePath()).append("</tt><br><br>");
        if (genomeIsLocalFile) {
            confirm.append("<b>Note:</b> the genome reference (").append(new File(genomeId).getName())
                    .append(") is a local file and will <b>not</b> be copied -- only bundled/registered genomes " +
                            "stay portable automatically.<br><br>");
        }
        confirm.append("Your original files are not moved or modified.</body></html>");

        int result = JOptionPane.showConfirmDialog(igv.getMainFrame(), confirm.toString(),
                "Move Session + Data Files Into Folder", JOptionPane.OK_CANCEL_OPTION);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        WaitCursorManager.CursorToken token = WaitCursorManager.showWaitCursor();
        LongRunningTask.submit(() -> {
            try {
                doMove(currentSessionFile, destDir, sourceFiles, locators);
            } finally {
                UIUtilities.invokeOnEventThread(() -> WaitCursorManager.removeWaitCursor(token));
            }
        });
    }

    private static void addIfLocalExisting(Set<File> sourceFiles, String path) {
        if (path == null || path.isEmpty() || path.equals(".") || FileUtils.isRemote(path)) {
            return;
        }
        File f = new File(path);
        if (f.isAbsolute() && f.exists() && f.isFile()) {
            sourceFiles.add(f);
        }
    }

    private void doMove(File currentSessionFile, File destDir, Set<File> sourceFiles, Set<ResourceLocator> locators) {

        if (!destDir.exists() && !destDir.mkdirs()) {
            UIUtilities.invokeOnEventThread(() ->
                    MessageUtils.showMessage("Could not create folder: " + destDir.getAbsolutePath()));
            return;
        }

        // Copy every source file into destDir, disambiguating filename collisions
        // (common with WGBS-style naming, where two different source folders can
        // easily both contain e.g. "col0_CG.bw"). Map from the ORIGINAL absolute
        // path string (as it appears in a ResourceLocator field) to the new
        // absolute path of the copy, so every locator field referencing that file
        // -- main path, index, coverage, mapping -- gets rewritten consistently
        // even if it was shared or duplicated across resources.
        Map<String, String> copiedTo = new LinkedHashMap<>();
        Set<String> usedNames = new LinkedHashSet<>();
        // Reserve the session file's own name up front so a same-named data
        // resource (e.g. a resource that happens to be called the same as the
        // session, or a coincidental name collision) gets disambiguated
        // instead -- otherwise it would be copied to that name first and then
        // silently overwritten when the session file itself is written below.
        usedNames.add(currentSessionFile.getName());
        List<String> failures = new ArrayList<>();
        int copiedCount = 0;

        for (File source : sourceFiles) {
            String destName = uniqueName(source.getName(), usedNames);
            File dest = new File(destDir, destName);
            try {
                if (!source.getCanonicalFile().equals(dest.getCanonicalFile())) {
                    // COPY_ATTRIBUTES preserves the original last-modified time, so the
                    // new "file changed since session save" check (below) doesn't
                    // immediately flag every file this action itself just copied.
                    Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.COPY_ATTRIBUTES);
                }
                copiedTo.put(source.getAbsolutePath(), dest.getAbsolutePath());
                copiedCount++;
            } catch (IOException ex) {
                log.error("Error copying " + source.getAbsolutePath() + " to " + dest.getAbsolutePath(), ex);
                failures.add(source.getName() + ": " + ex.getMessage());
            }
        }

        // Temporarily point each locator's path fields at the new copies (only
        // where the copy actually succeeded) so SessionWriter computes paths
        // relative to destDir; always restored in the finally block below,
        // regardless of outcome, so the live session is never left mutated.
        List<Runnable> restorers = new ArrayList<>();
        for (ResourceLocator rl : locators) {
            for (LocatorField field : LOCATOR_FIELDS) {
                restorers.add(retarget(rl, field, copiedTo));
            }
        }

        File newSessionFile = new File(destDir, currentSessionFile.getName());
        Exception saveError = null;
        try {
            // IGV-X: write the copy with the plain SessionWriter, NOT
            // igv.saveSession(File) -- that method also does
            // session.setPath(...), updates the window title, and adds to
            // Recent Sessions, silently making the just-created portable copy
            // the identity of the CURRENTLY OPEN session. That would mean a
            // later, unrelated Cmd+S (the silent-overwrite path) rewrites the
            // portable bundle using the original (now-restored) scattered
            // paths, destroying the very portability this action exists to
            // produce. The live session's identity must not change just
            // because a copy of it was written somewhere else -- exactly like
            // "Export a copy" never repoints "the current document" in any
            // other app. If the user wants to actually switch to the new
            // bundle, showResult()'s "Open Moved Session" button does that
            // properly via igv.loadSession(...).
            new org.broad.igv.session.SessionWriter().saveSession(igv.getSession(), newSessionFile);
        } catch (Exception ex) {
            log.error("Error writing moved session to " + newSessionFile.getAbsolutePath(), ex);
            saveError = ex;
        } finally {
            for (Runnable restore : restorers) {
                restore.run();
            }
        }

        final Exception finalSaveError = saveError;
        final int finalCopiedCount = copiedCount;
        UIUtilities.invokeOnEventThread(() ->
                showResult(newSessionFile, finalCopiedCount, sourceFiles.size(), failures, finalSaveError));
    }

    /**
     * If the given field of {@code locator} names a file that was successfully
     * copied, repoint it at the copy and return a {@link Runnable} that
     * restores the original value; otherwise a no-op restorer.
     */
    private static Runnable retarget(ResourceLocator locator, LocatorField field, Map<String, String> copiedTo) {
        String original = field.getter().apply(locator);
        if (original == null) {
            return () -> {
            };
        }
        String newPath = copiedTo.get(original);
        if (newPath == null) {
            return () -> {
            };
        }
        field.setter().accept(locator, newPath);
        return () -> field.setter().accept(locator, original);
    }

    /**
     * Disambiguate a filename against ones already claimed in this destination
     * folder by inserting " (2)", " (3)", ... before the last extension
     * (e.g. "col0_CG.bw" -> "col0_CG (2).bw"), and record whichever name wins.
     */
    private static String uniqueName(String desired, Set<String> usedNames) {
        if (usedNames.add(desired)) {
            return desired;
        }
        int dot = desired.lastIndexOf('.');
        String base = dot > 0 ? desired.substring(0, dot) : desired;
        String ext = dot > 0 ? desired.substring(dot) : "";
        for (int n = 2; ; n++) {
            String candidate = base + " (" + n + ")" + ext;
            if (usedNames.add(candidate)) {
                return candidate;
            }
        }
    }

    private void showResult(File newSessionFile, int copiedCount, int totalCount, List<String> failures, Exception saveError) {
        if (saveError != null) {
            MessageUtils.showMessage("Error writing the moved session: " + saveError.getMessage());
            return;
        }

        StringBuilder msg = new StringBuilder();
        msg.append("<html><body style='width: 340px'>");
        msg.append("Copied ").append(copiedCount).append(" of ").append(totalCount).append(" data file(s) and wrote:<br><br>")
                .append("<tt>").append(newSessionFile.getAbsolutePath()).append("</tt>");
        if (!failures.isEmpty()) {
            msg.append("<br><br><b>").append(failures.size()).append(" file(s) could not be copied:</b><br>");
            for (String f : failures) {
                msg.append(f).append("<br>");
            }
        }
        msg.append("</body></html>");

        Object[] options = {"Open Moved Session", "Done"};
        int choice = JOptionPane.showOptionDialog(igv.getMainFrame(), msg.toString(),
                "Move Session + Data Files Into Folder", JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, options, options[1]);
        if (choice == 0) {
            LongRunningTask.submit(() -> {
                try {
                    igv.loadSession(newSessionFile.getAbsolutePath(), null);
                } catch (Exception ex) {
                    log.error("Error opening moved session " + newSessionFile.getAbsolutePath(), ex);
                }
            });
        }
    }
}
