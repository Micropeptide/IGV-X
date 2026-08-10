package org.broad.igv.prefs;

import org.junit.Before;
import org.junit.Test;

import java.awt.Rectangle;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * IGV-X: regression tests for persisted main-window state (maximized flag + bounds).
 * Uses a dedicated temp prefs file so the shared test fixtures are never mutated.
 *
 * Note: PreferencesManager keeps a process-wide singleton map, so these tests only
 * assert set-then-read round-trips (not initial empty state) to stay order-independent.
 */
public class IGVPreferencesWindowStateTest {

    private Path tempPrefs;
    private IGVPreferences preferences;

    @Before
    public void setUp() throws Exception {
        tempPrefs = Files.createTempFile("igvx-windowstate-", ".properties");
        PreferencesManager.setPrefsFile(tempPrefs.toString());
        preferences = PreferencesManager.getPreferences();
    }

    @Test
    public void maximizedFlagRoundTrip() {
        preferences.setApplicationFrameMaximized(true);
        assertTrue(preferences.isApplicationFrameMaximized());
        preferences.setApplicationFrameMaximized(false);
        assertFalse(preferences.isApplicationFrameMaximized());
    }

    @Test
    public void frameBoundsRoundTrip() {
        preferences.setApplicationFrameBounds(new Rectangle(10, 20, 1200, 800));
        Rectangle bounds = preferences.getApplicationFrameBounds();
        assertNotNull(bounds);
        assertEquals(10, bounds.x);
        assertEquals(20, bounds.y);
        assertEquals(1200, bounds.width);
        assertEquals(800, bounds.height);
    }

    @Test
    public void zeroSizeBoundsAreRefused() {
        preferences.setApplicationFrameBounds(new Rectangle(10, 20, 1200, 800));
        // Zero-size bounds must not clobber the stored value
        preferences.setApplicationFrameBounds(new Rectangle(0, 0, 0, 0));
        Rectangle kept = preferences.getApplicationFrameBounds();
        assertNotNull(kept);
        assertEquals(1200, kept.width);
        assertEquals(800, kept.height);
    }
}
