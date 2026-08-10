package org.broad.igv.organize;

import org.broad.igv.track.Track;
import org.broad.igv.track.TrackGroup;
import org.broad.igv.ui.IGV;
import org.broad.igv.ui.panel.TrackPanel;
import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Applies {@link OrganizeRules} to the live IGV session: reorders tracks into
 * genotype groups (one {@link TrackGroup} per genotype, with a background tint
 * and border), orders tracks within each group by methylation context
 * (CG / CHG / CHH per the rule order, then tracks with no context), and sets a
 * consistent track color per context across all genotypes.
 * <p>
 * The operation is reversible by re-running with different rules; it never
 * renames user files or modifies data.
 */
public class TrackOrganizer {

    private static final Logger log = LogManager.getLogger(TrackOrganizer.class);

    /**
     * Organize all tracks in all panels of the current IGV session.
     * Must be called on the event thread.
     *
     * @param igv   the IGV instance
     * @param rules the rule set to apply
     * @return the number of tracks reorganized
     */
    public static int organize(IGV igv, OrganizeRules rules) {
        int count = 0;
        for (TrackPanel panel : igv.getTrackPanels()) {
            count += organizePanel(panel, rules);
        }
        igv.repaint();
        return count;
    }

    /**
     * IGV-X: apply the saved rules automatically after a session or batch
     * load, but only when the user enabled auto-organize (pref
     * IGVX.ORGANIZE.AUTO).  Must be called on the event thread.
     *
     * @param igv the IGV instance
     * @return the number of tracks reorganized, or 0 when disabled / no tracks
     */
    public static int autoOrganizeIfEnabled(IGV igv) {
        try {
            if (!OrganizeRules.isAutoOrganizeEnabled()) {
                return 0;
            }
            int count = organize(igv, OrganizeRules.load());
            if (count > 0) {
                log.info("Auto-organized " + count + " tracks by genotype");
            }
            return count;
        } catch (Exception e) {
            // Never let auto-organization break a load that already succeeded.
            log.warn("Auto-organize by genotype failed: " + e.getMessage(), e);
            return 0;
        }
    }

    /**
     * Rebuild one panel's track groups from the rules.
     */
    static int organizePanel(TrackPanel panel, OrganizeRules rules) {
        List<Track> tracks = panel.getTracks();
        if (tracks == null || tracks.isEmpty()) {
            return 0;
        }

        List<TrackClassifier.Classification> classified = new ArrayList<>(tracks.size());
        for (Track track : tracks) {
            String name = track.getName();
            classified.add(TrackClassifier.classify(name == null ? "" : name, rules));
        }

        List<TrackGroup> newGroups = buildGroupsForTest(tracks, classified, rules);

        // Swap groups in place (preserves panel identity / listeners).
        List<TrackGroup> groups = panel.getGroups();
        groups.clear();
        groups.addAll(newGroups);
        return tracks.size();
    }

    /**
     * Build the ordered TrackGroup list for the given tracks and their
     * classifications.  Package-visible so it can be unit tested without a
     * live IGV/TrackPanel.
     */
    static List<TrackGroup> buildGroupsForTest(List<Track> tracks,
                                                List<TrackClassifier.Classification> classified,
                                                OrganizeRules rules) {
        List<TrackGroup> newGroups = new ArrayList<>();
        if (tracks == null || classified == null || tracks.isEmpty()) {
            return newGroups;
        }

        // Build an ordered genotype list: explicit rules first (in rule order),
        // then auto-derived genotypes in first-seen order, then Ungrouped.
        List<String> genotypeOrder = new ArrayList<>();
        if (rules.getGenotypes() != null) {
            for (OrganizeRules.GenotypeRule rule : rules.getGenotypes()) {
                if (!genotypeOrder.contains(rule.getName())) {
                    genotypeOrder.add(rule.getName());
                }
            }
        }
        for (TrackClassifier.Classification c : classified) {
            if (!genotypeOrder.contains(c.genotype)) {
                genotypeOrder.add(c.genotype);
            }
        }
        if (!genotypeOrder.contains(TrackClassifier.UNGROUPED)) {
            genotypeOrder.add(TrackClassifier.UNGROUPED);
        }

        // Context order = rule order, then no-context last.
        List<String> contextOrder = new ArrayList<>();
        if (rules.getContexts() != null) {
            for (OrganizeRules.ContextRule rule : rules.getContexts()) {
                if (!contextOrder.contains(rule.getName())) {
                    contextOrder.add(rule.getName());
                }
            }
        }

        // Group tracks by (genotype, context).
        Map<String, Map<String, List<Track>>> byGenotype = new LinkedHashMap<>();
        for (int i = 0; i < tracks.size(); i++) {
            TrackClassifier.Classification c = classified.get(i);
            byGenotype.computeIfAbsent(c.genotype, k -> new LinkedHashMap<>())
                    .computeIfAbsent(c.context == null ? "" : c.context, k -> new ArrayList<>())
                    .add(tracks.get(i));
        }

        int tintIndex = 0;
        for (String genotype : genotypeOrder) {
            Map<String, List<Track>> contexts = byGenotype.get(genotype);
            if (contexts == null) {
                continue;
            }
            TrackGroup group = new TrackGroup(genotype);
            Color background = backgroundFor(genotype, rules, tintIndex);
            if (background != null) {
                group.setBackground(background);
            }
            group.setDrawBorder(true);

            for (String context : contextOrder) {
                List<Track> list = contexts.remove(context);
                if (list != null) {
                    for (Track track : list) {
                        applyContextColor(track, context, rules);
                        group.add(track);
                    }
                }
            }
            // Remaining contexts (unknown) and no-context tracks, original order.
            for (List<Track> list : contexts.values()) {
                for (Track track : list) {
                    group.add(track);
                }
            }
            if (group.size() > 0) {
                newGroups.add(group);
                tintIndex++;
            }
        }
        return newGroups;
    }

    /**
     * Background tint for a genotype: the rule's explicit color when the
     * genotype has an explicit rule, otherwise a deterministic pastel from a
     * palette (so auto-derived genotypes still get distinct backgrounds).
     */
    static Color backgroundFor(String genotype, OrganizeRules rules, int tintIndex) {
        if (rules.getGenotypes() != null) {
            for (OrganizeRules.GenotypeRule rule : rules.getGenotypes()) {
                if (genotype.equals(rule.getName())) {
                    Color c = rule.getBackground();
                    if (c != null) {
                        return c;
                    }
                }
            }
        }
        if (TrackClassifier.UNGROUPED.equals(genotype)) {
            return null;
        }
        // Deterministic pastel palette for auto-derived genotypes.
        String[] palette = {
                "#e8f0fe", "#fdeee8", "#e9f7ea", "#fdf7e3",
                "#f3e9fa", "#e6f7f6", "#fae9ef", "#eef2f7"
        };
        Color c = OrganizeRules.hexToColor(palette[Math.abs(tintIndex) % palette.length]);
        return c;
    }

    /**
     * Set a track's color to its context color (consistent across genotypes).
     * Only applied when the context rule provides an explicit color; tracks with
     * no matched context keep their current color.
     */
    static void applyContextColor(Track track, String context, OrganizeRules rules) {
        if (context == null || context.isEmpty() || rules.getContexts() == null) {
            return;
        }
        for (OrganizeRules.ContextRule rule : rules.getContexts()) {
            if (context.equals(rule.getName())) {
                Color c = rule.getColor();
                if (c != null) {
                    track.setColor(c);
                }
                return;
            }
        }
    }
}
