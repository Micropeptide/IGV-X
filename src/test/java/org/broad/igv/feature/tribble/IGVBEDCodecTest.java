/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2007-2015 Broad Institute
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
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.broad.igv.feature.tribble;

import org.broad.igv.AbstractHeadlessTest;
import org.broad.igv.feature.IGVFeature;
import org.broad.igv.util.TestUtils;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

/**
 * User: jacob
 * Date: 2012-Dec-11
 */

public class IGVBEDCodecTest extends AbstractHeadlessTest {

    @Test
    public void testTabDelimited() throws Exception {
        String line = "chr1\t1051161\t1051177\tstrong GGGCGGGTGGGGCGGG\t0.81";
        IGVBEDCodec codec = new IGVBEDCodec();
        IGVFeature feature =  codec.decode(line);
        assertEquals("strong GGGCGGGTGGGGCGGG", feature.getName());
    }

    /**
     * IGV-X regression: a BED file whose chromosome is Chr1 (capital C, as produced by
     * some pipelines) must canonicalize onto the genome's chr1 (lowercase) when the
     * codec is constructed with that genome.  Guards the genome-layer canonicalization
     * path used by all tribble codecs (BED/VCF/GFF/PSL/...).
     */
    @Test
    public void testChrNameCapitalizationCanonicalization() throws Exception {
        String line = "Chr1\t1051161\t1051177\tcapital_chr_test\t0.81";
        IGVBEDCodec codec = new IGVBEDCodec(TestUtils.mockUCSCGenome());
        IGVFeature feature = codec.decode(line);
        assertEquals("capital Chr1 must canonicalize to lowercase chr1", "chr1", feature.getChr());
    }

    @Test
    public void testMultiTabDelimited() throws Exception {
        String line = "chr1\t1051161\t\t\t1051177\tstrong GGGCGGGTGGGGCGGG\t0.81";
        IGVBEDCodec codec = new IGVBEDCodec();
        IGVFeature feature =  codec.decode(line);
        assertEquals("strong GGGCGGGTGGGGCGGG", feature.getName());
    }

    @Test
    public void testSpaceDelimited() throws Exception {
        String line = "chr1 1051161 1051177 strong_GGGCGGGTGGGGCGGG 0.81";
        IGVBEDCodec codec = new IGVBEDCodec();
        IGVFeature feature =  codec.decode(line);
        assertEquals("strong_GGGCGGGTGGGGCGGG", feature.getName());
    }

}
