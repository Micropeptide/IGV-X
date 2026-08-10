package org.broad.igv.organize;

import org.broad.igv.track.Track;
import org.broad.igv.track.TrackGroup;
import org.junit.Test;

import java.awt.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for {@link TrackOrganizer}: genotype ordering, context ordering,
 * background tints, and consistent per-context track colors.
 */
public class TrackOrganizerTest {

    /** Minimal Track stub via dynamic proxy (Track is an interface). */
    private Track track(String name) {
        final String[] nameHolder = {name};
        final Color[] colorHolder = {null};
        InvocationHandler h = (Object proxy, Method m, Object[] args) -> {
            switch (m.getName()) {
                case "getName": return nameHolder[0];
                case "setName": nameHolder[0] = (String) args[0]; return null;
                case "setColor": colorHolder[0] = (Color) args[0]; return null;
                case "getColor": return colorHolder[0];
                case "toString": return "Track[" + nameHolder[0] + "]";
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == args[0];
                default: return defaultValue(m.getReturnType());
            }
        };
        return (Track) Proxy.newProxyInstance(Track.class.getClassLoader(), new Class[]{Track.class}, h);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    private OrganizeRules rules() {
        OrganizeRules rules = OrganizeRules.createDefaults();
        List<OrganizeRules.GenotypeRule> genotypes = new ArrayList<>();
        genotypes.add(new OrganizeRules.GenotypeRule("Col-0", "(?i)col0", new Color(0xEAF2FB)));
        genotypes.add(new OrganizeRules.GenotypeRule("met1", "(?i)met1", new Color(0xFBEAEA)));
        rules.setGenotypes(genotypes);
        return rules;
    }

    private List<Track> tracks() {
        List<Track> list = new ArrayList<>();
        list.add(track("met1_CHH.bw"));
        list.add(track("col0_CG.bw"));
        list.add(track("col0_CHG.bw"));
        list.add(track("met1_CG.bw"));
        list.add(track("col0_CHH.bw"));
        list.add(track("unknown_CHH.bw"));   // auto-derived genotype
        list.add(track("input.bed"));        // no context, no genotype -> Ungrouped
        return list;
    }

    @Test
    public void testBackgroundFromRule() {
        assertEquals(new Color(0xEAF2FB),
                TrackOrganizer.backgroundFor("Col-0", rules(), 0));
        assertEquals(new Color(0xFBEAEA),
                TrackOrganizer.backgroundFor("met1", rules(), 1));
    }

    @Test
    public void testBackgroundAutoDerivedGetsPastel() {
        Color c = TrackOrganizer.backgroundFor("unknownMutant", rules(), 0);
        assertNotNull(c);
        // Distinct from the two explicit rule backgrounds
        assertNotEquals(new Color(0xEAF2FB), c);
        assertNotEquals(new Color(0xFBEAEA), c);
    }

    @Test
    public void testUngroupedNoBackground() {
        assertNull(TrackOrganizer.backgroundFor(TrackClassifier.UNGROUPED, rules(), 0));
    }

    @Test
    public void testApplyContextColor() {
        Track t = track("col0_CG.bw");
        TrackOrganizer.applyContextColor(t, "CG", rules());
        assertEquals(new Color(31, 119, 180), t.getColor());
        TrackOrganizer.applyContextColor(t, "CHG", rules());
        assertEquals(new Color(255, 127, 14), t.getColor());
        TrackOrganizer.applyContextColor(t, "", rules());
        assertEquals(new Color(255, 127, 14), t.getColor()); // unchanged when no context
    }

    @Test
    public void testBuildGroupsOrdering() {
        OrganizeRules rules = rules();
        List<Track> tracks = tracks();
        List<TrackClassifier.Classification> classified = new ArrayList<>();
        for (Track t : tracks) {
            classified.add(TrackClassifier.classify(t.getName(), rules));
        }
        List<TrackGroup> groups = TrackOrganizer.buildGroupsForTest(tracks, classified, rules);

        // Two explicit genotypes + auto-derived + ungrouped, in rule order
        assertEquals(4, groups.size());
        assertEquals("Col-0", groups.get(0).getName());
        assertEquals("met1", groups.get(1).getName());
        assertEquals("unknown", groups.get(2).getName());
        assertEquals(TrackClassifier.UNGROUPED, groups.get(3).getName());

        // Within Col-0: CG, CHG, CHH (rule order), colors set consistently
        TrackGroup col0 = groups.get(0);
        assertEquals(3, col0.size());
        assertEquals("CG", TrackClassifier.classify(col0.getTracks().get(0).getName(), rules).context);
        assertEquals("CHG", TrackClassifier.classify(col0.getTracks().get(1).getName(), rules).context);
        assertEquals("CHH", TrackClassifier.classify(col0.getTracks().get(2).getName(), rules).context);
        assertEquals(new Color(31, 119, 180), col0.getTracks().get(0).getColor());
        assertEquals(new Color(255, 127, 14), col0.getTracks().get(1).getColor());
        assertEquals(new Color(44, 160, 44), col0.getTracks().get(2).getColor());

        // Group backgrounds: Col-0 and met1 from rules, auto-derived pastel, ungrouped none
        assertEquals(new Color(0xEAF2FB), groups.get(0).getBackground());
        assertEquals(new Color(0xFBEAEA), groups.get(1).getBackground());
        assertNotNull(groups.get(2).getBackground());
        assertNull(groups.get(3).getBackground());
        assertTrue(groups.get(0).isDrawBorder());
    }
}
