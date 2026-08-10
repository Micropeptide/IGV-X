package org.broad.igv.organize;

import org.broad.igv.organize.OrganizeRules.ContextRule;
import org.broad.igv.organize.OrganizeRules.GenotypeRule;
import org.junit.Test;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link TrackClassifier} and {@link OrganizeRules}.
 * <p>
 * The scenarios mirror Runtian's Arabidopsis WGBS naming conventions, e.g.
 * {@code col0_CG.bigWig}, {@code met1_CHG.bw}, {@code drm2_CHH.tdf}.
 */
public class TrackClassifierTest {

    private OrganizeRules rules() {
        OrganizeRules rules = OrganizeRules.createDefaults();
        List<GenotypeRule> genotypes = new ArrayList<GenotypeRule>();
        genotypes.add(new GenotypeRule("Col-0", "(?i)col0|col-0", new Color(0xEAF2FB)));
        genotypes.add(new GenotypeRule("met1", "(?i)met1", new Color(0xFBEAEA)));
        genotypes.add(new GenotypeRule("drm2", "(?i)drm2", new Color(0xEDF7ED)));
        rules.setGenotypes(genotypes);
        return rules;
    }

    @Test
    public void testDefaultsHaveThreeContexts() {
        OrganizeRules d = OrganizeRules.createDefaults();
        assertEquals(3, d.getContexts().size());
        assertEquals("CG", d.getContexts().get(0).getName());
        assertEquals("CHG", d.getContexts().get(1).getName());
        assertEquals("CHH", d.getContexts().get(2).getName());
        // Context colors are distinct
        assertNotEquals(d.getContexts().get(0).getColor(), d.getContexts().get(1).getColor());
        assertNotEquals(d.getContexts().get(1).getColor(), d.getContexts().get(2).getColor());
    }

    @Test
    public void testClassifySimple() {
        TrackClassifier.Classification c =
                TrackClassifier.classify("col0_CG.bigWig", rules());
        assertEquals("Col-0", c.genotype);
        assertEquals("CG", c.context);
        assertEquals(new Color(0xEAF2FB), c.background);
        assertEquals(new Color(31, 119, 180), c.contextColor);
        assertFalse(c.autoGenotype);
    }

    @Test
    public void testClassifyCaseInsensitiveContext() {
        TrackClassifier.Classification c =
                TrackClassifier.classify("met1_chg.bw", rules());
        assertEquals("met1", c.genotype);
        assertEquals("CHG", c.context);
        assertEquals(new Color(255, 127, 14), c.contextColor);
    }

    @Test
    public void testAutoDeriveGenotypeWhenNoRuleMatches() {
        // Unknown genotype -> auto-derived from the prefix before the context token
        TrackClassifier.Classification c =
                TrackClassifier.classify("unknownMutant_CHH.tdf", rules());
        assertEquals("unknownMutant", c.genotype);
        assertEquals("CHH", c.context);
        assertEquals(new Color(44, 160, 44), c.contextColor);
        assertTrue(c.autoGenotype);
        assertNull(c.background); // no rule -> no tint
    }

    @Test
    public void testNoContextKeepsDefaultColor() {
        TrackClassifier.Classification c =
                TrackClassifier.classify("col0_input.bw", rules());
        assertEquals("Col-0", c.genotype);
        assertNull(c.context);
        assertNull(c.contextColor);
    }

    @Test
    public void testUngroupedWhenNothingMatches() {
        TrackClassifier.Classification c =
                TrackClassifier.classify("rRNA_counts.bed", rules());
        assertEquals(TrackClassifier.UNGROUPED, c.genotype);
        assertNull(c.context);
    }

    @Test
    public void testFirstRuleWins() {
        // 'col0' matches both rules if ordered badly; first rule must win
        OrganizeRules r = rules();
        List<GenotypeRule> genotypes = new ArrayList<GenotypeRule>();
        genotypes.add(new GenotypeRule("catchall", ".*", new Color(0xEEEEEE)));
        genotypes.add(new GenotypeRule("Col-0", "(?i)col0", new Color(0xEAF2FB)));
        r.setGenotypes(genotypes);
        TrackClassifier.Classification c = TrackClassifier.classify("col0_CG.bw", r);
        assertEquals("catchall", c.genotype);
        assertEquals(new Color(0xEEEEEE), c.background);
    }

    @Test
    public void testColorRoundTrip() {
        assertEquals("#1f77b4", OrganizeRules.colorToHex(new Color(0x1f77b4)));
        assertEquals(new Color(0x1f77b4), OrganizeRules.hexToColor("#1f77b4"));
        assertEquals(new Color(0x1f77b4), OrganizeRules.hexToColor("1f77b4"));
        assertNull(OrganizeRules.hexToColor("notacolor"));
        assertNull(OrganizeRules.hexToColor(null));
    }

    @Test
    public void testJsonRoundTrip() {
        OrganizeRules r = rules();
        String json = r.toJson();
        OrganizeRules parsed = new com.google.gson.Gson().fromJson(json, OrganizeRules.class);
        assertNotNull(parsed);
        assertEquals(r.getGenotypes().size(), parsed.getGenotypes().size());
        assertEquals(r.getContexts().size(), parsed.getContexts().size());
        assertEquals("Col-0", parsed.getGenotypes().get(0).getName());
        assertEquals("#eaf2fb", parsed.getGenotypes().get(0).getBackground() == null ? null :
                OrganizeRules.colorToHex(parsed.getGenotypes().get(0).getBackground()));
        assertEquals(new Color(31, 119, 180), parsed.getContexts().get(0).getColor());
    }

    @Test
    public void testSaveLoadRoundTrip() {
        OrganizeRules r = rules();
        r.save();
        OrganizeRules loaded = OrganizeRules.load();
        assertEquals(r.getGenotypes().size(), loaded.getGenotypes().size());
        assertEquals(r.getContexts().size(), loaded.getContexts().size());
    }
}
