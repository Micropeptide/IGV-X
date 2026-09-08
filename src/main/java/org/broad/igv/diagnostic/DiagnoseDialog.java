/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2026 Broad Institute, IGV-X contributors
 */
package org.broad.igv.diagnostic;

import org.broad.igv.track.Track;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.WaitCursorManager;
import org.broad.igv.ui.util.UIUtilities;
import org.broad.igv.util.LongRunningTask;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;

/**
 * IGV-X: the "Diagnose Track/Session" dialog. Runs TrackDiagnostics for the
 * requested track(s) and shows the report in a selectable text area with a
 * copy-to-clipboard button. Safe to open from any UI context.
 */
public class DiagnoseDialog extends JDialog {

    /**
     * Threshold above which the diagnostics loop runs off the EDT. Small
     * selections (the common case: right-click one or a few tracks) run
     * inline so the dialog appears instantly with no wait-cursor flicker;
     * "whole session" runs (Tools > Diagnose Track/Session with nothing
     * selected, on a session with hundreds of tracks) would otherwise hang
     * the UI for the whole scan.
     */
    private static final int ASYNC_THRESHOLD = 15;

    public static void showForTracks(Frame parent, List<Track> tracks) {
        if (tracks.size() <= ASYNC_THRESHOLD) {
            showReport(parent, buildReport(tracks));
            return;
        }
        WaitCursorManager.CursorToken token = WaitCursorManager.showWaitCursor();
        LongRunningTask.submit(() -> {
            String report = buildReport(tracks);
            UIUtilities.invokeOnEventThread(() -> {
                WaitCursorManager.removeWaitCursor(token);
                showReport(parent, report);
            });
        });
    }

    private static String buildReport(List<Track> tracks) {
        StringBuilder sb = new StringBuilder();
        sb.append("IGV-X Diagnose Report — ").append(tracks.size()).append(" track(s)\n\n");
        for (Track t : tracks) {
            try {
                DiagnosticReport report = TrackDiagnostics.diagnose(t);
                sb.append(report.render()).append("\n");
            } catch (Throwable e) {
                sb.append("=== ").append(t == null ? "(null track)" : t.getName())
                        .append(" ===\n[ERROR] Diagnostics itself failed: ").append(e).append("\n\n");
            }
        }
        return sb.toString();
    }

    public static void showReport(Frame parent, String text) {
        SwingUtilities.invokeLater(() -> {
            DiagnoseDialog dialog = new DiagnoseDialog(parent, text);
            dialog.setVisible(true);
        });
    }

    private DiagnoseDialog(Frame parent, String text) {
        super(parent, "IGV-X Diagnose", true);
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.getAccessibleContext().setAccessibleName("Diagnostic report");
        area.getAccessibleContext().setAccessibleDescription(
                "Read-only diagnostic report; use Copy to clipboard to share it");
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setLineWrap(false);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(820, 520));

        JButton copy = new JButton("Copy to clipboard");
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(area.getText()), null);
            copy.setText("Copied ✓");
        });
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());

        JPanel buttons = new JPanel();
        buttons.add(copy);
        buttons.add(close);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(buttons, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(parent);
    }
}
