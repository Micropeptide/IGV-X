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

package org.broad.igv.util;

import org.junit.Test;
import org.junit.Assume;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by IntelliJ IDEA.
 * User: jrobinso
 * Date: Jan 12, 2010
 * Time: 12:01:15 PM
 * To change this template use File | Settings | File Templates.
 */
public class FileUtilsTest {


    @Test
    public void testFindRelativeHttpPath() throws IOException {
        String basePath = "http://foo.bar.com/baseDir/";
        String targetPath = "http://foo.bar.com/baseDir/dir/test.txt";
        String relPath = FileUtils.getRelativePath(basePath, targetPath, "/");
       System.out.println(relPath);
    }

    @Test
    public void testFindRelativePath() throws IOException {
        String sep = System.getProperty("file.separator");
        File basePath = new File("src");
        File targetPath = new File("lib" + sep + "vcf.jar");
        String relPath = FileUtils.getRelativePath(basePath.getAbsolutePath(), targetPath.getAbsolutePath(), sep);
        assertEquals( ".." + sep + "lib" + sep + "vcf.jar", relPath);
    }

    @Test
    public void testGetRelativePathsUnix() {
        assertEquals("stuff/xyz.dat", FileUtils.getRelativePath("/var/data/", "/var/data/stuff/xyz.dat",  "/"));
        assertEquals("../../b/c", FileUtils.getRelativePath( "/a/x/y/","/a/b/c", "/"));
        assertEquals("../../b/c", FileUtils.getRelativePath( "/m/n/o/a/x/y/", "/m/n/o/a/b/c", "/"));
    }

    /**
     * IGV-X regression test: a session saved next to its data file must come out
     * relative even when the session's own path reaches the shared directory
     * through a symlink (e.g. a real Mac's ~/Desktop or ~/Documents under iCloud
     * Drive) while the data file's path was resolved directly, or vice versa.
     * Before the fix, {@code FileUtils.getRelativePath} compared the two raw
     * (non-canonicalized) absolute paths textually; a symlink on either side means
     * they share no common path element, and the method falls back to returning
     * the target's absolute path -- exactly the "session still has absolute paths
     * after Save" bug report this guards against.
     */
    @Test
    public void testGetRelativePathThroughSymlink() throws IOException {
        Path realDir = Files.createTempDirectory("igvx-relpath-real-");
        Path linkDir = realDir.getParent().resolve("igvx-relpath-link-" + System.nanoTime());
        try {
            Files.createSymbolicLink(linkDir, realDir);
        } catch (IOException | UnsupportedOperationException e) {
            // Some CI/sandbox environments (or Windows without the privilege) can't
            // create symlinks; the fix is inert but harmless there, so skip rather
            // than fail.
            Assume.assumeNoException("Symlinks not supported in this environment", e);
            return;
        }
        try {
            File dataFile = new File(realDir.toFile(), "data.bw");
            dataFile.createNewFile();

            // Session path resolved through the symlink; data file resolved directly.
            File sessionViaLink = new File(linkDir.toFile(), "session.xml");
            String rel = FileUtils.getRelativePath(sessionViaLink.getAbsolutePath(), dataFile.getAbsolutePath());
            assertEquals("Session reached via symlink, data file direct", "data.bw", rel);

            // The reverse: data file resolved through the symlink, session direct.
            File sessionDirect = new File(realDir.toFile(), "session.xml");
            File dataViaLink = new File(linkDir.toFile(), "data.bw");
            String rel2 = FileUtils.getRelativePath(sessionDirect.getAbsolutePath(), dataViaLink.getAbsolutePath());
            assertEquals("Session direct, data file reached via symlink", "data.bw", rel2);
        } finally {
            Files.deleteIfExists(linkDir);
            Files.deleteIfExists(realDir.resolve("data.bw"));
            Files.deleteIfExists(realDir);
        }
    }

    @Test
    public void testGetRelativePathWindows() {
        String base = "C:\\Windows\\Boot\\Fonts\\chs_boot.ttf";
        String target = "C:\\Windows\\Speech\\Common\\sapisvr.exe";

        String relPath = FileUtils.getRelativePath(target, base, "\\");
        assertEquals("..\\..\\Boot\\Fonts\\chs_boot.ttf", relPath);
    }

    @Test
    public void testGetRelativePathDirectoryToFile() {
        String base = "C:\\Windows\\Boot\\Fonts\\chs_boot.ttf";
        String target = "C:\\Windows\\Speech\\Common\\";

        String relPath = FileUtils.getRelativePath(target, base, "\\");
        assertEquals("..\\..\\Boot\\Fonts\\chs_boot.ttf", relPath);
    }

    @Test
    public void testGetRelativePathFileToDirectory() {
        String base = "C:\\Windows\\Boot\\Fonts";
        String target = "C:\\Windows\\Speech\\Common\\foo.txt";

        String relPath = FileUtils.getRelativePath(target, base, "\\");
        assertEquals("..\\..\\Boot\\Fonts", relPath);
    }

    @Test
    public void testGetRelativePathDirectoryToDirectory() {
        String base = "C:\\Windows\\Boot\\";
        String target = "C:\\Windows\\Speech\\Common\\";
        String expected = "..\\..\\Boot";

        String relPath = FileUtils.getRelativePath(target, base, "\\");
        assertEquals(expected, relPath);
    }

    @Test
    public void testGetRelativePathDifferentDriveLetters() {
        String base = "D:\\sources\\recovery\\RecEnv.exe";
        String target = "C:\\Java\\workspace\\AcceptanceTests\\Standard test data\\geo\\";
        String expected = target;

        String relPath = FileUtils.getRelativePath(base, target, "\\");
        assertEquals(expected, relPath);
    }

    @Test
    public void testGetParent() throws Exception {

        String windowsPath = "C:\\ path with spaces\\subdir\\foo.txt";
        assertEquals("C:\\ path with spaces\\subdir", FileUtils.getParent(windowsPath));

        String unixPath = "/path with spaces/subdir/foo.txt";
        assertEquals("/path with spaces/subdir", FileUtils.getParent(unixPath));

        String httpPath = "http://host:port/subdir/foo.txt";
        assertEquals("http://host:port/subdir", FileUtils.getParent(httpPath));
    }

    @Test
    public void testGetAbsolutePath() throws Exception {

        String inputPath = "test/mysession.xml";
        String referencePath = "http://foo.bar.com/bob/data/otherdata.xml";
        String absolutePath = "http://foo.bar.com/bob/data/test/mysession.xml";
        assertEquals(absolutePath, FileUtils.getAbsolutePath(inputPath, referencePath));

    }

}
