package org.broad.igv.organize;

import java.awt.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies a track name into a genotype + methylation-context bucket using
 * the user-editable {@link OrganizeRules}.  Pure logic, no UI or IGV state,
 * so it can be unit-tested directly.
 * <p>
 * Classification algorithm:
 * <ol>
 *   <li><b>Context</b>: scan {@code rules.contexts} in order; the first
 *       context rule whose regex matches the track name wins.  The match is
 *       searched anywhere in the name (case-insensitively for the default
 *       patterns).</li>
 *   <li><b>Genotype</b>: scan {@code rules.genotypes} in order; the first
 *       genotype rule whose regex matches wins.  If no rule matches, the
 *       genotype is auto-derived from the name prefix before the matched
 *       context token (or the whole base name when no context matched).</li>
 * </ol>
 * A track that matches no context rule keeps its default color; a track that
 * matches no genotype rule is placed in the "Ungrouped" bucket.
 */
public class TrackClassifier {

    /** Display name used when a track's genotype cannot be determined. */
    public static final String UNGROUPED = "Ungrouped";

    /** Result of classifying one track name. */
    public static class Classification {
        public final String trackName;
        public final String genotype;
        public final String context;
        public final Color contextColor;
        public final Color background;
        public final boolean autoGenotype;

        public Classification(String trackName, String genotype, String context,
                              Color contextColor, Color background, boolean autoGenotype) {
            this.trackName = trackName;
            this.genotype = genotype;
            this.context = context;
            this.contextColor = contextColor;
            this.background = background;
            this.autoGenotype = autoGenotype;
        }
    }

    /**
     * Classify a single track name.
     */
    public static Classification classify(String trackName, OrganizeRules rules) {
        if (trackName == null) {
            trackName = "";
        }
        // 1. Context (first matching rule wins)
        String context = null;
        Color contextColor = null;
        int contextIndex = -1;
        if (rules.getContexts() != null) {
            for (OrganizeRules.ContextRule rule : rules.getContexts()) {
                Matcher m = match(rule.getPattern(), trackName);
                if (m != null && m.find()) {
                    context = rule.getName();
                    contextColor = rule.getColor();
                    contextIndex = m.start();
                    break;
                }
            }
        }

        // 2. Genotype (first matching rule wins)
        String genotype = null;
        Color background = null;
        boolean autoGenotype = false;
        if (rules.getGenotypes() != null) {
            for (OrganizeRules.GenotypeRule rule : rules.getGenotypes()) {
                Matcher m = match(rule.getPattern(), trackName);
                if (m != null && m.find()) {
                    genotype = rule.getName();
                    background = rule.getBackground();
                    break;
                }
            }
        }

        if (genotype == null) {
            // Auto-derive a genotype only when a context token was found (the
            // name prefix before it is the genotype).  Tracks with no context
            // and no matching genotype rule are Ungrouped - we do not guess a
            // genotype from an arbitrary name.
            if (context != null && contextIndex >= 0) {
                genotype = deriveGenotype(trackName, context, contextIndex);
                autoGenotype = true;
            }
            if (genotype == null) {
                genotype = UNGROUPED;
            }
        }

        return new Classification(trackName, genotype, context, contextColor, background, autoGenotype);
    }

    /**
     * Derive a genotype name from the track name.  When a context token was
     * matched at {@code contextIndex}, everything before it (minus separators
     * and file extensions) is the genotype.  Otherwise the whole base name is
     * used.  Returns null when nothing usable remains.
     */
    static String deriveGenotype(String trackName, String context, int contextIndex) {
        String candidate;
        if (context != null && contextIndex >= 0) {
            candidate = trackName.substring(0, contextIndex);
        } else {
            candidate = trackName;
        }
        // Strip file extension(s)
        while (true) {
            int dot = candidate.lastIndexOf('.');
            if (dot <= 0) {
                break;
            }
            String ext = candidate.substring(dot + 1);
            if (ext.length() > 0 && ext.length() <= 5 && isAlphaNumeric(ext)) {
                candidate = candidate.substring(0, dot);
            } else {
                break;
            }
        }
        // Strip trailing separators and whitespace
        candidate = candidate.replaceAll("[\\s_\\.\\-:]+", " ").trim();
        candidate = candidate.replaceAll("\\s+", " ");
        return candidate.isEmpty() ? null : candidate;
    }

    private static boolean isAlphaNumeric(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
    }

    private static Matcher match(String pattern, String name) {
        if (pattern == null || pattern.isEmpty()) {
            return null;
        }
        try {
            return Pattern.compile(pattern).matcher(name);
        } catch (Exception e) {
            return null;
        }
    }
}
