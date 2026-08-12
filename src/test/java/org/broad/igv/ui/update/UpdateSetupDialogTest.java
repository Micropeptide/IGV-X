package org.broad.igv.ui.update;

import org.broad.igv.prefs.PreferencesManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * IGV-X: first-run update-setup prefs contract.
 *
 * Uses a DEDICATED temp prefs file (rule 14/15: PreferencesManager is a
 * process-wide singleton map and must never touch shared fixtures).
 */
public class UpdateSetupDialogTest {

    private Path prefsFile;

    @Before
    public void setUp() throws Exception {
        prefsFile = Files.createTempFile("igvx-update-setup", ".prefs");
        PreferencesManager.setPrefsFile(prefsFile.toString());
        // Fresh preferences: never-before-set state.
        PreferencesManager.getPreferences().remove(UpdateSetupDialog.PREF_SETUP_DONE);
        PreferencesManager.getPreferences().remove(UpdateSetupDialog.PREF_CHECK_INTERVAL_HOURS);
        PreferencesManager.getPreferences().remove(UpdateManager.PREF_CHECK_ON_STARTUP);
    }

    @After
    public void tearDown() throws Exception {
        PreferencesManager.getPreferences().clear();
        Files.deleteIfExists(prefsFile);
    }

    @Test
    public void neverSetIntervalDefaultsToDisabled() {
        assertFalse("fresh install must not auto-check", UpdateSetupDialog.isAutoCheckEnabled());
        assertEquals(0, UpdateSetupDialog.getIntervalHours());
    }

    @Test
    public void dailyIntervalEnablesAutoCheck() {
        UpdateSetupDialog.setIntervalHours(UpdateSetupDialog.INTERVAL_DAILY_HOURS);
        assertTrue(UpdateSetupDialog.isAutoCheckEnabled());
        assertEquals(24, UpdateSetupDialog.getIntervalHours());
    }

    @Test
    public void weeklyIntervalEnablesAutoCheck() {
        UpdateSetupDialog.setIntervalHours(UpdateSetupDialog.INTERVAL_WEEKLY_HOURS);
        assertTrue(UpdateSetupDialog.isAutoCheckEnabled());
        assertEquals(168, UpdateSetupDialog.getIntervalHours());
    }

    @Test
    public void neverIntervalDisablesAutoCheck() {
        UpdateSetupDialog.setIntervalHours(UpdateSetupDialog.INTERVAL_NEVER_HOURS);
        assertFalse(UpdateSetupDialog.isAutoCheckEnabled());
        assertEquals(0, UpdateSetupDialog.getIntervalHours());
    }

    @Test
    public void okChoicePersistsStartupPrefWithInterval() {
        // Simulate the dialog's OK handler for the daily option:
        UpdateSetupDialog.setIntervalHours(UpdateSetupDialog.INTERVAL_DAILY_HOURS);
        PreferencesManager.getPreferences().put(UpdateManager.PREF_CHECK_ON_STARTUP, "true");
        PreferencesManager.getPreferences().put(UpdateSetupDialog.PREF_SETUP_DONE, "true");

        assertTrue(PreferencesManager.getPreferences().getAsBoolean(UpdateManager.PREF_CHECK_ON_STARTUP));
        assertTrue(PreferencesManager.getPreferences().getAsBoolean(UpdateSetupDialog.PREF_SETUP_DONE));
        assertTrue(UpdateSetupDialog.isAutoCheckEnabled());
    }

    @Test
    public void neverChoicePersistsStartupPrefOff() {
        UpdateSetupDialog.setIntervalHours(UpdateSetupDialog.INTERVAL_NEVER_HOURS);
        PreferencesManager.getPreferences().put(UpdateManager.PREF_CHECK_ON_STARTUP, "false");
        PreferencesManager.getPreferences().put(UpdateSetupDialog.PREF_SETUP_DONE, "true");

        assertFalse(PreferencesManager.getPreferences().getAsBoolean(UpdateManager.PREF_CHECK_ON_STARTUP));
        assertFalse(UpdateSetupDialog.isAutoCheckEnabled());
    }
}
