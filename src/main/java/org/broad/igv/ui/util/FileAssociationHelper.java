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

package org.broad.igv.ui.util;

import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * IGV-X: a guided, one-time-setup dialog explaining how to make macOS open
 * plain {@code .xml} IGV session files in IGV-X on double-click. macOS shares
 * the ".xml" file type across every app that can read XML (Xcode, browsers,
 * text editors, ...), so IGV-X can only be the *default* handler for it after
 * a one-time manual step in Finder -- there is no supported way for an app to
 * silently take over a shared file type. This dialog exists so that one-time
 * step is as close to effortless as possible: concrete numbered steps, and a
 * button that reveals a real session file in Finder (pre-selected) so
 * "right-click it" is one click away instead of a hunt.
 * <p>
 * Session files IGV-X owns outright (.igvx, .session, .idxsession, ...)
 * already open on double-click with no setup -- this dialog is only about the
 * shared .xml extension.
 */
public class FileAssociationHelper {

    private static final Logger log = LogManager.getLogger(FileAssociationHelper.class);

    private FileAssociationHelper() {
    }

    /**
     * Show the guided dialog. {@code sampleSessionPath}, if non-null and the
     * file still exists, is offered as the file to reveal in Finder so the
     * user doesn't have to go find one themselves.
     */
    public static void showGuide(Component parent, String sampleSessionPath) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "Open Session Files by Double-Clicking",
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setResizable(false);

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));

        JLabel heading = new JLabel("<html><body style='width: 360px'>"
                + "<b style='font-size: 13px'>IGV-X session files (.igvx, .session, .idxsession) "
                + "already open on double-click.</b><br><br>"
                + "Standard <b>.xml</b> sessions are shared with every app that reads XML, so macOS "
                + "needs one manual, one-time step before it knows IGV-X should open them by default."
                + "</body></html>");
        content.add(heading, BorderLayout.NORTH);

        JPanel steps = new JPanel();
        steps.setLayout(new BoxLayout(steps, BoxLayout.Y_AXIS));
        steps.add(stepLabel(1, "Click “Reveal a Session File in Finder” below (or find any .xml session yourself)."));
        steps.add(Box.createVerticalStrut(6));
        steps.add(stepLabel(2, "Right-click it → Get Info."));
        steps.add(Box.createVerticalStrut(6));
        steps.add(stepLabel(3, "Under “Open with”, choose IGV-X."));
        steps.add(Box.createVerticalStrut(6));
        steps.add(stepLabel(4, "Click “Change All…” and confirm."));
        steps.add(Box.createVerticalStrut(10));
        JLabel footnote = new JLabel("<html><body style='width: 360px'>"
                + "You only need to do this once — every .xml session opens in IGV-X afterward.</body></html>");
        footnote.setForeground(UIManager.getColor("Label.disabledForeground"));
        footnote.setFont(footnote.getFont().deriveFont(11f));
        steps.add(footnote);

        content.add(steps, BorderLayout.CENTER);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        boolean haveSample = sampleSessionPath != null && new File(sampleSessionPath).exists();
        JButton revealButton = new JButton("Reveal a Session File in Finder");
        revealButton.setEnabled(haveSample);
        revealButton.setToolTipText(haveSample ? null : "No recently opened session file to reveal yet -- open one first");
        revealButton.addActionListener(e -> revealInFinder(sampleSessionPath));
        buttonRow.add(revealButton);

        JButton doneButton = new JButton("Done");
        doneButton.addActionListener(e -> dialog.dispose());
        buttonRow.add(doneButton);

        content.add(buttonRow, BorderLayout.SOUTH);

        dialog.getRootPane().setDefaultButton(doneButton);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static JLabel stepLabel(int number, String text) {
        JLabel label = new JLabel("<html><body style='width: 340px'><b>" + number + ".</b> " + text + "</body></html>");
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /**
     * Ask Finder to reveal (and pre-select) the given file. Never throws --
     * failures are logged and otherwise silent, since this is a convenience
     * shortcut, not a required step (the user can always navigate manually).
     */
    private static void revealInFinder(String path) {
        try {
            new ProcessBuilder("open", "-R", path).start();
        } catch (IOException e) {
            log.warn("Could not reveal file in Finder: " + path, e);
            MessageUtils.showMessage("Couldn't open Finder automatically. Please locate the file manually:\n" + path);
        }
    }
}
