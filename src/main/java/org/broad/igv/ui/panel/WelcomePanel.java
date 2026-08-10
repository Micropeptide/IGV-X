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

package org.broad.igv.ui.panel;

import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.RecentUrlsSet;
import org.broad.igv.ui.action.SmartOpenMenuAction;
import org.broad.igv.ui.util.RecentFiles;
import org.broad.igv.util.ResourceLocator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * IGV-X startup panel.  Shown in the main window when no data has been loaded
 * yet; lists the recently opened files and sessions as clickable entries so the
 * user can reopen them with a single click (Runtian, 2026-08-10).
 * <p>
 * The panel is hosted by {@code IGVContentPane} in a card layout and is
 * replaced by the normal data panel as soon as any track or session loads
 * (see IGV.addTracks / IGV.loadSession).
 */
public class WelcomePanel extends JPanel {

    private static final Logger log = LogManager.getLogger(WelcomePanel.class);

    private final IGV igv;
    private JList<String> recentFilesList;
    private JList<String> recentSessionsList;

    public WelcomePanel(IGV igv) {
        this.igv = igv;
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(24, 32, 24, 32));

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);
        center.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel("Welcome to IGV-X");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(title);
        center.add(Box.createVerticalStrut(6));

        JLabel subtitle = new JLabel("Open a recent file below, or use Open... to add files or sessions (auto-detected).");
        subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(subtitle);
        center.add(Box.createVerticalStrut(20));

        // ---- Recent files ----
        JLabel filesHeader = new JLabel("Recent Files");
        filesHeader.setFont(filesHeader.getFont().deriveFont(Font.BOLD, 14f));
        filesHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(filesHeader);
        center.add(Box.createVerticalStrut(4));

        recentFilesList = new JList<>();
        recentFilesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recentFilesList.setVisibleRowCount(6);
        recentFilesList.setPrototypeCellValue("/very/long/path/to/example/file_name.bigWig");
        recentFilesList.addMouseListener(doubleClickOpener(recentFilesList, false));
        JScrollPane filesScroll = new JScrollPane(recentFilesList);
        filesScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        filesScroll.setPreferredSize(new Dimension(560, 130));
        filesScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
        center.add(filesScroll);
        center.add(Box.createVerticalStrut(16));

        // ---- Recent sessions ----
        JLabel sessionsHeader = new JLabel("Recent Sessions");
        sessionsHeader.setFont(sessionsHeader.getFont().deriveFont(Font.BOLD, 14f));
        sessionsHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(sessionsHeader);
        center.add(Box.createVerticalStrut(4));

        recentSessionsList = new JList<>();
        recentSessionsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recentSessionsList.setVisibleRowCount(5);
        recentSessionsList.setPrototypeCellValue("/very/long/path/to/example/session.xml");
        recentSessionsList.addMouseListener(doubleClickOpener(recentSessionsList, true));
        JScrollPane sessionsScroll = new JScrollPane(recentSessionsList);
        sessionsScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        sessionsScroll.setPreferredSize(new Dimension(560, 110));
        sessionsScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
        center.add(sessionsScroll);
        center.add(Box.createVerticalStrut(18));

        JLabel hint = new JLabel("Double-click an entry to open it.");
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        center.add(hint);
        center.add(Box.createVerticalStrut(16));

        // ---- Buttons ----
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton openButton = new JButton("Open...");
        openButton.addActionListener(e ->
                new SmartOpenMenuAction("Open...", KeyEvent.VK_O, igv)
                        .actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, "open")));
        buttonPanel.add(openButton);

        JButton clearButton = new JButton("Clear Recent Files");
        clearButton.addActionListener(e -> {
            igv.getRecentUrls().clear();
            igv.getRecentSessionList().clear();
            refresh();
        });
        buttonPanel.add(clearButton);

        JButton dismissButton = new JButton("Dismiss");
        dismissButton.addActionListener(e -> igv.getContentPane().showWelcomePanel(false));
        buttonPanel.add(dismissButton);

        center.add(buttonPanel);
        center.add(Box.createVerticalGlue());

        add(center, BorderLayout.CENTER);
    }

    /**
     * Rebuild the two lists from the persisted history.  Called when the panel
     * is shown and after clearing recents.
     */
    public void refresh() {
        List<String> recentFiles = RecentFiles.pruneMissingFiles(igv.getRecentUrls().stream()
                .map(ResourceLocator::getPath)
                .collect(java.util.stream.Collectors.toList()));
        recentFilesList.setListData(recentFiles.toArray(new String[0]));
        recentFilesList.setToolTipText(recentFiles.isEmpty()
                ? "No recently opened files yet"
                : "Double-click to open");

        List<String> recentSessions = RecentFiles.pruneMissingFiles(igv.getRecentSessionList());
        recentSessionsList.setListData(recentSessions.toArray(new String[0]));
        recentSessionsList.setToolTipText(recentSessions.isEmpty()
                ? "No recently opened sessions yet"
                : "Double-click to open");
    }

    private MouseAdapter doubleClickOpener(JList<String> list, boolean sessionList) {
        return new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                    String path = list.getSelectedValue();
                    if (path != null) {
                        openPath(path, sessionList);
                    }
                }
            }
        };
    }

    private void openPath(String path, boolean sessionList) {
        // RecentFiles.open routes sessions vs tracks from the path itself.
        RecentFiles.open(igv, new ResourceLocator(path));
        // Once anything opens, drop back to the data panel.
        igv.getContentPane().showWelcomePanel(false);
    }
}
