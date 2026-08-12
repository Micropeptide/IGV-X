package org.broad.igv.ui.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.util.HttpUtils;

import java.io.IOException;
import java.net.URL;

/**
 * IGV-X: check the GitHub releases feed (Micropeptide/IGV-X) for a newer
 * IGV-X build.  The feed is the standard GitHub Releases API:
 *
 *   https://api.github.com/repos/Micropeptide/IGV-X/releases/latest
 *
 * which returns a JSON object with tag_name (e.g. "2.19.5-igvx.1"), name,
 * body (release notes) and an assets array whose members carry
 * browser_download_url for the .dmg / .zip artifacts.
 *
 * The check is deliberately non-fatal: any network error, malformed JSON,
 * or missing asset yields FAILED / UP_TO_DATE so the user is never blocked
 * from opening the app.
 */
public class UpdateChecker {

    private static final Logger log = LogManager.getLogger(UpdateChecker.class);

    public static final String DEFAULT_FEED_URL =
            "https://api.github.com/repos/Micropeptide/IGV-X/releases/latest";

    public enum Status { UPDATE_AVAILABLE, UP_TO_DATE, FAILED }

    /**
     * Immutable result of an update check.
     */
    public static class UpdateInfo {
        public final Status status;
        public final String version;      // release tag without leading 'v', e.g. 2.19.5-igvx.1 (UPDATE_AVAILABLE only)
        public final String notes;        // release body / notes (may be empty)
        public final String dmgUrl;       // direct .dmg download URL (may be null)
        public final String zipUrl;       // direct .zip download URL (may be null)
        public final String releaseUrl;   // GitHub release page (always present)

        private UpdateInfo(Status status, String version, String notes, String dmgUrl, String zipUrl, String releaseUrl) {
            this.status = status;
            this.version = version;
            this.notes = notes;
            this.dmgUrl = dmgUrl;
            this.zipUrl = zipUrl;
            this.releaseUrl = releaseUrl;
        }

        static UpdateInfo upToDate() {
            return new UpdateInfo(Status.UP_TO_DATE, null, null, null, null, null);
        }

        static UpdateInfo failed() {
            return new UpdateInfo(Status.FAILED, null, null, null, null, null);
        }
    }

    /**
     * Query the feed URL and return the update status.  Never throws.
     */
    public static UpdateInfo checkForUpdate(String feedUrl, String currentVersion) {
        try {
            HttpUtils httpUtils = HttpUtils.getInstance();
            String json = httpUtils.getContentsAsJSON(new URL(feedUrl));
            JsonObject release = JsonParser.parseString(json).getAsJsonObject();

            String tag = release.get("tag_name") == null ? "" : release.get("tag_name").getAsString();
            String version = stripLeadingV(tag);
            if (version.isEmpty()) {
                log.warn("Update feed has no tag_name");
                return UpdateInfo.failed();
            }
            if (compareVersions(currentVersion, version) >= 0) {
                return UpdateInfo.upToDate();  // already up to date (or feed older)
            }

            String notes = release.get("body") == null ? "" : release.get("body").getAsString();
            String releaseUrl = release.get("html_url") == null ?
                    "https://github.com/Micropeptide/IGV-X/releases" : release.get("html_url").getAsString();
            String dmgUrl = null;
            String zipUrl = null;
            JsonElement assetsEl = release.get("assets");
            if (assetsEl != null && assetsEl.isJsonArray()) {
                JsonArray assets = assetsEl.getAsJsonArray();
                for (JsonElement el : assets) {
                    JsonObject asset = el.getAsJsonObject();
                    String name = asset.get("name") == null ? "" : asset.get("name").getAsString();
                    String url = asset.get("browser_download_url") == null ? null : asset.get("browser_download_url").getAsString();
                    if (url == null) continue;
                    if (name.toLowerCase().endsWith(".dmg") && dmgUrl == null) dmgUrl = url;
                    else if (name.toLowerCase().endsWith(".zip") && zipUrl == null) zipUrl = url;
                }
            }
            if (dmgUrl == null && zipUrl == null) {
                log.warn("Update feed has no .dmg or .zip asset for " + version);
                return UpdateInfo.failed();
            }
            return new UpdateInfo(Status.UPDATE_AVAILABLE, version, notes, dmgUrl, zipUrl, releaseUrl);

        } catch (IOException | RuntimeException e) {
            log.warn("Update check failed: " + e.getMessage());
            return UpdateInfo.failed();
        }
    }

    /**
     * Compare two dotted version strings (optionally with a suffix such as
     * "-igvx.1").  Returns &lt;0 when a &lt; b, 0 when equal, &gt;0 when a &gt; b.
     * Numeric segments compare numerically; the non-numeric tail compares
     * lexically.  Missing segments count as 0 / empty.
     */
    public static int compareVersions(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        String[] pa = a.replace('-', '.').split("\\.");
        String[] pb = b.replace('-', '.').split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            String sa = i < pa.length ? pa[i] : "";
            String sb = i < pb.length ? pb[i] : "";
            int cmp = compareSegment(sa, sb);
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    private static int compareSegment(String a, String b) {
        Integer na = tryParseInt(a);
        Integer nb = tryParseInt(b);
        if (na != null && nb != null) return Integer.compare(na, nb);
        if (na != null && b.isEmpty()) return Integer.compare(na, 0);
        if (nb != null && a.isEmpty()) return Integer.compare(0, nb);
        if (a.isEmpty() && b.isEmpty()) return 0;
        if (a.isEmpty()) return -1;
        if (b.isEmpty()) return 1;
        return a.compareToIgnoreCase(b);
    }

    private static Integer tryParseInt(String s) {
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static String stripLeadingV(String tag) {
        String t = tag.trim();
        return t.startsWith("v") || t.startsWith("V") ? t.substring(1) : t;
    }
}
