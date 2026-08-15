package org.broad.igv.session;

import org.broad.igv.AbstractHeadlessTest;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * IGV-X regression tests for the optional .igvx.json session companion metadata.
 */
public class SessionMetadataTest extends AbstractHeadlessTest {

    @Test
    public void testCompanionPathReplacesSessionExtension() {
        assertEquals("session.igvx.json",
                SessionMetadata.getCompanionPath(new File("/tmp/session.xml")).getName());
        assertEquals("session.igvx.json",
                SessionMetadata.getCompanionPath(new File("/tmp/session.igvx")).getName());
        assertEquals("session.igvx.json",
                SessionMetadata.getCompanionPath(new File("/tmp/session.session.txt")).getName());
    }

    @Test
    public void testCompanionPathAppendsForUnknownExtension() {
        assertEquals("session.bed.igvx.json",
                SessionMetadata.getCompanionPath(new File("/tmp/session.bed")).getName());
    }

    @Test
    public void testWriteAndReadRoundTrip() throws Exception {
        File dir = Files.createTempDirectory("igvx-session-meta").toFile();
        dir.deleteOnExit();
        File sessionFile = new File(dir, "my.session.xml");

        SessionMetadata.write(sessionFile, "tair10", "chr1:1000-2000", 3,
                Arrays.asList("data/a.bigWig", "data/b.bed"), true);

        File companion = SessionMetadata.getCompanionPath(sessionFile);
        assertTrue(companion.exists());

        JsonObject meta = SessionMetadata.read(sessionFile.getAbsolutePath());
        assertNotNull(meta);
        assertEquals(SessionMetadata.APP_NAME, meta.get("app").getAsString());
        assertEquals(1, meta.get("formatVersion").getAsInt());
        assertTrue(meta.get("relativePaths").getAsBoolean());
        assertEquals("tair10", meta.get("genome").getAsString());
        assertEquals(3, meta.get("trackCount").getAsInt());
        JsonArray resources = meta.getAsJsonArray("resources");
        assertEquals(2, resources.size());
        assertTrue(SessionMetadata.isRelativePaths(meta));
    }

    @Test
    public void testMissingCompanionReturnsNull() {
        assertNull(SessionMetadata.read("/tmp/definitely-missing-session.xml"));
    }

    @Test
    public void testWrongVersionReturnsNull() throws Exception {
        File dir = Files.createTempDirectory("igvx-session-meta").toFile();
        dir.deleteOnExit();
        File sessionFile = new File(dir, "old.session.xml");
        File companion = SessionMetadata.getCompanionPath(sessionFile);
        Files.write(companion.toPath(),
                "{\"formatVersion\": 99, \"app\": \"IGV-X\"}".getBytes());
        assertNull(SessionMetadata.read(sessionFile.getAbsolutePath()));
    }

    @Test
    public void testIsSessionFileCoversAllSessionExtensions() {
        // Stock IGV sessions
        assertTrue(SessionMetadata.isSessionFile("/data/foo.xml"));
        assertTrue(SessionMetadata.isSessionFile("/data/foo.php"));
        assertTrue(SessionMetadata.isSessionFile("/data/foo.php3"));
        // IGV-X native sessions
        assertTrue(SessionMetadata.isSessionFile("/data/foo.igvx"));
        assertTrue(SessionMetadata.isSessionFile("/data/foo.session"));
        assertTrue(SessionMetadata.isSessionFile("/data/foo.session.txt"));
        assertTrue(SessionMetadata.isSessionFile("/data/foo.idxsession"));
        assertTrue(SessionMetadata.isSessionFile("/data/foo.idxsession.txt"));
        // Case-insensitive
        assertTrue(SessionMetadata.isSessionFile("/data/FOO.XML"));
        assertTrue(SessionMetadata.isSessionFile("/data/FOO.IGVX"));
        // Non-sessions and degenerate inputs
        assertFalse(SessionMetadata.isSessionFile("/data/track.bigWig"));
        assertFalse(SessionMetadata.isSessionFile("/data/reads.bam"));
        assertFalse(SessionMetadata.isSessionFile(null));
        assertFalse(SessionMetadata.isSessionFile(""));
    }
}
