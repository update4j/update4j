package org.update4j;

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestFull {
    // This test exercises building a configuration with signingin and validation and then installing it
    // using the archive updater.  It does not exercise launch-related functionality.
    @Test
    public void testBasicInstallation() throws IOException, NoSuchAlgorithmException {
        Path targetPath = new File("target/e2e").toPath();
        // Clean up the target directory, but don't do it on exit so any failures can
        // easily be inspected
        if(Files.exists(targetPath)) {
            Files.walkFileTree(targetPath,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult postVisitDirectory(
                                Path dir, IOException exc) throws IOException {
                            Files.delete(dir);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(
                                Path file, BasicFileAttributes attrs)
                                throws IOException {
                            Files.delete(file);
                            return FileVisitResult.CONTINUE;
                        }
                    });
        }

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("rsa");
        KeyPair pair = kpg.generateKeyPair();

        OS nonCurrent = OS.CURRENT == OS.LINUX ? OS.MAC : OS.WINDOWS;
        Configuration outConfig = Configuration.builder()
                .baseUri(new File("src/test/resources").toURI())
                .basePath(targetPath.resolve("output").toAbsolutePath())
                .signer(pair.getPrivate())
                .property("app.name", "Example")
                .property("user.location", "${user.home}/myapp/")
                .file(FileMetadata.readFrom("src/test/resources/example.txt").uri("example.txt"))
                .file(FileMetadata.readFrom("src/test/resources/example-os-match.txt").uri("example-os-match.txt")
                        .os(OS.CURRENT).path("example-os.txt"))
                .file(FileMetadata.readFrom("src/test/resources/example-os-other.txt").uri("example-os-other.txt")
                        .os(nonCurrent).path("example-os.txt"))
                .file(FileMetadata.readFrom("src/test/resources/example-arch-match.txt").uri("example-arch-match.txt")
                        .os(OS.CURRENT).arch(System.getProperty("os.arch")).path("example-arch.txt"))
                .file(FileMetadata.readFrom("src/test/resources/example-arch-other.txt").uri("example-arch-other.txt")
                        .os(OS.CURRENT).arch("not-an-arch").path("example-arch.txt")).build();

        Files.createDirectories(targetPath);
        try(Writer out = Files.newBufferedWriter(targetPath.resolve("config.xml"))) {
            outConfig.write(out);
        }

        Configuration inConfig = null;
        try(InputStreamReader in = new InputStreamReader(Files.newInputStream(targetPath.resolve("config.xml")))) {
            inConfig = Configuration.read(in, pair.getPublic());
        }
        assertEquals(outConfig, inConfig);

        inConfig.update(UpdateOptions.archive(targetPath.resolve("update.zip")).publicKey(pair.getPublic()));
        Archive.read(targetPath.resolve("update.zip")).install();

        assertEquals("example text file", new String(Files.readAllBytes(targetPath.resolve("output/example.txt"))));
        assertEquals("matching-os", new String(Files.readAllBytes(targetPath.resolve("output/example-os.txt"))));
        assertEquals("matching-arch", new String(Files.readAllBytes(targetPath.resolve("output/example-arch.txt"))));
    }
}
