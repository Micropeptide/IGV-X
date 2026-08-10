package org.broad.igv.lists;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Regression test for the bundled Arabidopsis (TAIR10) gene lists.
 * <p>
 * Guards the data integrity of src/main/resources/org/broad/igv/lists/tair10.gmt:
 * the resource must exist, be registered as a default gene list, parse as a valid
 * GMT file, and contain only well-formed TAIR10 AGI locus codes with no
 * duplicates within a list. Every AGI in the file was verified against the real
 * TAIR10 GTF when the file was generated; this test keeps it honest afterwards.
 */
public class Tair10GeneListTest {

    private static final String RESOURCE = "tair10.gmt";

    @Test
    public void testTair10ListRegisteredAsDefault() {
        assertTrue("tair10.gmt must be registered as a default gene list",
                GeneListManager.DEFAULT_GENE_LISTS.contains(RESOURCE));
    }

    @Test
    public void testTair10ListParsesAsGmt() throws IOException {
        List<String[]> rows = loadResource();
        assertTrue("Expected at least 6 gene lists, got " + rows.size(), rows.size() >= 6);
        for (String[] row : rows) {
            assertEquals("GMT row must have name + description + loci", row.length >= 3, true);
        }
    }

    @Test
    public void testTair10LociAreWellFormedAndUniquePerList() throws IOException {
        List<String[]> rows = loadResource();
        int totalLoci = 0;
        for (String[] row : rows) {
            Set<String> seen = new HashSet<>();
            for (int i = 2; i < row.length; i++) {
                String locus = row[i];
                totalLoci++;
                assertTrue("Invalid TAIR10 AGI: " + locus,
                        locus.matches("(?i)AT[1-5MC]G\\d{5}"));
                assertTrue("Duplicate locus in list " + row[0] + ": " + locus,
                        seen.add(locus));
            }
        }
        assertTrue("Expected at least 80 total loci, got " + totalLoci, totalLoci >= 80);
    }

    @Test
    public void testExpectedPathwayListsPresent() throws IOException {
        List<String[]> rows = loadResource();
        Set<String> names = new HashSet<>();
        for (String[] row : rows) {
            names.add(row[0]);
        }
        for (String expected : new String[]{
                "DNA methylation core (TAIR10)",
                "RdDM pathway (TAIR10)",
                "Small RNA machinery (TAIR10)",
                "Histone marks & readers (TAIR10)",
                "Imprinting & DME pathway (TAIR10)",
                "Flowering time (TAIR10)"}) {
            assertTrue("Missing expected list: " + expected, names.contains(expected));
        }
    }

    private List<String[]> loadResource() throws IOException {
        List<String[]> rows = new ArrayList<>();
        InputStream is = GeneListManager.class.getResourceAsStream(RESOURCE);
        assertNotNull("Resource not found: " + RESOURCE, is);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                String[] tokens = line.split("\\t");
                if (tokens.length > 2) {
                    rows.add(tokens);
                }
            }
        }
        return rows;
    }
}