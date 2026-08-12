package org.broad.igv.ui.update;

import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.prefs.PreferencesManager;

import javax.swing.*;
import java.awt.*;
import java.util.Timer;
import java.util.TimerTask;

/**
 * IGV-X: background update check runner.
 *
 * Runs the network fetch off the EDT (never blocks the UI), then posts the
 * result dialog back onto the EDT.  A single in-flight check is tracked so
 * menu clicks / startup checks / periodic checks cannot stack dialogs.
 */
public class UpdateManager {

    private static final Logger log = LogManager.getLogger(UpdateManager.class);

    public static final String PREF_UPDATE_URL = "IGVX.UPDATE.URL";
    public static final String PREF_CHECK_ON_STARTUP = "IGVX.UPDATE.CHECK.ON_STARTUP";

    private static volatile boolean checkInFlight = false;
    private static volatile Timer periodicTimer = null;

    /**
     * Kick off an update check and show the result dialog on the EDT.
     * No-op if a check is already in flight.  Safe to call from any thread.
     */
    public static void checkAndShow(Window parent) {
        runCheck(parent, true);
    }

    /**
     * Kick off an update check; show the result dialog ONLY when an update is
     * actually available (periodic/auto checks stay quiet when up to date or
     * when the feed is unreachable — the user can always check manually).
     */
    public static void checkAndShowIfUpdate(Window parent) {
        runCheck(parent, false);
    }

    private static void runCheck(Window parent, boolean showAll) {
        if (checkInFlight) return;
        checkInFlight = true;
        final Window owner = parent != null ? parent : findActiveWindow();
        new Thread(() -> {
            try {
                String feedUrl = PreferencesManager.getPreferences().get(PREF_UPDATE_URL, UpdateChecker.DEFAULT_FEED_URL);
                String current = org.broad.igv.Globals.VERSION;
                UpdateChecker.UpdateInfo info = UpdateChecker.checkForUpdate(feedUrl, current);
                if (showAll || info.status == UpdateChecker.Status.UPDATE_AVAILABLE) {
                    SwingUtilities.invokeLater(() -> UpdateCheckDialog.show(owner, info));
                }
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

    /**
     * First-run setup: prompt once (on the EDT, non-blocking) asking whether
     * and how often to check for updates.  The choice is stored in prefs by
     * {@link UpdateSetupDialog}.
     */
    public static void promptFirstRunIfNeeded(Window parent) {
        UpdateSetupDialog.promptIfFirstRun(parent);
    }

    /**
     * Schedule a periodic auto-check at the interval stored in prefs
     * (IGVX.UPDATE.INTERVAL.HOURS).  Only notifies when an update is actually
     * available.  No-op when the interval is 0 (never) or a timer is already
     * running.  The timer thread is a daemon, so it never blocks app exit.
     */
    public static synchronized void startPeriodicChecks(Window parent) {
        int intervalHours = UpdateSetupDialog.getIntervalHours();
        if (intervalHours <= 0 || periodicTimer != null) {
            return;
        }
        final Window owner = parent != null ? parent : findActiveWindow();
        periodicTimer = new Timer("IGV-X periodic update check", true);
        periodicTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                checkAndShowIfUpdate(owner);
            }
        }, (long) intervalHours * 3600_000L, (long) intervalHours * 3600_000L);
        log.info("IGV-X periodic update check scheduled every " + intervalHours + "h");
    }

    private static Window findActiveWindow() {
        for (Window w : Window.getWindows()) {
            if (w.isVisible() && w.isShowing()) return w;
        }
        return null;
    }
}
