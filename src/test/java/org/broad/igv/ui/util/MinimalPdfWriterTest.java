package org.broad.igv.ui.util;

import org.broad.igv.AbstractHeadlessTest;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * IGV-X regression tests for the dependency-free PDF export and snapshot options.
 */
public class MinimalPdfWriterTest extends AbstractHeadlessTest {

    @Test
    public void testSnapshotOptionsDefaultsAndReset() {
        SnapshotOptions.reset();
        assertFalse(SnapshotOptions.isSelectedTracksOnly());
        assertFalse(SnapshotOptions.isPublicationMode());
        assertEquals(96, SnapshotOptions.getDpi());

        SnapshotOptions.setDpi(300);
        SnapshotOptions.setSelectedTracksOnly(true);
        SnapshotOptions.setPublicationMode(true);
        assertEquals(300, SnapshotOptions.getDpi());
        assertTrue(SnapshotOptions.isSelectedTracksOnly());
        assertTrue(SnapshotOptions.isPublicationMode());

        SnapshotOptions.reset();
        assertEquals(96, SnapshotOptions.getDpi());
        assertFalse(SnapshotOptions.isSelectedTracksOnly());
    }

    @Test
    public void testWritesValidPdf() throws Exception {
        BufferedImage img = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 640, 480);
        g.dispose();

        File out = File.createTempFile("igvx-test", ".pdf");
        out.deleteOnExit();
        MinimalPdfWriter.writeImagePdf(out, img, 96);

        byte[] bytes;
        try (FileInputStream fis = new FileInputStream(out)) {
            bytes = fis.readAllBytes();
        }
        assertTrue(bytes.length > 1000);
        // PDF header
        assertEquals('%', bytes[0]);
        assertEquals('P', bytes[1]);
        assertEquals('D', bytes[2]);
        assertEquals('F', bytes[3]);
        String text = new String(bytes, "ISO-8859-1");
        assertTrue(text.contains("%%EOF"));
        assertTrue(text.contains("startxref"));
        assertTrue(text.contains("/Filter /DCTDecode"));
    }

    @Test
    public void testPngExportRoundTrip() throws Exception {
        // Ensure the in-memory PNG path used by export still works with ImageIO
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        File out = File.createTempFile("igvx-test", ".png");
        out.deleteOnExit();
        assertTrue(ImageIO.write(img, "png", out));
        assertTrue(out.length() > 0);
    }
}
