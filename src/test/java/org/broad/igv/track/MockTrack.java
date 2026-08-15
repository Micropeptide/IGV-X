package org.broad.igv.track;

import org.broad.igv.ui.panel.ReferenceFrame;
import org.broad.igv.util.ResourceLocator;

import java.awt.Rectangle;

/**
 * IGV-X: minimal {@link Track} implementation for tests that need real Track
 * objects without loading data files.  Uses a synthetic ResourceLocator so
 * the session machinery can enumerate the track.
 */
public class MockTrack extends AbstractTrack {

    public MockTrack(String name) {
        super(new ResourceLocator("mock://" + name), name, name);
        setHeight(40);
    }

    @Override
    public void render(RenderContext context, Rectangle rect) {
        // no-op — the mock carries no data to draw.
    }

    @Override
    public void load(ReferenceFrame frame) {
        // no-op — the mock has no data to load.
    }

    @Override
    public boolean isReadyToPaint(ReferenceFrame frame) {
        return true;
    }
}
