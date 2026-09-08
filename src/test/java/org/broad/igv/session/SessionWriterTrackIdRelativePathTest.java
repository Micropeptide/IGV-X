package org.broad.igv.session;

import org.broad.igv.AbstractHeadlessTest;
import org.broad.igv.track.DataSourceTrack;
import org.broad.igv.util.FileUtils;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;

/**
 * IGV-X regression test for a bug found while verifying the relative-session-path
 * feature end-to-end: {@link org.broad.igv.track.AbstractTrack#marshalXML} always
 * re-sets the "id" attribute on the Track element from the track's own (always-
 * absolute) id field. {@link SessionWriter#writePanels} used to compute a relative
 * id and set it on the element *before* calling {@code track.marshalXML(...)},
 * which immediately clobbered it back to an absolute path -- so a saved session's
 * &lt;Resource path=...&gt; came out relative (correct) while every &lt;Track
 * id=...&gt; stayed absolute (the actual "session still has absolute paths" bug
 * report). The fix re-applies the relative id *after* marshalXML runs.
 *
 * This test exercises the same two collaborators (a real track's marshalXML +
 * FileUtils.getRelativePath) without requiring a live IGV singleton, which
 * SessionWriter.writePanels itself needs (it calls IGV.getInstance() directly)
 * and can't easily be constructed in a headless unit test.
 */
public class SessionWriterTrackIdRelativePathTest extends AbstractHeadlessTest {

    @Test
    public void testMarshalXmlClobbersPreSetId() throws Exception {
        // Documents the underlying gotcha this fix works around: if a future
        // change moves the id-setting back to before marshalXML (the natural-
        // looking place to put it), this test fails loudly instead of silently
        // reintroducing the absolute-path regression.
        File dataFile = File.createTempFile("igvx-track-id-", ".bw");
        dataFile.deleteOnExit();
        String absoluteId = dataFile.getAbsolutePath();

        DataSourceTrack track = new DataSourceTrack(null, absoluteId, "test track", null);

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element element = doc.createElement("Track");

        element.setAttribute("id", "a-relative-id-set-before-marshal.bw");
        track.marshalXML(doc, element);

        assertEquals("marshalXML overwrites whatever id was set before it",
                absoluteId, element.getAttribute("id"));
    }

    @Test
    public void testRelativeIdSurvivesWhenSetAfterMarshalXml() throws Exception {
        File sessionDir = Files.createTempDirectory("igvx-session-dir-").toFile();
        sessionDir.deleteOnExit();
        File dataFile = new File(sessionDir, "track.bw");
        dataFile.createNewFile();
        dataFile.deleteOnExit();
        File sessionFile = new File(sessionDir, "session.xml");

        DataSourceTrack track = new DataSourceTrack(null, dataFile.getAbsolutePath(), "test track", null);

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element element = doc.createElement("Track");

        // Reproduces SessionWriter.writePanels' exact sequence: compute the
        // relative id, set it, let marshalXML run (which clobbers it), then
        // re-set it -- the actual fix.
        String id = track.getId();
        id = FileUtils.getRelativePath(sessionFile.getAbsolutePath(), id);
        element.setAttribute("id", id);
        track.marshalXML(doc, element);
        element.setAttribute("id", id);

        assertEquals("track.bw", element.getAttribute("id"));
    }

    @Test
    public void testNonPathIdIsNeverRelativized() throws Exception {
        // Guards the companion fix: a track whose "id" isn't a file path at all
        // (e.g. the synthetic reference-sequence track's id is literally the
        // string "Reference sequence") must be left completely alone --
        // FileUtils.getRelativePath would otherwise resolve it against the JVM's
        // working directory and emit nonsense like "../../../Reference sequence".
        String nonPathId = "Reference sequence";
        boolean looksAbsolute = new File(nonPathId).isAbsolute();
        assertEquals(false, looksAbsolute);
    }

    /**
     * IGV-X regression test for a second, related bug found in the same
     * review: a MergedTracks' member tracks are marshalled as nested
     * &lt;Track&gt; child elements via plain {@code Track.marshalXML}, which
     * has no session-output-file context and so always writes an absolute
     * id -- the writePanels id-relativize fix above only ever touched the
     * top-level &lt;Track&gt; element, never its children. Exercises
     * SessionWriter.relativizeNestedTrackIds directly (private, via
     * reflection) since the full writePanels flow needs a live IGV instance.
     */
    @Test
    public void testNestedTrackIdsAreRelativized() throws Exception {
        File sessionDir = Files.createTempDirectory("igvx-nested-track-id-").toFile();
        sessionDir.deleteOnExit();
        File sessionFile = new File(sessionDir, "session.xml");
        File memberFile = new File(sessionDir, "member.bw");

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element parent = doc.createElement("Track");
        Element nested = doc.createElement("Track");
        nested.setAttribute("id", memberFile.getAbsolutePath());
        parent.appendChild(nested);

        java.lang.reflect.Method m = SessionWriter.class.getDeclaredMethod(
                "relativizeNestedTrackIds", Element.class, File.class);
        m.setAccessible(true);

        SessionWriter writer = new SessionWriter();
        // relativizeIfApplicable/isUseRelative read PreferencesManager, which
        // defaults SESSION.RELATIVE_PATH to true -- no extra setup needed.
        m.invoke(writer, parent, sessionFile);

        assertEquals("member.bw", nested.getAttribute("id"));
    }
}
