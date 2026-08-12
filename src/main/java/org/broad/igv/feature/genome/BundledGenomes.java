package org.broad.igv.feature.genome;

import com.google.gson.Gson;
import org.broad.igv.DirectoryManager;
import org.broad.igv.Globals;
import org.broad.igv.feature.genome.load.GenomeConfig;
import org.broad.igv.feature.genome.load.TrackConfig;
import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.ui.commandbar.GenomeListManager;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * IGV-X: install bundled genomes shipped with the application (e.g. Arabidopsis TAIR10)
 * so that anyone who downloads IGV-X can use them directly, with no download step.
 * <p>
 * Compressed resources live under {@code /genomes/<id>/} on the classpath. On the first
 * launch they are expanded into the genome cache directory ({@code <igvx>/genomes/}),
 * one flat set of files per genome (descriptor + fasta + index + alias + gene track).
 * The expanded {@code <id>.json} descriptor is then picked up automatically by the
 * standard downloaded-genome scan on every launch, exactly like a downloaded genome.
 */
public class BundledGenomes {

    private static final Logger log = LogManager.getLogger(BundledGenomes.class);

    private static final String BUNDLED_GENOMES_PATH = "/genomes/";
    private static final String MANIFEST = BUNDLED_GENOMES_PATH + "manifest.txt";

    /**
     * Install all bundled genomes listed in the manifest into the genome cache directory.
     * Safe to call multiple times: genomes already present are skipped. No-op in headless
     * or testing mode so unit tests and batch/CLI runs never touch the user's cache.
     */
    public static void installBundledGenomes() {
        if (Globals.isHeadless() || Globals.isTesting()) {
            return;
        }
        try {
            File cacheDir = DirectoryManager.getGenomeCacheDirectory();
            for (String genomeId : readManifest()) {
                String id = genomeId.trim();
                if (id.isEmpty()) continue;
                File descriptor = materialize(id, cacheDir);
                if (descriptor != null) {
                    register(descriptor);
                }
            }
        } catch (IOException e) {
            log.error("Error installing bundled genomes", e);
        }
    }

    /**
     * Read the list of bundled genome ids from the manifest resource.
     */
    static List<String> readManifest() throws IOException {
        List<String> ids = new ArrayList<>();
        InputStream is = BundledGenomes.class.getResourceAsStream(MANIFEST);
        if (is == null) {
            log.warn("Bundled genomes manifest not found: " + MANIFEST);
            return ids;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) {
                    ids.add(line);
                }
            }
        }
        return ids;
    }

    /**
     * Materialize one bundled genome into {@code targetDir} (one flat set of files).
     * Skips genomes whose descriptor already exists. Returns the descriptor file, or
     * null when the bundled resources are missing.
     */
    static File materialize(String genomeId, File targetDir) throws IOException {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("Cannot create bundled genome directory: " + targetDir.getAbsolutePath());
        }

        File descriptorFile = new File(targetDir, genomeId + ".json");
        if (descriptorFile.exists()) {
            return descriptorFile;
        }

        URL descriptorResource = resource(genomeId, genomeId + ".json");
        if (descriptorResource == null) {
            log.warn("Bundled genome descriptor missing: " + genomeId);
            return null;
        }
        copyResource(descriptorResource, descriptorFile);

        // Copy/expand all files referenced by the descriptor.
        GenomeConfig config = loadConfig(descriptorFile);
        List<String> referenced = new ArrayList<>();
        if (config.getFastaURL() != null) referenced.add(config.getFastaURL());
        if (config.getIndexURL() != null) referenced.add(config.getIndexURL());
        if (config.getAliasURL() != null) referenced.add(config.getAliasURL());
        if (config.getTrackConfigs() != null) {
            for (TrackConfig tc : config.getTrackConfigs()) {
                if (tc.getUrl() != null) referenced.add(tc.getUrl());
                if (tc.getIndexURL() != null) referenced.add(tc.getIndexURL());
            }
        }
        for (String ref : referenced) {
            File out = new File(targetDir, ref);
            if (out.exists()) continue;
            // A referenced plain file may ship compressed as <name>.gz in resources.
            URL plain = resource(genomeId, ref);
            URL gz = resource(genomeId, ref + ".gz");
            if (plain != null) {
                copyResource(plain, out);
            } else if (gz != null) {
                gunzip(gz, out);
            } else {
                log.warn("Bundled genome resource missing: " + genomeId + "/" + ref);
            }
        }
        return descriptorFile;
    }

    /**
     * Register a genome descriptor with the genome list so the dropdown refreshes.
     */
    static void register(File descriptorFile) throws IOException {
        if (!descriptorFile.exists()) return;
        GenomeConfig config = loadConfig(descriptorFile);
        String id = config.getId() != null ? config.getId() : descriptorFile.getName();
        String name = config.getName() != null ? config.getName() : id;
        GenomeListManager.getInstance().addGenomeItem(
                new GenomeListItem(name, descriptorFile.getAbsolutePath(), id));
    }

    private static GenomeConfig loadConfig(File descriptorFile) throws IOException {
        try (Reader reader = new FileReader(descriptorFile)) {
            return new Gson().fromJson(reader, GenomeConfig.class);
        }
    }

    private static URL resource(String genomeId, String name) {
        return BundledGenomes.class.getResource(BUNDLED_GENOMES_PATH + genomeId + "/" + name);
    }

    private static void copyResource(URL source, File target) throws IOException {
        try (InputStream in = source.openStream()) {
            Files.copy(in, target.toPath());
        }
    }

    private static void gunzip(URL source, File target) throws IOException {
        try (InputStream in = new GZIPInputStream(source.openStream());
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        }
    }
}
