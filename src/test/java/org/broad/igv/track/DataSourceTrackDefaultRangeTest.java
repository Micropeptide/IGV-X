package org.broad.igv.track;

import org.broad.igv.AbstractHeadlessTest;
import org.broad.igv.prefs.Constants;
import org.broad.igv.prefs.PreferencesManager;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * IGV-X regression tests for the configurable default quantitative track range.
 */
public class DataSourceTrackDefaultRangeTest extends AbstractHeadlessTest {

    @Test
    public void testDefaultRangeUnsetReturnsNull() {
        PreferencesManager.getPreferences().remove(Constants.DEFAULT_QUANT_RANGE_MIN);
        PreferencesManager.getPreferences().remove(Constants.DEFAULT_QUANT_RANGE_MAX);
        assertNull(DataSourceTrack.getDefaultQuantRange());
    }

    @Test
    public void testDefaultRangeValid() {
        PreferencesManager.getPreferences().put(Constants.DEFAULT_QUANT_RANGE_MIN, "-5");
        PreferencesManager.getPreferences().put(Constants.DEFAULT_QUANT_RANGE_MAX, "100");
        Float[] range = DataSourceTrack.getDefaultQuantRange();
        assertNotNull(range);
        assertEquals(-5f, range[0], 1e-6);
        assertEquals(100f, range[1], 1e-6);
    }

    @Test
    public void testDefaultRangeBlankFallsBack() {
        PreferencesManager.getPreferences().put(Constants.DEFAULT_QUANT_RANGE_MIN, "  ");
        PreferencesManager.getPreferences().put(Constants.DEFAULT_QUANT_RANGE_MAX, "100");
        assertNull(DataSourceTrack.getDefaultQuantRange());
    }

    @Test
    public void testDefaultRangeInvalidMinGtMaxFallsBack() {
        PreferencesManager.getPreferences().put(Constants.DEFAULT_QUANT_RANGE_MIN, "100");
        PreferencesManager.getPreferences().put(Constants.DEFAULT_QUANT_RANGE_MAX, "-5");
        assertNull(DataSourceTrack.getDefaultQuantRange());
    }

    @Test
    public void testDefaultRangeNonNumericFallsBack() {
        PreferencesManager.getPreferences().put(Constants.DEFAULT_QUANT_RANGE_MIN, "abc");
        PreferencesManager.getPreferences().put(Constants.DEFAULT_QUANT_RANGE_MAX, "100");
        assertNull(DataSourceTrack.getDefaultQuantRange());
    }
}
