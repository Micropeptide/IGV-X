package org.broad.igv.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.util.FileUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;

/**
 * IGV-X: optional companion metadata for session files (.igvx.json).
 *
 * <p>Written next to a session file on save, read back on load. The companion is
 * strictly optional: if it is missing or corrupt, the session itself still loads
 * normally (standard IGV sessions have no companion at all and must keep working).
 * It exists so IGV-X can record save-time facts (relative-path mode, resource list,
 * genome, saved-at timestamp) that diagnostics and error recovery can later use.</p>
 */
public class SessionMetadata {

    private static final Logger log = LogManager.getLogger(SessionMetadata.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final int FORMAT_VERSION = 1;
    public static final String APP_NAME = "IGV-X";
    private static final String[] SESSION_EXTENSIONS = {
            ".xml", ".igvx", ".session.txt", ".session", ".idxsession.txt", ".idxsession"
    };

    /**
     * IGV-X: canonical "is this path a session file?" test, shared by the
     * open-file routing (Finder open handler, drag-and-drop, recent-files) and
     * by {@link SessionReader}.  Case-insensitive; covers stock IGV sessions
     * (.xml, .php, .php3) and IGV-X native sessions (.igvx, .session,
     * .session.txt, .idxsession, .idxsession.txt).
     */
    public static boolean isSessionFile(String path) {
        if (path == null) {
            return false;
        }
        String p = path.trim().toLowerCase();
        return p.endsWith(".xml")
                || p.endsWith(".php")
                || p.endsWith(".php3")
                || p.endsWith(".igvx")
                || p.endsWith(".session")
                || p.endsWith(".session.txt")
                || p.endsWith(".idxsession")
                || p.endsWith(".idxsession.txt");
    }

    private SessionMetadata() {
    }

    /**
     * Companion file for a session file: replace the session extension with
     * .igvx.json, or append it when the extension is unknown. Never equals the
     * session file itself.
     */
    public static File getCompanionPath(File sessionFile) {
        String name = sessionFile.getName();
        for (String ext : SESSION_EXTENSIONS) {
            if (name.endsWith(ext)) {
                name = name.substring(0, name.length() - ext.length()) + ".igvx.json";
                return new File(sessionFile.getParentFile(), name);
            }
        }
        return new File(sessionFile.getParentFile(), name + ".igvx.json");
    }

    /**
     * Write the companion metadata next to the session file. Never throws:
     * a failed companion write must not prevent the session itself from saving.
     */
    public static void write(File sessionFile, String genomeId, String locus,
                             int trackCount, List<String> resourcePaths,
                             boolean relativePaths) {
        if (sessionFile == null) {
            return;
        }
        try {
            JsonObject root = new JsonObject();
            root.addProperty("formatVersion", FORMAT_VERSION);
            root.addProperty("app", APP_NAME);
            root.addProperty("savedAt", Instant.now().toString());
            root.addProperty("relativePaths", relativePaths);
            if (genomeId != null) {
                root.addProperty("genome", genomeId);
            }
            if (locus != null) {
                root.addProperty("locus", locus);
            }
            root.addProperty("trackCount", trackCount);
            if (resourcePaths != null && !resourcePaths.isEmpty()) {
                JsonArray arr = new JsonArray();
                for (String p : resourcePaths) {
                    arr.add(p);
                }
                root.add("resources", arr);
            }
            File companion = getCompanionPath(sessionFile);
            Path tmp = companion.toPath().resolveSibling(companion.getName() + ".tmp");
            Files.write(tmp, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
            Files.move(tmp, companion.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.warn("IGV-X: could not write session companion metadata", e);
        }
    }

    /**
     * Read the companion metadata for a session path. Returns null when the
     * companion is missing, unreadable, or not valid IGV-X metadata — the caller
     * must treat null as "no metadata" and continue loading the session.
     */
    public static JsonObject read(String sessionPath) {
        if (sessionPath == null) {
            return null;
        }
        try {
            File companion = getCompanionPath(new File(sessionPath));
            if (!companion.exists()) {
                return null;
            }
            String content = FileUtils.getContents(companion.getAbsolutePath());
            JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
            if (obj.get("formatVersion") == null || obj.get("formatVersion").getAsInt() != FORMAT_VERSION) {
                return null;
            }
            return obj;
        } catch (Exception e) {
            log.warn("IGV-X: could not read session companion metadata", e);
            return null;
        }
    }

    /**
     * True when the session file was saved with relative resource paths.
     */
    public static boolean isRelativePaths(JsonObject metadata) {
        return metadata != null && metadata.has("relativePaths") && metadata.get("relativePaths").getAsBoolean();
    }
}
