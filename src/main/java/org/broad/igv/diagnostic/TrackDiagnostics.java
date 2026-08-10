/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2026 Broad Institute, IGV-X contributors
 */
package org.broad.igv.diagnostic;

import htsjdk.tribble.FeatureReader;
import org.broad.igv.Globals;
import org.broad.igv.feature.genome.Genome;
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.logging.LogManager;
import org.broad.igv.logging.Logger;
import org.broad.igv.sam.AlignmentDataManager;
import org.broad.igv.sam.AlignmentTrack;
import org.broad.igv.track.*;
import org.broad.igv.ucsc.bb.BBFeatureSource;
import org.broad.igv.util.FileUtils;
import org.broad.igv.util.ResourceLocator;

import java.io.File;
import java.util.*;

/**
 * IGV-X: gathers diagnostic findings for a track (and optionally the whole
 * session). This is the engine behind "Diagnose Track/Session". It never
 * mutates state and never renames or modifies user files; it only inspects
 * and reports.
 *
 * The most important check is the chromosome-resolution comparison: what
 * chromosomes does the file actually contain vs what the current genome
 * knows, with exact, case-insensitive and alias-aware matching.
 */
public class TrackDiagnostics {

    private static final Logger log = LogManager.getLogger(TrackDiagnostics.class);

    /**
     * Diagnose a single track.
     */
    public static DiagnosticReport diagnose(Track track) {
        DiagnosticReport report = new DiagnosticReport(track.getName(), track.getId());
        ResourceLocator locator = getLocator(track);
        Genome genome = GenomeManager.getInstance().getCurrentGenome();

        if (locator == null) {
            report.add(new DiagnosticFinding(DiagnosticFinding.Severity.WARNING,
                    DiagnosticCategory.RESOURCE, "No resource locator",
                    "This track has no underlying file resource; some checks are unavailable."));
        } else {
            diagnoseResource(report, locator);
            diagnoseIndex(report, locator);
        }

        diagnoseChromosomes(report, track, locator, genome);
        diagnoseMemory(report);
        return report;
    }

    private static ResourceLocator getLocator(Track track) {
        if (track == null) {
            return null;
        }
        try {
            return track.getResourceLocator();
        } catch (Exception e) {
            return null;
        }
    }

    private static void diagnoseResource(DiagnosticReport report, ResourceLocator locator) {
        String path = locator.getPath();
        boolean remote = FileUtils.isRemote(path);
        boolean exists = false;
        long size = -1;
        if (remote) {
            exists = FileUtils.resourceExists(path);
        } else {
            String local = path.startsWith("file://") ? path.substring(7) : path;
            File f = new File(local);
            exists = f.exists();
            size = f.length();
        }

        if (!exists) {
            report.add(new DiagnosticFinding(DiagnosticFinding.Severity.ERROR,
                    DiagnosticCategory.RESOURCE, "Resource missing",
                    "The file cannot be found: " + path +
                            (remote ? " (remote resource — check the URL and network)" :
                            " (check that the path is correct and the file has not been moved)")));
        } else if (size == 0) {
            report.add(new DiagnosticFinding(DiagnosticFinding.Severity.ERROR,
                    DiagnosticCategory.FILE_FORMAT, "File is empty",
                    "The file has size 0 bytes: " + path + ". It is likely truncated or a failed download."));
        } else {
            report.add(new DiagnosticFinding(DiagnosticFinding.Severity.OK,
                    DiagnosticCategory.RESOURCE, "Resource accessible",
                    (remote ? "Remote resource reachable: " : "Local file present (" + size + " bytes): ") + path));
        }
    }

    private static void diagnoseIndex(DiagnosticReport report, ResourceLocator locator) {
        String path = locator.getPath();
        if (FileUtils.isRemote(path)) {
            return; // Index checks for remote resources are done by readers at load time
        }
        String lower = path.toLowerCase();

        // bigWig / bigBed / TDF have internal indexes
        if (lower.endsWith(".bw") || lower.endsWith(".bigwig") ||
                lower.endsWith(".bb") || lower.endsWith(".bigbed") ||
                lower.endsWith(".tdf")) {
            report.add(new DiagnosticFinding(DiagnosticFinding.Severity.OK,
                    DiagnosticCategory.INDEX, "Self-indexed format",
                    "bigWig/bigBed/TDF carry an internal index; no companion index file required."));
            return;
        }

        // BAM / CRAM require .bai / .crai (or .bam.bai)
        if (lower.endsWith(".bam") || lower.endsWith(".cram")) {
            String idx = lower.endsWith(".bam") ? path + ".bai" : path + ".crai";
            String alt = lower.endsWith(".bam") ?
                    path.substring(0, path.length() - 4) + ".bai" :
                    path.substring(0, path.length() - 5) + ".crai";
            boolean found = new File(idx).exists() || new File(alt).exists();
            if (found) {
                report.add(new DiagnosticFinding(DiagnosticFinding.Severity.OK,
                        DiagnosticCategory.INDEX, "Index found", "Alignment index exists."));
            } else {
                report.add(new DiagnosticFinding(DiagnosticFinding.Severity.ERROR,
                        DiagnosticCategory.INDEX, "Index missing",
                        "No alignment index found for " + path +
                                ". BAM/CRAM tracks require an index to display; create one (e.g. samtools index)."));
            }
            return;
        }

        // Tribble formats: try standard index path
        String indexPath = null;
        try {
            indexPath = ResourceLocator.indexFile(locator);
        } catch (Exception e) {
            // fall through
        }
        if (indexPath != null && !FileUtils.isRemote(indexPath)) {
            boolean exists = new File(indexPath).exists();
            if (exists) {
                report.add(new DiagnosticFinding(DiagnosticFinding.Severity.OK,
                        DiagnosticCategory.INDEX, "Index found", "Index file present: " + indexPath));
            } else {
                report.add(new DiagnosticFinding(DiagnosticFinding.Severity.WARNING,
                        DiagnosticCategory.INDEX, "Index not found",
                        "No index at " + indexPath + ". Small files may still display, but indexed access" +
                                " (fast zooming, region queries) will be unavailable."));
            }
        }
    }

    /**
     * Result of a pure chromosome comparison (file vs genome).
     */
    static class ChromosomeComparison {
        final List<String> exact = new ArrayList<>();
        final List<String> caseMismatch = new ArrayList<>();
        final List<String> aliasMatched = new ArrayList<>();
        final List<String> missing = new ArrayList<>();

        boolean allResolved() {
            return missing.isEmpty();
        }
    }

    /**
     * Pure comparison of file chromosome names against a genome: exact,
     * case-insensitive and alias-aware. Unit-testable without IGV state.
     */
    static ChromosomeComparison compareChromosomes(List<String> fileChrs, Genome genome) {
        ChromosomeComparison result = new ChromosomeComparison();
        List<String> genomeChrs = genome.getChromosomeNames();
        Set<String> genomeSet = new HashSet<>(genomeChrs);
        Set<String> genomeLower = new HashSet<>();
        for (String c : genomeChrs) {
            genomeLower.add(c.toLowerCase(Locale.ROOT));
        }
        for (String chr : fileChrs) {
            if (genomeSet.contains(chr)) {
                result.exact.add(chr);
            } else if (genomeLower.contains(chr.toLowerCase(Locale.ROOT))) {
                result.caseMismatch.add(chr);
            } else {
                String canonical = genome.getCanonicalChrName(chr);
                if (canonical != null && !canonical.equals(chr) && genomeSet.contains(canonical)) {
                    result.aliasMatched.add(chr + " -> " + canonical);
                } else {
                    result.missing.add(chr);
                }
            }
        }
        return result;
    }

    /**
     * The heart of the subsystem: compare file chromosomes with genome
     * chromosomes, using exact, case-insensitive and alias-aware matching.
     */
    static void diagnoseChromosomes(DiagnosticReport report, Track track,
                                    ResourceLocator locator, Genome genome) {
        if (genome == null) {
            report.add(new DiagnosticFinding(DiagnosticFinding.Severity.WARNING,
                    DiagnosticCategory.GENOME, "No genome loaded",
                    "Chromosome-resolution checks require a loaded genome. Load a genome (Genomes menu) and retry."));
            return;
        }

        List<String> fileChrs = getFileChromosomes(track, locator);
        if (fileChrs == null || fileChrs.isEmpty()) {
            report.add(new DiagnosticFinding(DiagnosticFinding.Severity.INFO,
                    DiagnosticCategory.GENOME, "File chromosomes unavailable",
                    "Could not read chromosome names from this track type (" +
                            (track == null ? "unknown" : track.getClass().getSimpleName()) +
                            "). This is normal for some in-memory or composite tracks."));
            return;
        }

        ChromosomeComparison cmp = compareChromosomes(fileChrs, genome);
        List<String> exact = cmp.exact;
        List<String> caseMismatch = cmp.caseMismatch;
        List<String> aliasMatched = cmp.aliasMatched;
        List<String> missing = cmp.missing;

        StringBuilder detail = new StringBuilder();
        detail.append("File has ").append(fileChrs.size()).append(" chromosome(s); genome has ")
                .append(genome.getChromosomeNames().size()).append(".");
        detail.append(" Exact matches: ").append(exact.size());
        detail.append("; case-only mismatches: ").append(caseMismatch.size());
        detail.append("; alias-resolved: ").append(aliasMatched.size());
        detail.append("; unmatched: ").append(missing.size()).append(".");

        if (!caseMismatch.isEmpty()) {
            detail.append(" Case-mismatched (file vs genome): ").append(join(caseMismatch, ", ")).append(".");
        }
        if (!aliasMatched.isEmpty()) {
            detail.append(" Alias-resolved: ").append(join(aliasMatched, ", ")).append(".");
        }
        if (!missing.isEmpty()) {
            detail.append(" NOT FOUND in genome: ").append(join(missing, ", ")).append(".");
        }

        if (missing.isEmpty() && caseMismatch.isEmpty()) {
            report.add(new DiagnosticFinding(DiagnosticFinding.Severity.OK,
                    DiagnosticCategory.GENOME, "Chromosomes match genome", detail.toString()));
        } else if (missing.isEmpty()) {
            report.add(new DiagnosticFinding(DiagnosticFinding.Severity.OK,
                    DiagnosticCategory.GENOME, "Chromosomes resolve via case/alias", detail.toString()));
        } else {
            report.add(new DiagnosticFinding(DiagnosticFinding.Severity.WARNING,
                    DiagnosticCategory.GENOME, "Some file chromosomes not in genome", detail.toString() +
                            " This is the classic cause of 'no data' tracks — if these names are synonyms," +
                            " IGV-X resolves them via the alias table; otherwise the file and genome disagree."));
        }
    }

    /**
     * Read chromosome names from the track's data source without touching
     * user files. Supports bigWig/bigBed, Tribble (BED/VCF/GFF/GTF), BAM/CRAM,
     * and TDF-backed tracks.
     */
    private static List<String> getFileChromosomes(Track track, ResourceLocator locator) {
        try {
            if (track instanceof AlignmentTrack) {
                AlignmentDataManager dm = ((AlignmentTrack) track).getDataManager();
                if (dm != null) {
                    List<String> names = dm.getSequenceNames();
                    if (names != null) return names;
                }
            }
            if (track instanceof FeatureTrack) {
                FeatureSource source = ((FeatureTrack) track).source;
                if (source instanceof BBFeatureSource) {
                    String[] names = ((BBFeatureSource) source).getChromosomeNames();
                    if (names != null) return Arrays.asList(names);
                } else if (source instanceof TribbleFeatureSource) {
                    List<String> names = ((TribbleFeatureSource) source).getChromosomeNames();
                    if (names != null && !names.isEmpty()) return names;
                }
            }
            if (track instanceof DataSourceTrack) {
                org.broad.igv.data.DataSource ds = ((DataSourceTrack) track).dataSource;
                if (ds instanceof org.broad.igv.tdf.TDFDataSource) {
                    Set<String> names = ((org.broad.igv.tdf.TDFDataSource) ds).getChromosomeNames();
                    if (names != null && !names.isEmpty()) return new ArrayList<>(names);
                }
            }
        } catch (Exception e) {
            log.warn("Diagnostics: could not read file chromosomes for " + (track == null ? "null" : track.getName()), e);
        }
        return null;
    }

    private static void diagnoseMemory(DiagnosticReport report) {
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long maxMB = rt.maxMemory() / (1024 * 1024);
        double pct = maxMB > 0 ? (100.0 * usedMB / maxMB) : 0;
        if (pct > 85) {
            report.add(new DiagnosticFinding(DiagnosticFinding.Severity.WARNING,
                    DiagnosticCategory.MEMORY, "JVM heap nearly full",
                    String.format("Heap in use: %d MB of %d MB (%.0f%%). Large sessions may slow or fail;" +
                            " increase the Xmx setting in the launcher.", usedMB, maxMB, pct)));
        } else {
            report.add(new DiagnosticFinding(DiagnosticFinding.Severity.OK,
                    DiagnosticCategory.MEMORY, "Heap OK",
                    String.format("Heap in use: %d MB of %d MB (%.0f%%).", usedMB, maxMB, pct)));
        }
    }

    private static String join(List<String> items, String sep) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (String s : items) {
            if (shown > 0) sb.append(sep);
            if (shown >= 12) {
                sb.append("... (").append(items.size() - shown).append(" more)");
                break;
            }
            sb.append(s);
            shown++;
        }
        return sb.toString();
    }
}
