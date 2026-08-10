package org.broad.igv.diagnostic;

import org.broad.igv.feature.Chromosome;
import org.broad.igv.feature.genome.ChromAlias;
import org.broad.igv.feature.genome.ChromAliasSource;
import org.broad.igv.feature.genome.Genome;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * IGV-X regression tests for the diagnostics chromosome-resolution comparison:
 * the exact/case/alias matching that powers "Diagnose Track/Session" must agree
 * with the resolution rules of the IGV-X chromosome layer (never NPE, never
 * rename user files, resolve case-insensitively and via aliases).
 */
public class TrackDiagnosticsTest {

    private static Genome mockTair10Genome() {
        int[] lengths = {30427671, 19698289, 23459830, 18585056, 26975502, 154478, 366924};
        String[] names = {"chr1", "chr2", "chr3", "chr4", "chr5", "chrC", "chrM"};
        String[] refseq = {"NC_003070.9", "NC_003071.3", "NC_003074.8", "NC_003075.7", "NC_003076.8", "NC_000932.1", "NC_037304.1"};
        List<Chromosome> chromosomeList = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            chromosomeList.add(new Chromosome(i + 1, names[i], lengths[i]));
        }
        Genome genome = new Genome("tair10", chromosomeList);
        ChromAliasSource aliasSource = new ChromAliasSource() {
            @Override
            public String getChromosomeAlias(String chr, String nameSet) {
                return null;
            }

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
        };
        genome.setChromAliasSource(aliasSource);
        return genome;
    }

    @Test
    public void exactMatchIsResolved() {
        Genome genome = mockTair10Genome();
        TrackDiagnostics.ChromosomeComparison cmp =
                TrackDiagnostics.compareChromosomes(Arrays.asList("chr1", "chr2", "chrM"), genome);
        assertEquals(3, cmp.exact.size());
        assertTrue(cmp.caseMismatch.isEmpty());
        assertTrue(cmp.aliasMatched.isEmpty());
        assertTrue(cmp.missing.isEmpty());
        assertTrue(cmp.allResolved());
    }

    @Test
    public void caseMismatchIsDetectedAndResolved() {
        Genome genome = mockTair10Genome();
        // File uses uppercase Chr1..ChrM (the classic TAIR10 WGBS bigWig naming)
        TrackDiagnostics.ChromosomeComparison cmp =
                TrackDiagnostics.compareChromosomes(Arrays.asList("Chr1", "ChrM"), genome);
        assertEquals(2, cmp.caseMismatch.size());
        assertTrue(cmp.missing.isEmpty());
        assertTrue(cmp.allResolved());
    }

    @Test
    public void refseqAccessionIsResolvedViaAlias() {
        Genome genome = mockTair10Genome();
        TrackDiagnostics.ChromosomeComparison cmp =
                TrackDiagnostics.compareChromosomes(Arrays.asList("NC_003070.9", "NC_037304.1"), genome);
        assertEquals(2, cmp.aliasMatched.size());
        assertTrue(cmp.missing.isEmpty());
        assertTrue(cmp.allResolved());
    }

    @Test
    public void trulyUnknownChromosomeIsReportedMissing() {
        Genome genome = mockTair10Genome();
        TrackDiagnostics.ChromosomeComparison cmp =
                TrackDiagnostics.compareChromosomes(Arrays.asList("chr1", "chrZ"), genome);
        assertEquals(1, cmp.exact.size());
        assertEquals(1, cmp.missing.size());
        assertEquals("chrZ", cmp.missing.get(0));
        assertFalse(cmp.allResolved());
    }

    @Test
    public void mixedSessionAllResolveWithNoNpe() {
        Genome genome = mockTair10Genome();
        TrackDiagnostics.ChromosomeComparison cmp = TrackDiagnostics.compareChromosomes(
                Arrays.asList("chr1", "Chr2", "NC_003074.8", "chrC", "chrM"), genome);
        assertTrue(cmp.missing.isEmpty());
        assertTrue(cmp.allResolved());
        // chr1, chrC, chrM are exact; Chr2 is case-only; NC_003074.8 resolves via alias
        assertEquals(3, cmp.exact.size());
        assertEquals(1, cmp.caseMismatch.size());
        assertEquals(1, cmp.aliasMatched.size());
        assertEquals(5, cmp.exact.size() + cmp.caseMismatch.size() + cmp.aliasMatched.size());
    }
}
