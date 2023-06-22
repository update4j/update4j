package org.update4j;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

public class TestConfiguration {

    @Test
    public void testDuplicateFileDetection() {
        Exception ex;

        // Duplicate plain files should fail
        ex = assertThrows(IllegalStateException.class, () -> Configuration.builder()
                .baseUri("http://example.com/")
                .file(FileMetadata.readFrom(new File("src/test/resources/example.txt").toPath().toAbsolutePath()))
                .file(FileMetadata.readFrom(new File("src/test/resources/example.txt").toPath().toAbsolutePath()))
                .build());
        assertTrue(ex.getMessage().startsWith("2 files resolve to same 'path'"));

        // One with OS and one without OS should fail
        ex = assertThrows(IllegalStateException.class, () -> Configuration.builder()
                .baseUri("http://example.com/")
                .file(FileMetadata.readFrom(new File("src/test/resources/example.txt").toPath().toAbsolutePath()))
                .file(FileMetadata.readFrom(new File("src/test/resources/example.txt").toPath().toAbsolutePath()).os(OS.LINUX))
                .build());
        assertTrue(ex.getMessage().startsWith("2 files resolve to same 'path'"));

        // Two with the same OSs should pass
        ex = assertThrows(IllegalStateException.class, () -> Configuration.builder()
                .baseUri("http://example.com/")
                .file(FileMetadata.readFrom(new File("src/test/resources/example.txt").toPath().toAbsolutePath()).os(OS.LINUX))
                .file(FileMetadata.readFrom(new File("src/test/resources/example.txt").toPath().toAbsolutePath()).os(OS.LINUX))
                .build());
        assertTrue(ex.getMessage().startsWith("2 files resolve to same 'path'"));

        // Two with different OSs should pass
        assertDoesNotThrow(() -> Configuration.builder()
                .baseUri("http://example.com/")
                .file(FileMetadata.readFrom(new File("src/test/resources/example.txt").toPath().toAbsolutePath()).os(OS.WINDOWS))
                .file(FileMetadata.readFrom(new File("src/test/resources/example.txt").toPath().toAbsolutePath()).os(OS.LINUX))
                .build());

        // One with arch and one without arch for the same OS should fail
        ex = assertThrows(IllegalStateException.class, () -> Configuration.builder()
                .baseUri("http://example.com/")
                .file(FileMetadata.readFrom(new File("src/test/resources/example.txt").toPath().toAbsolutePath()).os(OS.LINUX))
                .file(FileMetadata.readFrom(new File("src/test/resources/example.txt").toPath().toAbsolutePath()).os(OS.LINUX).arch("amd64"))
                .build());
        assertTrue(ex.getMessage().startsWith("2 files resolve to same 'path'"));

        // One with arch and one without arch for the different OS should pass
        assertDoesNotThrow(() -> Configuration.builder()
                .baseUri("http://example.com/")
                .file(FileMetadata.readFrom(new File("src/test/resources/example.txt").toPath().toAbsolutePath()).os(OS.WINDOWS))
                .file(FileMetadata.readFrom(new File("src/test/resources/example.txt").toPath().toAbsolutePath()).os(OS.LINUX).arch("amd64"))
                .build());

        // Two with the same arch but different OSs should pass
        assertDoesNotThrow(() -> Configuration.builder()
                .baseUri("http://example.com/")
                .file(FileMetadata.readFrom(new File("src/test/resources/example.txt").toPath().toAbsolutePath()).os(OS.WINDOWS).arch("amd64"))
                .file(FileMetadata.readFrom(new File("src/test/resources/example.txt").toPath().toAbsolutePath()).os(OS.LINUX).arch("amd64"))
                .build());

        // Two with the same arch but and same OSs should fail
        ex = assertThrows(IllegalStateException.class, () -> Configuration.builder()
                .baseUri("http://example.com/")
                .file(FileMetadata.readFrom(new File("src/test/resources/example.txt").toPath().toAbsolutePath()).os(OS.LINUX).arch("amd64"))
                .file(FileMetadata.readFrom(new File("src/test/resources/example.txt").toPath().toAbsolutePath()).os(OS.LINUX).arch("amd64"))
                .build());
        assertTrue(ex.getMessage().startsWith("2 files resolve to same 'path'"));

        // Two with the different arch but the same OSs should pass
        assertDoesNotThrow(() -> Configuration.builder()
                .baseUri("http://example.com/")
                .file(FileMetadata.readFrom(new File("src/test/resources/example.txt").toPath().toAbsolutePath()).os(OS.LINUX).arch("amd64"))
                .file(FileMetadata.readFrom(new File("src/test/resources/example.txt").toPath().toAbsolutePath()).os(OS.LINUX).arch("aarch64"))
                .build());
    }
}