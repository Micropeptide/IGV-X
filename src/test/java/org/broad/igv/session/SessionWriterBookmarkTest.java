package org.broad.igv.session;

import org.broad.igv.AbstractHeadlessTest;
import org.broad.igv.feature.Bookmark;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * IGV-X regression tests: persistent bookmarks round-trip through the
 * session XML writer's writeBookmarks method (invoked reflectively to keep
 * the test focused and independent of a full IGV application instance).
 */
public class SessionWriterBookmarkTest extends AbstractHeadlessTest {

    private String serializeBookmarks(Bookmark... bookmarks) throws Exception {
        Session session = new Session("/tmp/test-session.xml");
        for (Bookmark bm : bookmarks) {
            session.addBookmark(bm);
        }

        SessionWriter writer = new SessionWriter();
        Field sessionField = SessionWriter.class.getDeclaredField("session");
        sessionField.setAccessible(true);
        sessionField.set(writer, session);

        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.newDocument();
        Element root = doc.createElement(SessionElement.SESSION);
        doc.appendChild(root);

        Method method = SessionWriter.class.getDeclaredMethod("writeBookmarks", Element.class, Document.class);
        method.setAccessible(true);
        method.invoke(writer, root, doc);

        return org.broad.igv.util.Utilities.getString(doc);
    }

    @Test
    public void testBookmarkXmlRoundTrip() throws Exception {
        Bookmark bm = new Bookmark("chr1", 1000, 5000, "promoter", new Color(255, 128, 0));
        bm.setHighlighted(true);
        Bookmark bm2 = new Bookmark("chrM", 10, 20, "organellar");

        String xml = serializeBookmarks(bm, bm2);
        assertNotNull(xml);
        assertTrue(xml.contains("<Bookmarks>"));
        assertTrue(xml.contains("<Bookmark "));
        assertTrue(xml.contains("chromosome=\"chr1\""));
        assertTrue(xml.contains("start=\"1000\""));
        assertTrue(xml.contains("end=\"5000\""));
        assertTrue(xml.contains("label=\"promoter\""));
        assertTrue(xml.contains("color=\"#ff8000\""));
        assertTrue(xml.contains("highlighted=\"true\""));
        assertTrue(xml.contains("highlighted=\"false\""));
        assertTrue(xml.contains("chromosome=\"chrM\""));
    }

    @Test
    public void testNoBookmarksWritesNothing() throws Exception {
        String xml = serializeBookmarks();
        assertNotNull(xml);
        assertFalse(xml.contains("<Bookmarks>"));
    }

    @Test
    public void testSessionBookmarkApi() {
        Session session = new Session("/tmp/test-session3.xml");
        Bookmark bm1 = new Bookmark("chr1", 1, 100, "a");
        Bookmark bm2 = new Bookmark("chr2", 5, 50, "b");
        session.addBookmark(bm1);
        session.addBookmark(bm2);
        assertEquals(2, session.getAllBookmarks().size());
        assertEquals(1, session.getBookmarks("chr1").size());
        assertEquals(1, session.getBookmarks("chr2").size());
        assertEquals(2, session.getBookmarks(org.broad.igv.Globals.CHR_ALL).size());

        java.util.List<Bookmark> toRemove = new java.util.ArrayList<>();
        toRemove.add(bm1);
        assertTrue(session.removeBookmarks(toRemove));
        assertEquals(1, session.getAllBookmarks().size());

        session.clearBookmarks();
        assertEquals(0, session.getAllBookmarks().size());
    }
}
