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
}
