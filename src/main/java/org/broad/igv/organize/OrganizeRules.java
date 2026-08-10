package org.broad.igv.organize;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.prefs.Constants;
import org.broad.igv.prefs.PreferencesManager;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * User-editable rules that drive automatic track organization by genotype and
 * methylation context (CG / CHG / CHH).
 * <p>
 * The rule set is stored as a single JSON string in preferences and consists of:
 * <ul>
 *   <li>an ordered list of <b>genotype rules</b> — each has a display name, a
 *       regex that matches part of a track name, and a background tint color;</li>
 *   <li>an ordered list of <b>context rules</b> — each has a context name
 *       (CG / CHG / CHH ...), a regex, and a track color.  Context colors are
 *       global, so the same context has the same color in every genotype.</li>
 * </ul>
 * A track is classified by scanning the rules in order and using the first
 * match.  If no genotype rule matches, the genotype is auto-derived from the
 * track name (the part before the matched context token).  If no context rule
 * matches, the track keeps its default color.
 */
public class OrganizeRules {

    private static final Logger log = LogManager.getLogger(OrganizeRules.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Preference key holding the JSON rule set. */
    public static final String PREFS_RULES_KEY = "IGVX.ORGANIZE.RULES";
    /** Preference key: auto-organize tracks when a session / batch load completes. */
    public static final String PREFS_AUTO_KEY = "IGVX.ORGANIZE.AUTO";

    private List<GenotypeRule> genotypes;
    private List<ContextRule> contexts;

    public OrganizeRules() {
        this.genotypes = new ArrayList<GenotypeRule>();
        this.contexts = new ArrayList<ContextRule>();
    }

    public List<GenotypeRule> getGenotypes() {
        return genotypes;
    }

    public void setGenotypes(List<GenotypeRule> genotypes) {
        this.genotypes = genotypes == null ? new ArrayList<GenotypeRule>() : genotypes;
    }

    public List<ContextRule> getContexts() {
        return contexts;
    }

    public void setContexts(List<ContextRule> contexts) {
        this.contexts = contexts == null ? new ArrayList<ContextRule>() : contexts;
    }

    /**
     * Build the default rule set: the three Arabidopsis methylation contexts
     * with the conventional colors and a single catch-all genotype rule that
     * lets the classifier auto-derive genotype names.
     */
    public static OrganizeRules createDefaults() {
        OrganizeRules rules = new OrganizeRules();
        rules.contexts.add(new ContextRule("CG", "(?i)\\bCG\\b", new Color(31, 119, 180)));   // blue
        rules.contexts.add(new ContextRule("CHG", "(?i)\\bCHG\\b", new Color(255, 127, 14)));  // orange
        rules.contexts.add(new ContextRule("CHH", "(?i)\\bCHH\\b", new Color(44, 160, 44)));   // green
        // No explicit genotype rules by default -> classifier auto-derives the
        // genotype from the track name prefix before the context token.
        return rules;
    }

    /**
     * Load rules from preferences, falling back to defaults when unset or
     * unparseable.
     */
    public static OrganizeRules load() {
        String json = PreferencesManager.getPreferences().get(PREFS_RULES_KEY, null);
        if (json == null || json.trim().isEmpty()) {
            return createDefaults();
        }
        try {
            OrganizeRules rules = GSON.fromJson(json, OrganizeRules.class);
            if (rules == null) {
                return createDefaults();
            }
            if (rules.genotypes == null) {
                rules.genotypes = new ArrayList<GenotypeRule>();
            }
            if (rules.contexts == null) {
                rules.contexts = new ArrayList<ContextRule>();
            }
            return rules;
        } catch (Exception e) {
            log.error("Error parsing organize rules, using defaults", e);
            return createDefaults();
        }
    }

    /**
     * Persist these rules to preferences.  Also registers the constant in
     * {@link Constants} space so it is visible in the prefs UI if the user
     * inspects the raw preference.
     */
    public void save() {
        PreferencesManager.getPreferences().put(PREFS_RULES_KEY, GSON.toJson(this));
    }

    public static boolean isAutoOrganizeEnabled() {
        String v = PreferencesManager.getPreferences().get(PREFS_AUTO_KEY, "false");
        return Boolean.parseBoolean(v);
    }

    public static void setAutoOrganizeEnabled(boolean enabled) {
        PreferencesManager.getPreferences().put(PREFS_AUTO_KEY, Boolean.toString(enabled));
    }

    public static class GenotypeRule {
        private String name;
        private String pattern;
        private String background;

        public GenotypeRule() {
        }

        public GenotypeRule(String name, String pattern, Color background) {
            this.name = name;
            this.pattern = pattern;
            this.background = colorToHex(background);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public Color getBackground() {
            return hexToColor(background);
        }

        public void setBackground(Color color) {
            this.background = colorToHex(color);
        }
    }

    public static class ContextRule {
        private String name;
        private String pattern;
        private String color;

        public ContextRule() {
        }

        public ContextRule(String name, String pattern, Color color) {
            this.name = name;
            this.pattern = pattern;
            this.color = colorToHex(color);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public Color getColor() {
            return hexToColor(color);
        }

        public void setColor(Color color) {
            this.color = colorToHex(color);
        }
    }

    public static String colorToHex(Color c) {
        if (c == null) {
            return null;
        }
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    public static Color hexToColor(String hex) {
        if (hex == null) {
            return null;
        }
        try {
            String s = hex.trim();
            if (s.startsWith("#")) {
                s = s.substring(1);
            }
            if (s.length() == 6) {
                return new Color(Integer.parseInt(s, 16));
            }
            if (s.length() == 8) {
                return new Color(
                        Integer.parseInt(s.substring(0, 2), 16),
                        Integer.parseInt(s.substring(2, 4), 16),
                        Integer.parseInt(s.substring(4, 6), 16),
                        Integer.parseInt(s.substring(6, 8), 16));
            }
        } catch (Exception e) {
            log.error("Invalid color hex: " + hex, e);
        }
        return null;
    }

    /**
     * Round-trip through JSON (used by tests).
     */
    public String toJson() {
        return GSON.toJson(this);
    }
}
