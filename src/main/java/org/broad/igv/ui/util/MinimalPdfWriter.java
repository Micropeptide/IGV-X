/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
 * Copyright (c) 2026 IGV-X
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

package org.broad.igv.ui.util;

import java.awt.image.BufferedImage;
import java.io.*;

/**
 * IGV-X: minimal dependency-free single-page PDF writer.
 * <p>
 * Paints the supplied (already rendered) image into a PDF as a DCTDecode (JPEG)
 * XObject. Produces a standards-compliant PDF readable by Preview, Acrobat,
 * and macOS Quick Look. No iText/Batik-PDF dependency required.
 * <p>
 * Page size is derived from the image pixel size and the requested DPI so the
 * physical dimensions are correct (72 points per inch).
 */
public class MinimalPdfWriter {

    private MinimalPdfWriter() {
    }

    /**
     * Write a one-page PDF containing {@code image} to {@code file}.
     *
     * @param dpi requested output DPI (96 = screen scale); used only for page size
     */
    public static void writeImagePdf(File file, BufferedImage image, double dpi) throws IOException {

        // Encode the image as JPEG for embedding
        ByteArrayOutputStream jpegBytes = new ByteArrayOutputStream();
        if (!javax.imageio.ImageIO.write(image, "jpg", jpegBytes)) {
            throw new IOException("Failed to encode JPEG for PDF export");
        }
        byte[] jpeg = jpegBytes.toByteArray();

        double dpiSafe = dpi > 0 ? dpi : 96.0;
        double pageWidthPt = image.getWidth() * 72.0 / dpiSafe;
        double pageHeightPt = image.getHeight() * 72.0 / dpiSafe;

        String content = "q\n" + pageWidthPt + " 0 0 " + pageHeightPt + " 0 0 cm\n/Im0 Do\nQ\n";

        // Object stream bodies (offsets computed below)
        String[] objects = new String[6];
        objects[1] = "<< /Type /Catalog /Pages 2 0 R >>";
        objects[2] = "<< /Type /Pages /Kids [3 0 R] /Count 1 >>";
        objects[3] = "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + pageWidthPt + " " + pageHeightPt
                + "] /Resources << /XObject << /Im0 5 0 R >> >> /Contents 4 0 R >>";
        objects[4] = "<< /Length " + content.length() + " >>";
        objects[5] = "<< /Type /XObject /Subtype /Image /Width " + image.getWidth()
                + " /Height " + image.getHeight()
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + jpeg.length + " >>";

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            long[] offsets = new long[6];
            bos.write("%PDF-1.4\n".getBytes("ISO-8859-1"));

            for (int i = 1; i <= 5; i++) {
                offsets[i] = bos.size();
                bos.write((i + " 0 obj\n").getBytes("ISO-8859-1"));
                bos.write(objects[i].getBytes("ISO-8859-1"));
                bos.write("\nendobj\n".getBytes("ISO-8859-1"));
                if (i == 4) {
                    bos.write("stream\n".getBytes("ISO-8859-1"));
                    bos.write(content.getBytes("ISO-8859-1"));
                    bos.write("endstream\n".getBytes("ISO-8859-1"));
                } else if (i == 5) {
                    bos.write("stream\n".getBytes("ISO-8859-1"));
                    bos.write(jpeg);
                    bos.write("\nendstream\n".getBytes("ISO-8859-1"));
                }
            }

            long xrefOffset = bos.size();
            StringBuilder xref = new StringBuilder();
            xref.append("xref\n0 6\n");
            xref.append("0000000000 65535 f \n");
            for (int i = 1; i <= 5; i++) {
                xref.append(String.format("%010d 00000 n \n", offsets[i]));
            }
            xref.append("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xrefOffset).append("\n%%EOF\n");
            bos.write(xref.toString().getBytes("ISO-8859-1"));

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(bos.toByteArray());
            }
        }
    }
}
