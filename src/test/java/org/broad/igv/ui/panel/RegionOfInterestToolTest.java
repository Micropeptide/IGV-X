package org.broad.igv.ui.panel;

import org.broad.igv.AbstractHeadlessTest;
import org.broad.igv.feature.RegionOfInterest;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * IGV-X regression tests for the RegionOfInterestTool improvements:
 * drag-to-select and live feedback (click-click behavior preserved).
 */
public class RegionOfInterestToolTest extends AbstractHeadlessTest {

    @Test
    public void testCreateRegion_normalOrder() {
        RegionOfInterest roi = RegionOfInterestTool.createRegion("chr1", 1000, 5000);
        assertNotNull(roi);
        assertEquals("chr1", roi.getChr());
        assertEquals(1000, roi.getStart());
        assertEquals(5000, roi.getEnd());
    }

    @Test
    public void testCreateRegion_reverseDrag() {
        // Drag from right to left must still normalize start < end
        RegionOfInterest roi = RegionOfInterestTool.createRegion("chr1", 5000, 1000);
        assertNotNull(roi);
        assertEquals(1000, roi.getStart());
        assertEquals(5000, roi.getEnd());
    }

    @Test
    public void testCreateRegion_singlePoint() {
        // A click at a single position must produce a non-empty span (end == start + 1)
        RegionOfInterest roi = RegionOfInterestTool.createRegion("chr2", 777, 777);
        assertNotNull(roi);
        assertEquals(777, roi.getStart());
        assertEquals(778, roi.getEnd());
    }

    @Test
    public void testCreateRegion_organellarName() {
        // Chromosome-name resolution must not interfere with ROI creation
        RegionOfInterest roi = RegionOfInterestTool.createRegion("ChrM", 10, 20);
        assertNotNull(roi);
        assertEquals("ChrM", roi.getChr());
        assertEquals(10, roi.getStart());
        assertEquals(20, roi.getEnd());
    }
}
