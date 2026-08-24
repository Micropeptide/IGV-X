package org.broad.igv.track;

import htsjdk.samtools.seekablestream.SeekableStream;
import junit.framework.TestCase;
import org.broad.igv.exceptions.HttpResponseException;
import org.broad.igv.util.TestUtils;
import org.broad.igv.util.stream.IGVSeekableStreamFactory;

public class FileFormatUtilsTest extends TestCase {

    public void testIsBAM() throws Exception {
        String path = "https://1000genomes.s3.amazonaws.com/phase3/data/HG01879/exome_alignment/HG01879.mapped.ILLUMINA.bwa.ACB.exome.20120522.bam";
        boolean b = FileFormatUtils.isBAM(path);
        assertTrue(b);

    }

    public void testDetermineFormat() throws Exception {
        String bamFIle = TestUtils.DATA_DIR + "bam/NA12878.SLX.sample.bam";
        String format = FileFormatUtils.determineFormat(bamFIle);
        assertEquals("bam", format);

        String cramFile = TestUtils.DATA_DIR + "cram/cram_with_bai_index.cram";
        format = FileFormatUtils.determineFormat(cramFile);
        assertEquals("cram", format);

        String vcfFile = TestUtils.DATA_DIR + "vcf/ex2.vcf";
        format = FileFormatUtils.determineFormat(vcfFile);
        assertEquals("vcf", format);

        String gffFile = TestUtils.DATA_DIR + "gff/gene.sorted.gff3";
        format = FileFormatUtils.determineFormat(gffFile);
        assertEquals("gff3", format);

        String tdfFile = TestUtils.DATA_DIR + "tdf/NA12878.SLX.egfr.sam.tdf";
        format = FileFormatUtils.determineFormat(tdfFile);
        assertEquals("tdf", format);

        String unknown = TestUtils.DATA_DIR + "testgzip.fasta.gz";
        format = FileFormatUtils.determineFormat(unknown);
        assertNull(format);

        String wigFile = TestUtils.DATA_DIR + "wig/dm3_var_sample.wig";
        format = FileFormatUtils.determineFormat(wigFile);
        assertEquals("wig", format);

        // Remote-hosted data LAST and graceful: igvdata.broadinstitute.org started
        // returning 403 Forbidden for this legacy TCGA file (observed 2026-08-24,
        // AmazonS3), which broke the release gate 3 builds in a row. The Broad can
        // retire hosted data at any time — skip rather than fail when it does.
        String sampleInfoFile = "http://igvdata.broadinstitute.org/data/hg18/tcga/gbm/gbmsubtypes/sampleTable.txt.gz";
        try {
            format = FileFormatUtils.determineFormat(sampleInfoFile);
            assertEquals("sampleinfo", format);
        } catch (HttpResponseException e) {
            System.out.println("testDetermineFormat: skipping remote sampleinfo check — upstream unavailable: " + e.getMessage());
        }
    }
}