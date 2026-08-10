/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
 * Copyright (c) 2026 IGV-X contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.broad.igv.ui.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * IGV-X batch track import: recursively scans a folder (and all subfolders)
 * for track files IGV can load, excluding index files and hidden files.
 * <p>
 * The scanner is deliberately pure (no Swing, no IGV singletons) so it can be
 * unit-tested headlessly.
 */
public class TrackFolderScanner {

    /** Canonical lowercase extensions IGV can load as track data. */
    public static final Set<String> TRACK_EXTENSIONS = new HashSet<>(Arrays.asList(
            // bigWig / bigBed
            "bw", "bigwig", "bb", "bigbed", "biggene",
            // alignment
            "bam", "cram", "sam", "bed", "bedgraph", "bg", "wig", "tdf", "cn", "xcn", "seg",
            // variants / annotations
            "vcf", "gff", "gff3", "gtf", "bedpe", "psl", "maf", "gct", "tab", "txt", "snp", "repeats"));

    /** Index extensions that accompany track files but must NOT be loaded as tracks. */
    private static final Set<String> INDEX_EXTENSIONS = new HashSet<>(Arrays.asList(
            "bai", "crai", "tbi", "idx", "csi", "fai", "gzi", "dict"));

    /** A discovered track file and its human-readable type label. */
    public static class TrackFile {
        public final File file;
        public final String type;

        TrackFile(File file, String type) {
            this.file = file;
            this.type = type;
        }
    }

    /**
     * Recursively scan {@code root} for loadable track files.
     *
     * @return discovered files, sorted by path for stable UI ordering
     */
    public static List<TrackFile> scan(File root) {
        List<TrackFile> results = new ArrayList<>();
        if (root != null && root.isDirectory()) {
            collect(root, results);
        }
        results.sort((a, b) -> a.file.getPath().compareTo(b.file.getPath()));
        return results;
    }

    private static void collect(File dir, List<TrackFile> results) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isHidden()) {
                continue;
            }
            if (child.isDirectory()) {
                collect(child, results);
            } else {
                String type = classify(child);
                if (type != null) {
                    results.add(new TrackFile(child, type));
                }
            }
        }
    }

    /**
     * Return a canonical type label for a track file, or null if the file is
     * not a loadable track (including index files).
     */
    public static String classify(File file) {
        String name = file.getName();
        String lower = name.toLowerCase();
        if (file.isHidden() || lower.startsWith(".")) {
            return null;
        }
        String ext = extensionOf(lower);
        if (ext == null || INDEX_EXTENSIONS.contains(ext)) {
            return null;
        }
        if (TRACK_EXTENSIONS.contains(ext)) {
            return typeLabel(ext);
        }
        return null;
    }

    private static String extensionOf(String lowerName) {
        // Handle double extensions: .bed.gz, .vcf.gz, .bigwig etc.
        if (lowerName.endsWith(".gz")) {
            String base = lowerName.substring(0, lowerName.length() - 3);
            int idx = base.lastIndexOf('.');
            if (idx >= 0) {
                String inner = base.substring(idx + 1);
                if (TRACK_EXTENSIONS.contains(inner)) {
                    return inner;
                }
            }
            return null;
        }
        int idx = lowerName.lastIndexOf('.');
        if (idx < 0) {
            return null;
        }
        return lowerName.substring(idx + 1);
    }

    private static String typeLabel(String ext) {
        switch (ext) {
            case "bw":
            case "bigwig":
                return "bigWig";
            case "bb":
            case "bigbed":
            case "biggene":
                return "bigBed";
            case "bam":
                return "BAM";
            case "cram":
                return "CRAM";
            case "sam":
                return "SAM";
            case "vcf":
                return "VCF";
            case "gff":
            case "gff3":
                return "GFF";
            case "gtf":
                return "GTF";
            case "bed":
                return "BED";
            case "bedgraph":
            case "bg":
                return "bedGraph";
            case "wig":
                return "WIG";
            case "tdf":
                return "TDF";
            case "cn":
            case "xcn":
                return "copy number";
            case "seg":
                return "SEG";
            case "psl":
                return "PSL";
            case "maf":
                return "MAF";
            case "gct":
                return "GCT";
            case "tab":
            case "txt":
                return "table";
            case "snp":
                return "SNP";
            case "repeats":
                return "repeats";
            default:
                return ext.toUpperCase();
        }
    }

    /** Distinct type labels present in a scan, sorted. */
    public static List<String> typesIn(List<TrackFile> files) {
        Set<String> types = new TreeSet<>();
        for (TrackFile tf : files) {
            types.add(tf.type);
        }
        return new ArrayList<>(types);
    }
}
