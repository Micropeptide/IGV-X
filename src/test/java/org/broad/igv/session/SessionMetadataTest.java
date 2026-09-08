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
    public void testWriteWithHistoryRoundTrip() throws Exception {
        File dir = Files.createTempDirectory("igvx-session-meta").toFile();
        dir.deleteOnExit();
        File sessionFile = new File(dir, "hist.session.xml");

        JsonArray history = new JsonArray();
        history.add("Add 2 track(s)");
        history.add("Rename track");
        history.add("Remove 1 track(s)");

        SessionMetadata.write(sessionFile, "tair10", null, 3,
                Arrays.asList("data/a.bigWig"), false, history);

        JsonObject meta = SessionMetadata.read(sessionFile.getAbsolutePath());
        assertNotNull(meta);
        JsonArray readHistory = meta.getAsJsonArray("history");
        assertNotNull(readHistory);
        assertEquals(3, readHistory.size());
        assertEquals("Rename track", readHistory.get(1).getAsString());
    }

    @Test
    public void testWriteWithoutHistoryOmitsField() throws Exception {
        File dir = Files.createTempDirectory("igvx-session-meta").toFile();
        dir.deleteOnExit();
        File sessionFile = new File(dir, "nohist.session.xml");

        SessionMetadata.write(sessionFile, "tair10", null, 1,
                Arrays.asList("data/a.bigWig"), false, null);

        JsonObject meta = SessionMetadata.read(sessionFile.getAbsolutePath());
        assertNotNull(meta);
        assertFalse(meta.has("history"));
    }

    @Test
    public void testMissingCompanionReturnsNull() {
        assertNull(SessionMetadata.read("/tmp/definitely-missing-session.xml"));
    }

    @Test
    public void testFindChangedResourcesDetectsModifiedFile() throws Exception {
        File dir = Files.createTempDirectory("igvx-session-meta").toFile().getCanonicalFile();
        dir.deleteOnExit();
        File sessionFile = new File(dir, "mtime.session.xml");
        File dataFile = new File(dir, "data.bw");
        Files.write(dataFile.toPath(), "v1".getBytes());

        java.util.Map<String, Long> mtimes = new java.util.HashMap<>();
        mtimes.put("data.bw", dataFile.lastModified());
        SessionMetadata.write(sessionFile, "tair10", null, 1, Arrays.asList("data.bw"), true, null, mtimes);

        assertTrue("Unchanged file should not be flagged",
                SessionMetadata.findChangedResources(sessionFile.getAbsolutePath()).isEmpty());

        // Backdate the recorded mtime so the (unmodified) file now looks newer
        // than what was recorded -- simulating the file having been rewritten
        // after the session was saved, without needing a real filesystem
        // mtime-resolution wait.
        mtimes.put("data.bw", dataFile.lastModified() - 60_000);
        SessionMetadata.write(sessionFile, "tair10", null, 1, Arrays.asList("data.bw"), true, null, mtimes);

        java.util.List<String> changed = SessionMetadata.findChangedResources(sessionFile.getAbsolutePath());
        assertEquals(1, changed.size());
        assertEquals("data.bw", changed.get(0));
    }

    @Test
    public void testFindChangedResourcesEmptyWhenNoMtimesRecorded() throws Exception {
        File dir = Files.createTempDirectory("igvx-session-meta").toFile();
        dir.deleteOnExit();
        File sessionFile = new File(dir, "nomtime.session.xml");
        SessionMetadata.write(sessionFile, "tair10", null, 1, Arrays.asList("data.bw"), true);

        assertTrue(SessionMetadata.findChangedResources(sessionFile.getAbsolutePath()).isEmpty());
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
