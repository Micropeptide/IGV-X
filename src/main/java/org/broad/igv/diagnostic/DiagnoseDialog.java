/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2026 Broad Institute, IGV-X contributors
 */
package org.broad.igv.diagnostic;

import org.broad.igv.track.Track;
import org.broad.igv.ui.IGV;

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

    public static void showForTracks(Frame parent, List<Track> tracks) {
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
        showReport(parent, sb.toString());
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
