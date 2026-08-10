/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
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
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.broad.igv.ui.panel;

import org.broad.igv.feature.RegionOfInterest;
import org.broad.igv.ui.AbstractDataPanelTool;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.util.UIUtilities;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author eflakes
 *
 * IGV-X: supports both click-click and drag-to-select region definition.
 * Dragging paints live feedback (start + end boundaries and a translucent
 * fill) so the user can see exactly which region will be added.
 */
public class RegionOfInterestTool extends AbstractDataPanelTool {

    Integer roiStart = null;
    Integer roiEnd = null;
    private int pressX = -1;
    private boolean dragging = false;
    JButton roiButton;

    public RegionOfInterestTool(DataPanel owner, JButton roiButton) {
        super(owner, Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        this.roiButton = roiButton;
        setName("Region of Interest");
    }

    public int getRoiStart() {
        return (roiStart == null ? 0 : roiStart.intValue());
    }

    public int getRoiEnd() {
        return (roiEnd == null ? 0 : roiEnd.intValue());
    }

    public boolean isDragging() {
        return dragging;
    }

    /**
     * Create a {@link RegionOfInterest} from two chromosome positions,
     * normalizing start/end order and guaranteeing a non-empty span.
     * Package-private for unit testing; both the drag and click paths use it.
     */
    static RegionOfInterest createRegion(String chromosomeName, int posA, int posB) {
        int start = Math.min(posA, posB);
        int end = Math.max(posA, posB);
        if (start == end) {
            ++end;
        }
        return new RegionOfInterest(chromosomeName, start, end, null);
    }

    /**
     * The mouse has been pressed.  Record the press position; the ROI start
     * is only committed when the user actually drags (or clicks, see
     * {@link #mouseClicked(MouseEvent)}).
     */
    @Override
    public void mousePressed(final MouseEvent e) {
        if (e.isPopupTrigger() || e.getButton() != MouseEvent.BUTTON1) {
            return;
        }
        pressX = e.getX();
        roiStart = null;
        roiEnd = null;
        dragging = false;
    }

    /**
     * The mouse has been dragged while the ROI tool is active.  Commit the
     * drag start (from the press position) and track the current end with
     * live repaint feedback.
     */
    @Override
    public void mouseDragged(final MouseEvent e) {
        if (pressX < 0) {
            return;
        }
        ReferenceFrame referenceFrame = this.getReferenceFame();
        if (referenceFrame.getChromosome() == null || referenceFrame.getChrName() == null) {
            return;
        }
        if (!dragging) {
            roiStart = (int) referenceFrame.getChromosomePosition(pressX);
            dragging = true;
        }
        roiEnd = (int) referenceFrame.getChromosomePosition(e.getX());
        UIUtilities.invokeOnEventThread(() -> getOwner().paintImmediately(getOwner().getBounds()));
    }

    /**
     * The mouse has been released.  If a drag was in progress, finalize the
     * region of interest; otherwise leave the event for the click path.
     */
    @Override
    public void mouseReleased(final MouseEvent e) {
        if (!dragging) {
            pressX = -1;
            roiStart = null;
            roiEnd = null;
            return;
        }
        dragging = false;
        pressX = -1;
        try {
            ReferenceFrame referenceFrame = this.getReferenceFame();
            String chromosomeName = referenceFrame.getChrName();
            if (chromosomeName == null || roiStart == null || roiEnd == null) {
                return;
            }
            RegionOfInterest regionOfInterest = createRegion(chromosomeName, roiStart, roiEnd);
            IGV.getInstance().endROI();
            IGV.getInstance().addRegionOfInterest(regionOfInterest);
            IGV.getInstance().repaint();
        } finally {
            roiStart = null;
            roiEnd = null;
            roiButton.setSelected(false);
        }
    }

    /**
     * The mouse has been clicked.  Define one edge of the region of interest.
     * (Click-click fallback; drag-select is handled by press/drag/release.)
     */
    @Override
    public void mouseClicked(final MouseEvent e) {

        if (e.isPopupTrigger()) {
            return;
        }

        if(e.getClickCount() > 1) {
            return;
        }

        ReferenceFrame referenceFrame = this.getReferenceFame();

        if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1) {

            Object chromosome = referenceFrame.getChromosome();

            // Allow Regions of Interest edits if ROI is enabled
            // and we have a valid Chromosome
            if (chromosome != null) {

                String chromosomeName = referenceFrame.getChrName();
                if (chromosomeName != null) {

                    int x = e.getX();

                    // Create a user Region of Interest
                    if (roiStart == null) {
                        roiStart = (int) referenceFrame.getChromosomePosition(x);
                        UIUtilities.invokeOnEventThread(() -> getOwner().paintImmediately(getOwner().getBounds()));

                    } else {

                        try {

                            int roiEnd = (int) referenceFrame.getChromosomePosition(x);

                            // Create a Region of Interest
                            RegionOfInterest regionOfInterest = createRegion(chromosomeName, roiStart, roiEnd);

                            IGV.getInstance().endROI();
                            IGV.getInstance().addRegionOfInterest(regionOfInterest);
                            IGV.getInstance().repaint();
                        } finally {
                            roiButton.setSelected(false);
                        }
                    }
                }

            }
        }
    }
}
