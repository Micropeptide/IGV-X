package org.broad.igv.session;

import org.broad.igv.AbstractHeadlessTest;
import org.broad.igv.util.ResourceLocator;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * IGV-X regression tests for {@link IGVSessionReader#processResource}: a
 * relative index/coverage/mapping path (which {@link SessionWriter} now
 * correctly writes, see SessionWriterTrackIdRelativePathTest) must resolve
 * back to an absolute path relative to the session's own directory, exactly
 * like the main resource path already does.
 * <p>
 * Two real bugs found and fixed here:
 * <ul>
 *   <li>{@code index="..."} was stored verbatim (never resolved against the
 *   session directory at all), unlike coverage/mapping.</li>
 *   <li>{@code coverage="..."} WAS resolved to an absolute path, but a
 *   redundant {@code setCoverage(coverage)} a few lines later clobbered it
 *   back to the raw (relative) attribute string.</li>
 * </ul>
 */
public class IGVSessionReaderResourcePathTest extends AbstractHeadlessTest {

    @SuppressWarnings("unchecked")
    private ResourceLocator processResource(File sessionFile, String path, String index, String coverage, String mapping) throws Exception {
        IGVSessionReader reader = new IGVSessionReader(null);

        Field dataFilesField = IGVSessionReader.class.getDeclaredField("dataFiles");
        dataFilesField.setAccessible(true);
        dataFilesField.set(reader, new ArrayList<ResourceLocator>());

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element element = doc.createElement(SessionElement.RESOURCE);
        element.setAttribute(SessionAttribute.PATH, path);
        element.setAttribute(SessionAttribute.TYPE, "bw");
        if (index != null) element.setAttribute(SessionAttribute.INDEX, index);
        if (coverage != null) element.setAttribute(SessionAttribute.COVERAGE, coverage);
        if (mapping != null) element.setAttribute(SessionAttribute.MAPPING, mapping);

        Session session = new Session(sessionFile.getAbsolutePath());
        reader.processResource(session, element, sessionFile.getAbsolutePath());

        Collection<ResourceLocator> dataFiles = (Collection<ResourceLocator>) dataFilesField.get(reader);
        assertFalse("processResource should have added a locator", dataFiles.isEmpty());
        return dataFiles.iterator().next();
    }

    @Test
    public void testRelativeIndexPathResolvedToAbsolute() throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("igvx-session-reader-").toFile().getCanonicalFile();
        File sessionFile = new File(dir, "session.xml");

        ResourceLocator locator = processResource(sessionFile, "data.bw", "data.bw.idx", null, null);

        assertEquals(new File(dir, "data.bw.idx").getAbsolutePath(), locator.getIndexPath());
    }

    @Test
    public void testRelativeCoveragePathResolvedToAbsoluteAndNotClobbered() throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("igvx-session-reader-").toFile().getCanonicalFile();
        File sessionFile = new File(dir, "session.xml");

        ResourceLocator locator = processResource(sessionFile, "data.bam", null, "data.bam.coverage.wig", null);

        assertEquals(new File(dir, "data.bam.coverage.wig").getAbsolutePath(), locator.getCoverage());
    }

    @Test
    public void testRelativeMappingPathResolvedToAbsolute() throws Exception {
        File dir = java.nio.file.Files.createTempDirectory("igvx-session-reader-").toFile().getCanonicalFile();
        File sessionFile = new File(dir, "session.xml");

        ResourceLocator locator = processResource(sessionFile, "data.bam", null, null, "data.bam.mapping");

        assertEquals(new File(dir, "data.bam.mapping").getAbsolutePath(), locator.getMappingPath());
    }
}
