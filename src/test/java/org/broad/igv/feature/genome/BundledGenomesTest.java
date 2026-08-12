/*
 * IGV-X
 *
 * Tests for the bundled-genomes installer: manifest parsing, materialization
 * (copy/gunzip of descriptor + fasta + index + alias + gene track) into the
 * genome cache layout, idempotency, and registration with the genome list.
 */

package org.broad.igv.feature.genome;

import org.broad.igv.AbstractHeadlessTest;
import org.broad.igv.feature.genome.load.GenomeConfig;
import org.broad.igv.ui.commandbar.GenomeListManager;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

public class BundledGenomesTest extends AbstractHeadlessTest {

    @Test
    public void testManifestContainsTair10() throws Exception {
        List<String> ids = BundledGenomes.readManifest();
        assertTrue("manifest should list tair10", ids.contains("tair10"));
    }

    @Test
    public void testMaterializeTair10ResourcesPresent() throws Exception {
        // The real tair10 bundle ships as compressed resources; materializing the
        // full 121MB fasta is too heavy for a unit test, so verify the resource
        // layout directly (descriptor parses, compressed sequence + gene + alias
        // resources all exist).
        java.net.URL json = BundledGenomes.class.getResource("/genomes/tair10/tair10.json");
        assertNotNull("tair10.json resource missing", json);
        java.net.URL faGz = BundledGenomes.class.getResource("/genomes/tair10/TAIR10_chr.fa.gz");
        assertNotNull("TAIR10_chr.fa.gz resource missing", faGz);
        java.net.URL fai = BundledGenomes.class.getResource("/genomes/tair10/TAIR10_chr.fa.fai");
        assertNotNull("TAIR10_chr.fa.fai resource missing", fai);
        java.net.URL genes = BundledGenomes.class.getResource("/genomes/tair10/tair10_genes.gtf.gz");
        assertNotNull("tair10_genes.gtf.gz resource missing", genes);
        java.net.URL alias = BundledGenomes.class.getResource("/genomes/tair10/tair10.chrAlias");
        assertNotNull("tair10.chrAlias resource missing", alias);

        try (java.io.InputStream is = json.openStream()) {
            String s = new String(is.readAllBytes());
            assertTrue("descriptor must name Arabidopsis", s.contains("Arabidopsis thaliana (TAIR10)"));
        }
    }

    @Test
    public void testMaterializeSyntheticGenome() throws Exception {
        File target = Files.createTempDirectory("bundled-genome-test").toFile();
        try {
            File descriptor = BundledGenomes.materialize("testgenome", target);
            assertNotNull(descriptor);
            assertTrue(descriptor.exists());

            // The gzipped fasta must be expanded to a plain .fa
            File fasta = new File(target, "testgenome.fa");
            assertTrue("fasta should be gunzipped into place", fasta.exists());
            String content = new String(Files.readAllBytes(fasta.toPath()));
            assertTrue(content.startsWith(">chr1"));

            // Alias + gene track copied
            assertTrue(new File(target, "testgenome.chrAlias").exists());
            assertTrue(new File(target, "testgenome_genes.gtf.gz").exists());

            // Idempotency: calling again returns the same descriptor without error
            File descriptor2 = BundledGenomes.materialize("testgenome", target);
            assertEquals(descriptor.getAbsolutePath(), descriptor2.getAbsolutePath());

            // Descriptor parses as a GenomeConfig
            try (java.io.FileReader reader = new java.io.FileReader(descriptor)) {
                GenomeConfig config = new com.google.gson.Gson().fromJson(reader, GenomeConfig.class);
                assertEquals("testgenome", config.getId());
                assertEquals("Test Genome (synthetic)", config.getName());
                assertEquals("testgenome.fa", config.getFastaURL());
                assertNotNull(config.getTrackConfigs());
                assertEquals(1, config.getTrackConfigs().size());
            }
        } finally {
            org.broad.igv.util.FileUtils.deleteDir(target);
        }
    }

    @Test
    public void testRegisterAddsGenomeItem() throws Exception {
        File target = Files.createTempDirectory("bundled-genome-register").toFile();
        try {
            File descriptor = BundledGenomes.materialize("testgenome", target);
            assertNotNull(descriptor);
            BundledGenomes.register(descriptor);

            GenomeListManager manager = GenomeListManager.getInstance();
            assertNotNull("testgenome should be in the genome map",
                    manager.getGenomeItemMap().get("testgenome"));
            assertEquals("Test Genome (synthetic)",
                    manager.getGenomeItemMap().get("testgenome").getDisplayableName());
        } finally {
            org.broad.igv.util.FileUtils.deleteDir(target);
        }
    }

    /**
     * Opt-in integration test: materializes the REAL tair10 bundle (gunzips the
     * 121MB fasta) and loads it through the JSON genome loader. Skipped by default
     * because it materializes ~160MB and takes a while; run explicitly with
     * {@code --tests '*BundledGenomesTest*realTair10*'} after changing the bundle.
     */
    @org.junit.Ignore("Opt-in integration test (materializes full TAIR10 fasta)")
    @Test
    public void testRealTair10BundleLoads() throws Exception {
        File target = Files.createTempDirectory("bundled-tair10-real").toFile();
        try {
            File descriptor = BundledGenomes.materialize("tair10", target);
            assertNotNull(descriptor);
            File fasta = new File(target, "TAIR10_chr.fa");
            assertTrue("TAIR10_chr.fa should be materialized", fasta.exists());

            org.broad.igv.feature.genome.load.GenomeLoader loader =
                    org.broad.igv.feature.genome.load.GenomeLoader.getLoader(descriptor.getAbsolutePath());
            org.broad.igv.feature.genome.Genome genome = loader.loadGenome();
            assertNotNull(genome);
            assertEquals("tair10", genome.getId());
            assertTrue("genome should have chromosome chr1",
                    genome.getChromosomeNames().contains("chr1"));
            assertTrue("genome should have chrM (organelle)",
                    genome.getChromosomeNames().contains("chrM"));
            // Alias resolution: Chr1 (RefSeq-style) should have an alias record
            // whose synonyms include the canonical fasta name chr1.
            org.broad.igv.feature.genome.ChromAlias aliasRecord = genome.getAliasRecord("Chr1");
            assertNotNull("Chr1 should have an alias record", aliasRecord);
            assertTrue("alias record should contain chr1", aliasRecord.values().contains("chr1"));
            // Gene track: the JSON genome path exposes bundled GTF tracks via
            // annotation resources (loaded by IGV when the genome is selected),
            // not via the legacy getGeneTrack() (which is only set by .genome archives).
            assertNotNull("gene annotation should be registered from bundled GTF",
                    genome.getAnnotationResources());
            assertEquals(1, genome.getAnnotationResources().size());
            assertTrue("annotation resource should reference the GTF",
                    genome.getAnnotationResources().get(0).getPath().endsWith("tair10_genes.gtf.gz"));
        } finally {
            org.broad.igv.util.FileUtils.deleteDir(target);
        }
    }
}
