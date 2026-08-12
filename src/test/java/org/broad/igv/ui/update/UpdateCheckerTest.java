package org.broad.igv.ui.update;

import com.sun.net.httpserver.HttpServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static org.junit.Assert.*;

/**
 * Regression tests for the IGV-X update checker.
 *
 * Uses a local com.sun.net.httpserver (no network) to serve a fake GitHub
 * releases payload, so the tests are deterministic and run in CI.
 */
public class UpdateCheckerTest {

    private static HttpServer server;
    private static String baseUrl;

    private static final String SAMPLE_RELEASE = "{\n" +
            "  \"tag_name\": \"v2.19.5-igvx.1\",\n" +
            "  \"name\": \"IGV-X 2.19.5-igvx.1\",\n" +
            "  \"body\": \"* Fixed the bigWig NPE\\n* Added organize-by-genotype\",\n" +
            "  \"html_url\": \"https://github.com/Micropeptide/IGV-X/releases/tag/v2.19.5-igvx.1\",\n" +
            "  \"assets\": [\n" +
            "    {\"name\": \"IGV-X-2.19.5-igvx.1.dmg\", \"browser_download_url\": \"https://example.com/IGV-X.dmg\"},\n" +
            "    {\"name\": \"IGV-X-2.19.5-igvx.1.zip\", \"browser_download_url\": \"https://example.com/IGV-X.zip\"}\n" +
            "  ]\n" +
            "}";

    @BeforeClass
    public static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/latest", exchange -> {
            byte[] bytes = SAMPLE_RELEASE.getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.createContext("/empty", exchange -> {
            byte[] bytes = "{}".getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterClass
    public static void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    public void testVersionCompare() {
        assertTrue(UpdateChecker.compareVersions("2.19.5-igvx", "2.19.5-igvx.1") < 0);
        assertTrue(UpdateChecker.compareVersions("2.19.5-igvx.1", "2.19.5-igvx") > 0);
        assertTrue(UpdateChecker.compareVersions("2.19.5-igvx", "2.19.5-igvx") == 0);
        assertTrue(UpdateChecker.compareVersions("2.19.5-igvx", "2.19.6-igvx") < 0);
        assertTrue(UpdateChecker.compareVersions("2.19.6", "2.19.5-igvx") > 0);
        assertTrue(UpdateChecker.compareVersions("2.19", "2.19.0") == 0);
        assertTrue(UpdateChecker.compareVersions("2.19.5-igvx", "2.19.5-igvx") == 0);
        assertTrue(UpdateChecker.compareVersions("2.19.5-igvx.10", "2.19.5-igvx.9") > 0);
    }

    @Test
    public void testNewerUpdateAvailable() {
        UpdateChecker.UpdateInfo info = UpdateChecker.checkForUpdate(baseUrl + "/latest", "2.19.5-igvx");
        assertNotNull(info);
        assertEquals(UpdateChecker.Status.UPDATE_AVAILABLE, info.status);
        assertEquals("2.19.5-igvx.1", info.version);
        assertTrue(info.notes.contains("bigWig"));
        assertTrue(info.dmgUrl.endsWith(".dmg"));
        assertTrue(info.zipUrl.endsWith(".zip"));
        assertTrue(info.releaseUrl.contains("Micropeptide/IGV-X"));
    }

    @Test
    public void testAlreadyUpToDate() {
        UpdateChecker.UpdateInfo info = UpdateChecker.checkForUpdate(baseUrl + "/latest", "2.19.5-igvx.1");
        assertNotNull(info);
        assertEquals(UpdateChecker.Status.UP_TO_DATE, info.status);
    }

    @Test
    public void testNewerFeedThanCurrent() {
        UpdateChecker.UpdateInfo info = UpdateChecker.checkForUpdate(baseUrl + "/latest", "1.0");
        assertNotNull(info);
        assertEquals(UpdateChecker.Status.UPDATE_AVAILABLE, info.status);
    }

    @Test
    public void testMalformedFeedIsFailure() {
        UpdateChecker.UpdateInfo info = UpdateChecker.checkForUpdate(baseUrl + "/empty", "2.19.5-igvx");
        assertNotNull(info);
        assertEquals(UpdateChecker.Status.FAILED, info.status);
    }

    @Test
    public void testUnreachableFeedIsFailure() {
        UpdateChecker.UpdateInfo info = UpdateChecker.checkForUpdate(
                "http://127.0.0.1:1/nothing", "2.19.5-igvx");
        assertNotNull(info);
        assertEquals(UpdateChecker.Status.FAILED, info.status);
    }

    @Test
    public void testStripLeadingV() {
        assertEquals("2.19.5-igvx", UpdateChecker.stripLeadingV("v2.19.5-igvx"));
        assertEquals("2.19.5-igvx", UpdateChecker.stripLeadingV("V2.19.5-igvx"));
        assertEquals("2.19.5-igvx", UpdateChecker.stripLeadingV(" 2.19.5-igvx "));
    }
}
