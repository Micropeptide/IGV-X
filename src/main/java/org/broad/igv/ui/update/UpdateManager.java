package org.broad.igv.ui.update;

import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.prefs.PreferencesManager;

import javax.swing.*;
import java.awt.*;

/**
 * IGV-X: background update check runner.
 *
 * Runs the network fetch off the EDT (never blocks the UI), then posts the
 * result dialog back onto the EDT.  A single in-flight check is tracked so
 * menu clicks / startup checks cannot stack dialogs.
 */
public class UpdateManager {

    private static final Logger log = LogManager.getLogger(UpdateManager.class);

    public static final String PREF_UPDATE_URL = "IGVX.UPDATE.URL";
    public static final String PREF_CHECK_ON_STARTUP = "IGVX.UPDATE.CHECK.ON_STARTUP";

    private static volatile boolean checkInFlight = false;

    /**
     * Kick off an update check and show the result dialog on the EDT.
     * No-op if a check is already in flight.  Safe to call from any thread.
     */
    public static void checkAndShow(Window parent) {
        if (checkInFlight) return;
        checkInFlight = true;
        final Window owner = parent != null ? parent : findActiveWindow();
        new Thread(() -> {
            try {
                String feedUrl = PreferencesManager.getPreferences().get(PREF_UPDATE_URL, UpdateChecker.DEFAULT_FEED_URL);
                String current = org.broad.igv.Globals.VERSION;
                UpdateChecker.UpdateInfo info = UpdateChecker.checkForUpdate(feedUrl, current);
                SwingUtilities.invokeLater(() -> UpdateCheckDialog.show(owner, info));
            } finally {
                checkInFlight = false;
            }
        }, "IGV-X update check").start();
    }

    /**
     * Startup auto-check: only when the preference is enabled.  Runs entirely
     * in the background; never blocks startup; silent on error (the user can
     * check manually from the Help menu).
     */
    public static void checkOnStartupIfEnabled(Window parent) {
        try {
            if (!PreferencesManager.getPreferences().getAsBoolean(PREF_CHECK_ON_STARTUP)) {
                return;
            }
            checkAndShow(parent);
        } catch (Exception e) {
            log.warn("Startup update check skipped: " + e.getMessage());
        }
    }

    private static Window findActiveWindow() {
        for (Window w : Window.getWindows()) {
            if (w.isVisible() && w.isShowing()) return w;
        }
        return null;
    }
}
