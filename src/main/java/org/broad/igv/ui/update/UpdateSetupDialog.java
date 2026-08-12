package org.broad.igv.ui.update;

import org.broad.igv.prefs.PreferencesManager;

import javax.swing.*;
import java.awt.*;

/**
 * IGV-X: first-run setup dialog for automatic update checks.
 *
 * Shown once, on the first launch after installing an IGV-X build that
 * includes this feature.  Asks whether the user wants IGV-X to check for
 * updates from micropeptide's GitHub releases automatically, and how often.
 *
 * The choice is persisted to prefs and is always editable later in
 * Preferences &gt; Updates (IGV-X) — Check for updates on startup and the
 * update-check interval.
 */
public class UpdateSetupDialog extends JDialog {

    public static final String PREF_SETUP_DONE = "IGVX.UPDATE.SETUP.DONE";
    public static final String PREF_CHECK_INTERVAL_HOURS = "IGVX.UPDATE.INTERVAL.HOURS";

    public static final int INTERVAL_DAILY_HOURS = 24;
    public static final int INTERVAL_WEEKLY_HOURS = 168;
    public static final int INTERVAL_NEVER_HOURS = 0;

    private final JRadioButton dailyRadio = new JRadioButton("Check once a day");
    private final JRadioButton weeklyRadio = new JRadioButton("Check once a week");
    private final JRadioButton neverRadio = new JRadioButton("Never — I'll check manually (Help > Check for Updates)");

    /**
     * Show the first-run setup dialog if it has not been shown before.
     * Returns immediately; the dialog is shown on the EDT.
     */
    public static void promptIfFirstRun(Window parent) {
        try {
            if (PreferencesManager.getPreferences().getAsBoolean(PREF_SETUP_DONE)) {
                return;
            }
            SwingUtilities.invokeLater(() -> {
                try {
                    if (PreferencesManager.getPreferences().getAsBoolean(PREF_SETUP_DONE)) {
                        return;
                    }
                    UpdateSetupDialog dlg = new UpdateSetupDialog(parent);
                    dlg.setLocationRelativeTo(parent);
                    dlg.setVisible(true);
                } catch (Exception e) {
                    org.broad.igv.logging.LogManager.getLogger(UpdateSetupDialog.class)
                            .warn("First-run update setup skipped: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            org.broad.igv.logging.LogManager.getLogger(UpdateSetupDialog.class)
                    .warn("First-run update setup skipped: " + e.getMessage());
        }
    }

    /**
     * True when the user has an automatic check interval configured (> 0).
     */
    public static boolean isAutoCheckEnabled() {
        return PreferencesManager.getPreferences().getAsInt(PREF_CHECK_INTERVAL_HOURS) > 0;
    }

    /**
     * Read the configured interval in hours (0 = never).
     */
    public static int getIntervalHours() {
        return PreferencesManager.getPreferences().getAsInt(PREF_CHECK_INTERVAL_HOURS);
    }

    public static void setIntervalHours(int hours) {
        PreferencesManager.getPreferences().put(PREF_CHECK_INTERVAL_HOURS, String.valueOf(hours));
    }

    private UpdateSetupDialog(Window parent) {
        super(parent, "Welcome to IGV-X — Updates", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel main = new JPanel(new BorderLayout(12, 12));
        main.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        main.setPreferredSize(new Dimension(520, 300));

        JTextArea intro = new JTextArea();
        intro.setEditable(false);
        intro.setLineWrap(true);
        intro.setWrapStyleWord(true);
        intro.setOpaque(false);
        intro.setText("IGV-X is a customized version of IGV built specifically for micropeptide's research " +
                "(Arabidopsis epigenetics / WGBS).\n\n" +
                "New versions and release notes are published on the micropeptide GitHub page. " +
                "Would you like IGV-X to check for updates automatically?");
        main.add(intro, BorderLayout.NORTH);

        ButtonGroup group = new ButtonGroup();
        group.add(dailyRadio);
        group.add(weeklyRadio);
        group.add(neverRadio);
        JPanel radios = new JPanel();
        radios.setLayout(new BoxLayout(radios, BoxLayout.Y_AXIS));
        radios.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 4));
        weeklyRadio.setSelected(true);
        radios.add(dailyRadio);
        radios.add(Box.createVerticalStrut(8));
        radios.add(weeklyRadio);
        radios.add(Box.createVerticalStrut(8));
        radios.add(neverRadio);
        main.add(radios, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton ok = new JButton("OK");
        ok.addActionListener(e -> {
            int hours;
            if (dailyRadio.isSelected()) hours = INTERVAL_DAILY_HOURS;
            else if (weeklyRadio.isSelected()) hours = INTERVAL_WEEKLY_HOURS;
            else hours = INTERVAL_NEVER_HOURS;
            setIntervalHours(hours);
            // Startup check follows the interval choice: on for daily/weekly, off for never.
            PreferencesManager.getPreferences().put(
                    UpdateManager.PREF_CHECK_ON_STARTUP, String.valueOf(hours > 0));
            PreferencesManager.getPreferences().put(PREF_SETUP_DONE, "true");
            dispose();
        });
        JButton remind = new JButton("Ask me later");
        remind.addActionListener(e -> dispose());
        buttons.add(remind);
        buttons.add(ok);
        ok.requestFocusInWindow();
        main.add(buttons, BorderLayout.SOUTH);

        setContentPane(main);
        pack();
    }
}
