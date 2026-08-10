package org.broad.igv.feature;

import org.broad.igv.AbstractHeadlessTest;
import org.junit.Test;

import java.awt.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * IGV-X regression tests for the persistent Bookmark feature.
 */
public class BookmarkTest extends AbstractHeadlessTest {

    @Test
    public void testBookmarkDefaults() {
        Bookmark bm = new Bookmark("chr1", 100, 500, "promoter");
        assertEquals("chr1", bm.getChr());
        assertEquals(100, bm.getStart());
        assertEquals(500, bm.getEnd());
        assertEquals("promoter", bm.getLabel());
        assertFalse(bm.isHighlighted());
        // Default color falls back to the shared RegionOfInterest default
        assertEquals(RegionOfInterest.getDefaultBackgroundColor(), bm.getColor());
    }

    @Test
    public void testBookmarkColorAndHighlight() {
        Bookmark bm = new Bookmark("chr2", 10, 20, "peak", new Color(0, 128, 255));
        assertEquals(new Color(0, 128, 255), bm.getColor());
        bm.setHighlighted(true);
        assertTrue(bm.isHighlighted());
        bm.setHighlighted(false);
        assertFalse(bm.isHighlighted());
    }

    @Test
    public void testColorSerialization() {
        assertEquals("#ff0000", Bookmark.colorToString(Color.RED));
        assertEquals("#0080ff", Bookmark.colorToString(new Color(0, 128, 255)));
        assertNull(Bookmark.colorToString(null));
        assertEquals(Color.RED, Bookmark.colorFromString("#ff0000"));
        assertEquals(Color.RED, Bookmark.colorFromString("FF0000"));
        assertNull(Bookmark.colorFromString(null));
        assertNull(Bookmark.colorFromString(""));
        assertNull(Bookmark.colorFromString("not-a-color"));
        assertNull(Bookmark.colorFromString("#12345"));
    }
}
