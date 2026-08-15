/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
 * Copyright (c) 2026 IGV-X contributors
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
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.broad.igv.ui;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * IGV-X regression tests for the macOS app-bundle Info.plist template.
 *
 * <p>The template (scripts/mac.app/Contents/Info.plist.template) is copied by the
 * Gradle createMacAppDist task into the built .app bundle and is what tells
 * LaunchServices that IGV session files can open in IGV-X (double-click and
 * Finder "Open With"). If someone removes or breaks those registrations, the
 * feature silently disappears, so these tests pin the required keys.</p>
 */
public class InfoPlistTemplateTest {

    private static String readTemplate() throws Exception {
        File f = new File("scripts/mac.app/Contents/Info.plist.template");
        if (!f.exists()) {
            f = new File("../scripts/mac.app/Contents/Info.plist.template");
        }
        if (!f.exists()) {
            fail("Info.plist.template not found from " + new File(".").getAbsolutePath());
        }
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void templateRegistersDocumentTypes() throws Exception {
        String plist = readTemplate();
        assertTrue("CFBundleDocumentTypes must be declared",
                plist.contains("<key>CFBundleDocumentTypes</key>"));
        assertTrue("IGV-X session UTI must be declared as a document type",
                plist.contains("org.igvx.session"));
        assertTrue("public.xml must be declared as an editor type (Open With)",
                plist.contains("public.xml"));
        assertTrue("UTExportedTypeDeclarations must be declared",
                plist.contains("<key>UTExportedTypeDeclarations</key>"));
    }

    @Test
    public void templateRegistersNativeSessionExtensions() throws Exception {
        String plist = readTemplate();
        for (String ext : new String[]{"igvx", "session", "session.txt", "idxsession", "idxsession.txt"}) {
            assertTrue("extension " + ext + " must be registered for org.igvx.session",
                    plist.contains(ext));
        }
    }

    @Test
    public void templateKeepsIgvXIdentity() throws Exception {
        String plist = readTemplate();
        assertTrue("bundle id must stay org.igvx.IGVX (never conflict with stock IGV)",
                plist.contains("org.igvx.IGVX"));
        assertTrue("display name must stay IGV-X", plist.contains("IGV-X"));
    }
}
