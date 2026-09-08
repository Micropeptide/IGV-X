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

import org.broad.igv.Globals;
import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.prefs.Constants;
import org.broad.igv.prefs.PreferencesManager;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.action.SmartOpenMenuAction;
import org.broad.igv.ui.util.FileAssociationHelper;
import org.broad.igv.ui.util.RecentFiles;
import org.broad.igv.util.ResourceLocator;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private JPanel xmlAssocBanner;

    public WelcomePanel(IGV igv) {
        this.igv = igv;
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());

        JPanel column = new JPanel();
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setOpaque(false);
        column.setAlignmentX(Component.LEFT_ALIGNMENT);
        // A fixed-ish preferred width keeps this readable as a centered "start
        // screen" card rather than stretching across a wide monitor.
        column.setMaximumSize(new Dimension(620, Integer.MAX_VALUE));

        xmlAssocBanner = buildXmlAssocBanner();
        if (xmlAssocBanner != null) {
            xmlAssocBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
            column.add(xmlAssocBanner);
            column.add(Box.createVerticalStrut(18));
        }

        JPanel headerRow = new JPanel();
        headerRow.setOpaque(false);
        headerRow.setLayout(new BoxLayout(headerRow, BoxLayout.X_AXIS));
        headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        URL iconUrl = WelcomePanel.class.getResource("resources/igvx_icon_64.png");
        if (iconUrl != null) {
            JLabel iconLabel = new JLabel(new ImageIcon(iconUrl));
            iconLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 14));
            iconLabel.setAlignmentY(Component.TOP_ALIGNMENT);
            headerRow.add(iconLabel);
        }

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Welcome to IGV-X");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleBlock.add(title);
        titleBlock.add(Box.createVerticalStrut(4));

        JLabel subtitle = new JLabel("Open a recent file below, or use Open… to add files or sessions (auto-detected).");
        subtitle.setForeground(UIManager.getColor("Label.disabledForeground"));
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleBlock.add(subtitle);

        headerRow.add(titleBlock);
        column.add(headerRow);
        column.add(Box.createVerticalStrut(26));

        // ---- Recent files ----
        column.add(sectionHeader("Recent Files"));
        column.add(Box.createVerticalStrut(6));

        recentFilesList = newRecentList(false);
        recentFilesList.addMouseListener(doubleClickOpener(recentFilesList, false));
        column.add(cardFor(recentFilesList, 130, 150));
        column.add(Box.createVerticalStrut(20));

        // ---- Recent sessions ----
        column.add(sectionHeader("Recent Sessions"));
        column.add(Box.createVerticalStrut(6));

        recentSessionsList = newRecentList(true);
        recentSessionsList.addMouseListener(doubleClickOpener(recentSessionsList, true));
        column.add(cardFor(recentSessionsList, 110, 130));
        column.add(Box.createVerticalStrut(14));

        JLabel hint = new JLabel("Double-click an entry to open it.");
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        hint.setFont(hint.getFont().deriveFont(11f));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(hint);
        column.add(Box.createVerticalStrut(18));

        // ---- Buttons ----
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton openButton = new JButton("Open…");
        openButton.putClientProperty("JButton.buttonType", "default");
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

        column.add(buttonPanel);

        // Center the whole card both horizontally and vertically within the
        // window, rather than pinning it to the top-left corner of a
        // potentially much larger frame.
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(24, 32, 24, 32);
        add(column, gbc);

        // The root pane's default button should be "Open..." while this panel
        // is showing, so Return triggers it (harmless once the panel is hidden
        // since no key events reach a non-visible component's default button).
        SwingUtilities.invokeLater(() -> {
            JRootPane rootPane = getRootPane();
            if (rootPane != null) {
                rootPane.setDefaultButton(openButton);
            }
        });
    }

    private JLabel sectionHeader(String text) {
        JLabel header = new JLabel(text);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 13f));
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        return header;
    }

    private JList<String> newRecentList(boolean sessionList) {
        JList<String> list = new JList<>() {
            @Override
            public String getToolTipText(MouseEvent event) {
                int index = locationToIndex(event.getPoint());
                if (index < 0) {
                    return null;
                }
                String value = getModel().getElementAt(index);
                return value == null ? null : value;
            }
        };
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new RecentEntryRenderer());
        list.setFixedCellHeight(38);
        list.setBackground(UIManager.getColor("List.background"));
        // ToolTipManager only watches a component once setToolTipText(String)
        // has been called on it at least once -- without this, the
        // getToolTipText(MouseEvent) override above is never invoked and no
        // tooltip ever appears, no matter what it returns.
        list.setToolTipText("");
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }

            private void maybeShowPopup(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int index = list.locationToIndex(e.getPoint());
                if (index < 0) {
                    return;
                }
                list.setSelectedIndex(index);
                String path = list.getModel().getElementAt(index);
                JPopupMenu popup = new JPopupMenu();
                JMenuItem remove = new JMenuItem("Remove from Recent");
                remove.addActionListener(a -> {
                    if (sessionList) {
                        igv.getRecentSessionList().remove(path);
                    } else {
                        igv.getRecentUrls().removeIf(rl -> path.equals(rl.getPath()));
                    }
                    refresh();
                });
                popup.add(remove);
                popup.show(list, e.getX(), e.getY());
            }
        });
        return list;
    }

    private JScrollPane cardFor(JList<String> list, int preferredHeight, int maxHeight) {
        JScrollPane scroll = new JScrollPane(list);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setPreferredSize(new Dimension(620, preferredHeight));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, maxHeight));
        scroll.setBorder(BorderFactory.createLineBorder(dividerColor()));
        return scroll;
    }

    private static Color dividerColor() {
        Color c = UIManager.getColor("Separator.foreground");
        return c != null ? c : Color.GRAY;
    }

    /**
     * A dismissible one-time hint (macOS only) about enabling double-click
     * for .xml session files, since IGV-X can't make itself the default
     * handler for a shared file type on its own. Returns null (nothing added)
     * once the user has dismissed it or asked for the guide.
     */
    private JPanel buildXmlAssocBanner() {
        if (!Globals.IS_MAC) {
            return null;
        }
        if (PreferencesManager.getPreferences().getAsBoolean(Constants.XML_ASSOC_HINT_DISMISSED)) {
            return null;
        }

        JPanel banner = new JPanel(new BorderLayout(10, 0));
        banner.setOpaque(true);
        banner.setBackground(bannerBackground());
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(dividerColor()),
                BorderFactory.createEmptyBorder(10, 12, 10, 10)));

        JLabel text = new JLabel("<html><body style='width: 340px'>"
                + "<b>Tip:</b> .xml session files open via Finder's right-click → Open With today. "
                + "Want plain double-click to work too?</body></html>");
        banner.add(text, BorderLayout.CENTER);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));

        JButton setupButton = new JButton("Set Up…");
        setupButton.addActionListener(e -> {
            String sample = igv.getRecentSessionList().stream()
                    .filter(p -> p.toLowerCase().endsWith(".xml"))
                    .findFirst().orElse(null);
            FileAssociationHelper.showGuide(this, sample);
            dismissXmlAssocBanner();
        });
        actions.add(setupButton);
        actions.add(Box.createHorizontalStrut(4));

        JButton closeButton = new JButton("✕");
        closeButton.setToolTipText("Don't show this again");
        closeButton.setMargin(new Insets(2, 6, 2, 6));
        closeButton.addActionListener(e -> dismissXmlAssocBanner());
        actions.add(closeButton);

        banner.add(actions, BorderLayout.EAST);
        return banner;
    }

    private static Color bannerBackground() {
        Color base = UIManager.getColor("Panel.background");
        if (base == null) {
            return new Color(235, 242, 250);
        }
        // A gentle tint distinguishable from the plain panel background in
        // both light and (should it ever be enabled) dark system themes.
        int r = base.getRed(), g = base.getGreen(), b = base.getBlue();
        boolean dark = (r + g + b) / 3 < 128;
        return dark ? new Color(Math.min(255, r + 18), Math.min(255, g + 22), Math.min(255, b + 30))
                : new Color(Math.max(0, r - 12), Math.max(0, g - 6), Math.max(0, b + 6));
    }

    private void dismissXmlAssocBanner() {
        PreferencesManager.getPreferences().put(Constants.XML_ASSOC_HINT_DISMISSED, true);
        if (xmlAssocBanner != null) {
            xmlAssocBanner.setVisible(false);
        }
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

        List<String> recentSessions = RecentFiles.pruneMissingFiles(igv.getRecentSessionList());
        recentSessionsList.setListData(recentSessions.toArray(new String[0]));
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

    /**
     * Renders a recent-file/session path as a small two-line row: the
     * filename (bold) with a colored file-type badge, and the containing
     * directory (dim, smaller) underneath -- much more scannable than a raw
     * absolute path string, especially once several entries share a long
     * common prefix (e.g. everything under one lab's project folder).
     */
    private static class RecentEntryRenderer extends JPanel implements ListCellRenderer<String> {

        private static final Map<String, Color> BADGE_COLORS = new HashMap<>();

        static {
            BADGE_COLORS.put("bw", new Color(0x2E77C8));
            BADGE_COLORS.put("bigwig", new Color(0x2E77C8));
            BADGE_COLORS.put("bam", new Color(0xC77A1F));
            BADGE_COLORS.put("cram", new Color(0xC77A1F));
            BADGE_COLORS.put("vcf", new Color(0x8A4FBF));
            BADGE_COLORS.put("bed", new Color(0x2FA05A));
            BADGE_COLORS.put("bb", new Color(0x2FA05A));
            BADGE_COLORS.put("bigbed", new Color(0x2FA05A));
            BADGE_COLORS.put("gff", new Color(0x2FA05A));
            BADGE_COLORS.put("gff3", new Color(0x2FA05A));
            BADGE_COLORS.put("gtf", new Color(0x2FA05A));
            BADGE_COLORS.put("wig", new Color(0x3B8FA6));
            BADGE_COLORS.put("tdf", new Color(0x3B8FA6));
            BADGE_COLORS.put("xml", new Color(0x5A6470));
            BADGE_COLORS.put("igvx", new Color(0x5A6470));
            BADGE_COLORS.put("session", new Color(0x5A6470));
        }

        private final JLabel badge = new JLabel();
        private final JLabel nameLabel = new JLabel();
        private final JLabel pathLabel = new JLabel();

        RecentEntryRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));

            badge.setOpaque(true);
            badge.setHorizontalAlignment(SwingConstants.CENTER);
            badge.setFont(badge.getFont().deriveFont(Font.BOLD, 9f));
            badge.setForeground(Color.WHITE);
            badge.setPreferredSize(new Dimension(42, 18));
            badge.setMaximumSize(new Dimension(42, 18));

            JPanel badgeWrap = new JPanel(new GridBagLayout());
            badgeWrap.setOpaque(false);
            badgeWrap.add(badge);
            add(badgeWrap, BorderLayout.WEST);

            JPanel textPanel = new JPanel();
            textPanel.setOpaque(false);
            textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 12f));
            pathLabel.setFont(pathLabel.getFont().deriveFont(Font.PLAIN, 10f));
            textPanel.add(nameLabel);
            textPanel.add(pathLabel);
            add(textPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String value, int index,
                                                        boolean isSelected, boolean cellHasFocus) {
            String path = value == null ? "" : value;
            File f = new File(path);
            String name = f.getName();
            String parent = f.getParent();

            nameLabel.setText(name.isEmpty() ? path : name);
            pathLabel.setText(parent == null ? "" : parent);

            String ext = extensionOf(name);
            badge.setText(ext.isEmpty() ? "–" : ext.toUpperCase());
            badge.setBackground(BADGE_COLORS.getOrDefault(ext.toLowerCase(), new Color(0x8A8A8A)));

            Color bg = isSelected ? list.getSelectionBackground() : list.getBackground();
            Color fg = isSelected ? list.getSelectionForeground() : list.getForeground();
            setOpaque(true);
            setBackground(bg);
            nameLabel.setForeground(fg);
            pathLabel.setForeground(isSelected ? fg : UIManager.getColor("Label.disabledForeground"));

            return this;
        }

        private static String extensionOf(String filename) {
            int dot = filename.lastIndexOf('.');
            if (dot < 0 || dot == filename.length() - 1) {
                return "";
            }
            String ext = filename.substring(dot + 1);
            // Trim a trailing badge to something that still fits (e.g. "session.txt" -> "txt").
            return ext.length() > 6 ? ext.substring(0, 6) : ext;
        }
    }
}
