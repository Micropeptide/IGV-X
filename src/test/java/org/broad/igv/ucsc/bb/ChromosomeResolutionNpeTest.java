package org.broad.igv.ucsc.bb;

import org.broad.igv.data.DataTile;
import org.broad.igv.feature.LocusScore;
import org.broad.igv.feature.Chromosome;
import org.broad.igv.feature.genome.ChromAlias;
import org.broad.igv.feature.genome.ChromAliasSource;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.util.TestUtils;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Regression tests for the IGV-X chromosome-resolution layer.
 *
 * Upstream IGV 2.19.5 crashes with a NullPointerException on TAIR10 WGBS sessions:
 * bigWig tracks name their chromosomes Chr1..ChrM while the tair10 genome exposes
 * canonical names like NC_003070.9 / chr1.  BBDataSource.getPrecomputedSummaryScores()
 * unboxes BBFile.getIdForChr() without a null check, so a missing mapping NPEs on
 * every mouse move over the track.
 *
 * IGV-X rule: a missing chromosome mapping must NEVER throw a NullPointerException;
 * it must resolve case-insensitively / alias-aware when possible, and otherwise
 * degrade gracefully (empty data, no crash).
 */
public class ChromosomeResolutionNpeTest {

    /**
     * Build a tair10-like genome: canonical names chr1..chr5, chrC, chrM with RefSeq
     * accessions as aliases (like the real tair10 genome in IGV).  Lengths are the
     * real TAIR10 chromosome sizes.
     */
    private static Genome mockTair10Genome() {
        int[] lengths = {30427671, 19698289, 23459830, 18585056, 26975502, 154478, 366924};
        String[] names = {"chr1", "chr2", "chr3", "chr4", "chr5", "chrC", "chrM"};
        String[] refseq = {"NC_003070.9", "NC_003071.3", "NC_003074.8", "NC_003075.7", "NC_003076.8", "NC_000932.1", "NC_037304.1"};
        List<Chromosome> chromosomeList = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            chromosomeList.add(new Chromosome(i + 1, names[i], lengths[i]));
        }
        Genome genome = new Genome("tair10", chromosomeList);

        // Alias source: NCBI accession <-> UCSC-style name for each chromosome
        ChromAliasSource aliasSource = new ChromAliasSource() {
            @Override
            public ChromAlias search(String token) {
                for (int i = 0; i < names.length; i++) {
                    if (token.equals(names[i]) || token.equals(refseq[i])) {
                        ChromAlias record = new ChromAlias(names[i]);
                        record.put("ncbi", refseq[i]);
                        record.put("ucsc", names[i]);
                        return record;
                    }
                }
                return null;
            }

            @Override
            public String getChromosomeAlias(String chr, String nameSet) {
                ChromAlias record = search(chr);
                return record == null ? null : record.get(nameSet);
            }
        };
        genome.setChromAliasSource(aliasSource);
        return genome;
    }

    /**
     * The exact upstream crash: a bigWig whose chrom tree uses Chr1..ChrM (capital C,
     * as in Runtian's WGBS methylation bigWigs) queried with the genome's canonical
     * name chr1 (lowercase, as tair10 exposes).  Upstream unboxes null in
     * getPrecomputedSummaryScores -> NPE.  IGV-X must resolve it and never crash.
     */
    @Test
    public void testGetPrecomputedSummaryScoresCaseInsensitiveResolution() throws IOException {
        String path = TestUtils.DATA_DIR + "bb/tair10_chr_case.bigWig";
        Genome genome = mockTair10Genome();   // canonical chr1..chrM (lowercase)
        BBFile reader = new BBFile(path, genome);
        BBDataSource source = new BBDataSource(reader, genome);

        // Query with the genome's canonical name; the file uses Chr1..ChrM.
        // Before the IGV-X fix this throws NPE (unboxing null Integer).
        List<LocusScore> scores = source.getPrecomputedSummaryScores("chr1", 0, Integer.MAX_VALUE, 1);
        assertNotNull(scores);
        assertTrue(scores.size() > 0);
    }

    /**
     * Same guarantee for the raw-data path (getRawData).
     */
    @Test
    public void testGetRawDataCaseInsensitiveResolution() throws IOException {
        String path = TestUtils.DATA_DIR + "bb/tair10_chr_case.bigWig";
        Genome genome = mockTair10Genome();
        BBFile reader = new BBFile(path, genome);
        BBDataSource source = new BBDataSource(reader, genome);

        DataTile tile = source.getRawData("chr1", 0, Integer.MAX_VALUE);
        assertNotNull(tile);
        assertTrue(!tile.isEmpty());
        assertTrue(tile.getStartLocations().length > 0);
    }

    /**
     * getIdForChr itself must resolve Chr1 (file) from chr1 (genome canonical), i.e.
     * case-insensitive file-side lookup, and return null rather than throw for an
     * unmapped chromosome.
     */
    @Test
    public void testGetIdForChrCaseInsensitiveAndNullForUnmapped() throws IOException {
        String path = TestUtils.DATA_DIR + "bb/tair10_chr_case.bigWig";
        Genome genome = mockTair10Genome();
        BBFile reader = new BBFile(path, genome);

        Integer id = reader.getIdForChr("chr1");   // genome canonical, file has Chr1
        assertNotNull(id);

        Integer idUnknown = reader.getIdForChr("chrZ");   // not in file or genome
        assertNull(idUnknown);   // must return null, never throw
    }

    /**
     * RefSeq-accession bigWig: the file's chromosome tree is named entirely with NCBI
     * RefSeq accessions (NC_003070.9 ... NC_037304.1, as many published TAIR10 files
     * are).  The genome exposes chr1..chrM canonical names plus the same accessions as
     * aliases.  Resolution must happen through the genome alias record.
     */
    @Test
    public void testRefSeqAccessionBigWigAliasResolution() throws IOException {
        String path = TestUtils.DATA_DIR + "bb/tair10_refseq.bigWig";
        Genome genome = mockTair10Genome();   // canonical chr1..chrM + NC_* aliases
        BBFile reader = new BBFile(path, genome);
        BBDataSource source = new BBDataSource(reader, genome);

        // Genome canonical name chr1 must resolve to file's NC_003070.9 via alias.
        Integer id = reader.getIdForChr("chr1");
        assertNotNull("chr1 must resolve to RefSeq accession via alias", id);

        List<LocusScore> scores = source.getPrecomputedSummaryScores("chr1", 0, Integer.MAX_VALUE, 1);
        assertNotNull(scores);
        assertTrue("expected data for chr1 via alias", scores.size() > 0);

        DataTile tile = source.getRawData("chr1", 0, Integer.MAX_VALUE);
        assertNotNull(tile);
        assertTrue(!tile.isEmpty());
    }

    /**
     * Organellar case: file uses ChrC / ChrM (capital C/M, as in real WGBS output),
     * genome exposes chrC / chrM.  Case-insensitive fallback must cover organelles too,
     * not just the main chromosomes.
     */
    @Test
    public void testOrganellarCaseInsensitiveResolution() throws IOException {
        String path = TestUtils.DATA_DIR + "bb/tair10_organellar_case.bigWig";
        Genome genome = mockTair10Genome();
        BBFile reader = new BBFile(path, genome);
        BBDataSource source = new BBDataSource(reader, genome);

        Integer idC = reader.getIdForChr("chrC");
        assertNotNull("chrC must resolve to file ChrC", idC);
        Integer idM = reader.getIdForChr("chrM");
        assertNotNull("chrM must resolve to file ChrM", idM);

        // Summary path through case-insensitive resolution on a chromosome whose
        // zoom scale matches (chr1): must resolve and return data.
        List<LocusScore> scores1 = source.getPrecomputedSummaryScores("chr1", 0, Integer.MAX_VALUE, 1);
        assertNotNull(scores1);
        assertTrue("expected data for chr1 via case-insensitive resolution", scores1.size() > 0);

        // Tiny organelles (154kb/366kb) at zoom=1 legitimately fall below the file's
        // zoom reduction level, so the summary path may return null (upstream
        // behavior).  The IGV-X guarantee is: never NPE for a resolved chromosome.
        List<LocusScore> scoresC = source.getPrecomputedSummaryScores("chrC", 0, Integer.MAX_VALUE, 1);
        assertTrue(scoresC == null || !scoresC.isEmpty());   // no NPE, empty/null allowed

        // Raw-data path for organelles must resolve and return data (no zoom needed).
        DataTile tileC = source.getRawData("chrC", 0, Integer.MAX_VALUE);
        assertNotNull(tileC);
        assertTrue("expected raw data for chrC", !tileC.isEmpty());
        DataTile tileM = source.getRawData("chrM", 0, Integer.MAX_VALUE);
        assertNotNull(tileM);
        assertTrue(!tileM.isEmpty());
    }

    /**
     * Real-world RefSeq bigBed (B. subtilis ncbiGene): the file's single chromosome is
     * the accession NC_000964.3.  A genome exposing a different canonical name with
     * NC_000964.3 as an alias must still resolve.
     */
    @Test
    public void testRealRefSeqBigBedAliasResolution() throws IOException {
        String path = TestUtils.DATA_DIR + "bb/GCF_000009045.1_ASM904v1.ncbiGene.bb";
        // Genome whose canonical name is "chr" (as some assemblies label the single
        // replicon) but which carries NC_000964.3 as an alias.
        Genome genome = mockSingleChrGenome("chr", "NC_000964.3");
        BBFile reader = new BBFile(path, genome);
        BBDataSource source = new BBDataSource(reader, genome);

        Integer id = reader.getIdForChr("chr");
        assertNotNull("canonical chr must resolve to file NC_000964.3", id);

        // Reading actual features through the data source must not throw.
        List<LocusScore> scores = source.getPrecomputedSummaryScores("chr", 0, Integer.MAX_VALUE, 1);
        assertNotNull(scores);
    }

    /**
     * A genome with a single chromosome plus one RefSeq alias, used for real-world
     * RefSeq-accession fixtures (e.g. bacterial assemblies).
     */
    private static Genome mockSingleChrGenome(String canonical, String refseq) {
        List<Chromosome> chromosomeList = new ArrayList<>();
        chromosomeList.add(new Chromosome(1, canonical, 4215606));   // B. subtilis 168
        Genome genome = new Genome("bsub", chromosomeList);
        ChromAliasSource aliasSource = new ChromAliasSource() {
            @Override
            public ChromAlias search(String token) {
                if (token.equals(canonical) || token.equals(refseq)) {
                    ChromAlias record = new ChromAlias(canonical);
                    record.put("ncbi", refseq);
                    record.put("ucsc", canonical);
                    return record;
                }
                return null;
            }

            @Override
            public String getChromosomeAlias(String chr, String nameSet) {
                ChromAlias record = search(chr);
                return record == null ? null : record.get(nameSet);
            }
        };
        genome.setChromAliasSource(aliasSource);
        return genome;
    }
}
