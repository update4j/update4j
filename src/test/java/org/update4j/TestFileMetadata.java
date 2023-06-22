package org.update4j;

import org.junit.jupiter.api.Test;
import org.update4j.util.FileUtils;
import org.update4j.util.FilenameMatch;

import java.io.File;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

public class TestFileMetadata {

    @Test
    public void testAppliesToCurrentPlatform() {
        OS currentOs = OS.CURRENT;
        // Doesn't matter what value here, just something that is not the current OS
        OS otherOs = OS.CURRENT == OS.WINDOWS ? OS.MAC : OS.WINDOWS;

        String currentArch = System.getProperty("os.arch");
        String otherArch = "fakeArch";

        assertTrue(placeholderBuilder().os(null).arch(null).build().appliesToCurrentPlatform());
        assertTrue(placeholderBuilder().os(currentOs).arch(null).build().appliesToCurrentPlatform());
        assertFalse(placeholderBuilder().os(otherOs).arch(null).build().appliesToCurrentPlatform());
        assertTrue(placeholderBuilder().os(currentOs).arch(currentArch).build().appliesToCurrentPlatform());
        assertFalse(placeholderBuilder().os(currentOs).arch(otherArch).build().appliesToCurrentPlatform());
        assertFalse(placeholderBuilder().os(otherOs).arch(currentArch).build().appliesToCurrentPlatform());

        // metadata with an arch but no os is forbidden
        assertThrows(IllegalArgumentException.class,
                () -> placeholderBuilder().os(null).arch(currentArch).build().appliesToCurrentPlatform());
    }

    private FileMetadata.Builder placeholderBuilder() {
        return FileMetadata.builder().uri(URI.create("http://localhost/placeholder")).path(new File("/tmp/placeholder").toPath());
    }
}