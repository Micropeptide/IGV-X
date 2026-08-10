package org.broad.igv.ui.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Regression tests for the batch-import folder scanner: recursive discovery,
 * index-file exclusion, hidden-file exclusion, gz handling, type labels.
 */
public class TrackFolderScannerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File touch(File dir, String name) throws IOException {
        File f = new File(dir, name);
        Files.write(f.toPath(), new byte[0]);
        return f;
    }

    @Test
    public void testRecursiveScanFindsNestedTracks() throws IOException {
        File root = tmp.newFolder("root");
        touch(root, "sample1.bw");
        touch(root, "sample2.bam");
        File sub = new File(root, "deep/nested");
        assertTrue(sub.mkdirs());
        touch(sub, "sample3.bb");
        touch(sub, "sample4.vcf.gz");
        touch(sub, "ignored.png");

        List<TrackFolderScanner.TrackFile> found = TrackFolderScanner.scan(root);
        assertEquals(4, found.size());
        java.util.Set<String> types = new java.util.HashSet<>();
        for (TrackFolderScanner.TrackFile tf : found) {
            types.add(tf.type);
        }
        assertTrue(types.contains("bigWig"));
        assertTrue(types.contains("BAM"));
        assertTrue(types.contains("bigBed"));
        assertTrue(types.contains("VCF"));
    }

    @Test
    public void testIndexFilesExcluded() throws IOException {
        File root = tmp.newFolder("idx");
        touch(root, "reads.bam");
        touch(root, "reads.bam.bai");
        touch(root, "variants.vcf.gz");
        touch(root, "variants.vcf.gz.tbi");
        touch(root, "genome.fa.fai");
        touch(root, "genome.dict");

        List<TrackFolderScanner.TrackFile> found = TrackFolderScanner.scan(root);
        assertEquals(2, found.size());
    }

    @Test
    public void testHiddenFilesAndDotfilesExcluded() throws IOException {
        File root = tmp.newFolder("hidden");
        touch(root, ".hidden.bw");
        touch(root, "visible.bw");
        File sub = new File(root, ".git");
        assertTrue(sub.mkdirs());
        touch(sub, "inside.bw");

        List<TrackFolderScanner.TrackFile> found = TrackFolderScanner.scan(root);
        assertEquals(1, found.size());
        assertEquals("visible.bw", found.get(0).file.getName());
    }

    @Test
    public void testClassifyTypes() throws IOException {
        File root = tmp.newFolder("types");
        touch(root, "a.bw");
        touch(root, "b.bigwig");
        touch(root, "c.bb");
        touch(root, "d.cram");
        touch(root, "e.gff3");
        touch(root, "f.gtf");
        touch(root, "g.wig");
        touch(root, "h.bedgraph");
        touch(root, "i.tdf");

        List<TrackFolderScanner.TrackFile> found = TrackFolderScanner.scan(root);
        assertEquals(9, found.size());
        assertTrue(TrackFolderScanner.typesIn(found).contains("bigWig"));
        assertTrue(TrackFolderScanner.typesIn(found).contains("bigBed"));
        assertTrue(TrackFolderScanner.typesIn(found).contains("CRAM"));
        assertTrue(TrackFolderScanner.typesIn(found).contains("GFF"));
        assertTrue(TrackFolderScanner.typesIn(found).contains("GTF"));
        assertTrue(TrackFolderScanner.typesIn(found).contains("WIG"));
        assertTrue(TrackFolderScanner.typesIn(found).contains("bedGraph"));
        assertTrue(TrackFolderScanner.typesIn(found).contains("TDF"));
    }

    @Test
    public void testNonTrackFilesExcluded() throws IOException {
        File root = tmp.newFolder("non");
        touch(root, "data.png");
        touch(root, "archive.zip");
        touch(root, "notes.pdf");
        assertEquals(0, TrackFolderScanner.scan(root).size());
    }
}
