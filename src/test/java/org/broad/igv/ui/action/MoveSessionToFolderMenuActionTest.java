package org.broad.igv.ui.action;

import org.broad.igv.AbstractHeadlessTest;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * IGV-X regression tests for the pure-logic helpers behind "Move Session +
 * Data Files Into Folder...". The full copy-and-rewrite flow needs a live
 * IGV instance ({@code igv.saveSession}, {@code igv.getDataResourceLocators})
 * and isn't practical to construct headlessly (consistent with the rest of
 * this test suite -- no test constructs a full IGV instance), so these cover
 * the filename-collision disambiguation and local/remote/missing-file
 * filtering in isolation via reflection.
 */
public class MoveSessionToFolderMenuActionTest extends AbstractHeadlessTest {

    private String uniqueName(String desired, Set<String> usedNames) throws Exception {
        Method m = MoveSessionToFolderMenuAction.class.getDeclaredMethod("uniqueName", String.class, Set.class);
        m.setAccessible(true);
        return (String) m.invoke(null, desired, usedNames);
    }

    @Test
    public void testUniqueNameNoCollision() throws Exception {
        Set<String> used = new LinkedHashSet<>();
        assertEquals("col0_CG.bw", uniqueName("col0_CG.bw", used));
        assertTrue(used.contains("col0_CG.bw"));
    }

    @Test
    public void testUniqueNameDisambiguatesCollision() throws Exception {
        Set<String> used = new LinkedHashSet<>();
        assertEquals("col0_CG.bw", uniqueName("col0_CG.bw", used));
        // Same filename claimed by a second, different source file (e.g. two
        // different sample folders both containing "col0_CG.bw") must not
        // silently overwrite the first copy.
        assertEquals("col0_CG (2).bw", uniqueName("col0_CG.bw", used));
        assertEquals("col0_CG (3).bw", uniqueName("col0_CG.bw", used));
    }

    @Test
    public void testUniqueNamePreservesCompoundExtension() throws Exception {
        Set<String> used = new LinkedHashSet<>();
        uniqueName("sample.bam.bai", used);
        // The disambiguator must insert before the LAST extension only, so a
        // ".bai" index file collision still looks like an index file.
        assertEquals("sample.bam (2).bai", uniqueName("sample.bam.bai", used));
    }

    @Test
    public void testAddIfLocalExistingFiltersRemoteAndMissing() throws Exception {
        Method m = MoveSessionToFolderMenuAction.class.getDeclaredMethod("addIfLocalExisting", Set.class, String.class);
        m.setAccessible(true);

        Set<File> files = new LinkedHashSet<>();
        File realFile = File.createTempFile("igvx-move-session-", ".bw");
        realFile.deleteOnExit();

        m.invoke(null, files, "https://example.com/remote.bw");
        m.invoke(null, files, "/no/such/file/on/disk.bw");
        m.invoke(null, files, (Object) null);
        m.invoke(null, files, ".");
        m.invoke(null, files, realFile.getAbsolutePath());

        assertEquals(1, files.size());
        assertTrue(files.contains(realFile));
        assertFalse(files.stream().anyMatch(f -> f.getPath().startsWith("http")));
    }
}
