package org.update4j;

import org.junit.jupiter.api.Test;
import org.update4j.util.FileUtils;
import org.update4j.util.FilenameMatch;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class TestFileUtils {

    @Test
    public void testFromFilename() {
        FilenameMatch testMatch;
        testMatch = FileUtils.fromFilename("example.dat");
        assertNull(testMatch.getOs());
        assertNull(testMatch.getArch());

        testMatch = FileUtils.fromFilename("example-win.dat");
        assertEquals(OS.WINDOWS, testMatch.getOs());
        assertNull(testMatch.getArch());

        testMatch = FileUtils.fromFilename("example-win-aarch64.dat");
        assertEquals(OS.WINDOWS, testMatch.getOs());
        assertEquals("aarch64", testMatch.getArch());

        testMatch = FileUtils.fromFilename("example-not-an-os-aarch64.dat");
        assertNull(testMatch.getOs());
        assertNull(testMatch.getArch());
    }
}