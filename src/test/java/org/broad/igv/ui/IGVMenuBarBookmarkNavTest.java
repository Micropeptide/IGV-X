package org.broad.igv.ui;

import org.broad.igv.AbstractHeadlessTest;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * IGV-X regression test for a bug found while reviewing "Next/Previous
 * Bookmark": {@code chrIndexOrLast} must treat an unresolvable chromosome
 * (alt contig, naming mismatch with the genome's chromosome list) as
 * "sorts after everything", not as index -1 (which used to make the forward
 * search's {@code bIdx > curChrIdx} true for almost every bookmark, so
 * "Next Bookmark" always jumped to the very first bookmark in the session
 * instead of the one after the current, unresolvable, position).
 */
public class IGVMenuBarBookmarkNavTest extends AbstractHeadlessTest {

    private int chrIndexOrLast(List<String> chrOrder, String chr) throws Exception {
        Method m = IGVMenuBar.class.getDeclaredMethod("chrIndexOrLast", List.class, String.class);
        m.setAccessible(true);
        return (int) m.invoke(null, chrOrder, chr);
    }

    @Test
    public void testResolvableChromosomeReturnsItsIndex() throws Exception {
        List<String> chrOrder = Arrays.asList("chr1", "chr2", "chr3");
        assertEquals(0, chrIndexOrLast(chrOrder, "chr1"));
        assertEquals(2, chrIndexOrLast(chrOrder, "chr3"));
    }

    @Test
    public void testUnresolvableChromosomeSortsLast() throws Exception {
        List<String> chrOrder = Arrays.asList("chr1", "chr2", "chr3");
        // Not index -1 (which would sort BEFORE everything and break the
        // forward-search comparison) but chrOrder.size(), consistently
        // matching the sort key used to order all bookmarks.
        assertEquals(chrOrder.size(), chrIndexOrLast(chrOrder, "chrUn_alt_contig"));
        assertEquals(chrOrder.size(), chrIndexOrLast(chrOrder, "1")); // naming mismatch, e.g. "1" vs "chr1"
    }
}
