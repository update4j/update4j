package org.update4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.module.ModuleFinder;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.update4j.mapper.FileMapper;

public class Archive {

    private Path location;
    private Configuration config;
    private List<FileMetadata> files;

    public static Archive read(Path location) throws IOException {
        Archive archive = new Archive(location);
        archive.load();

        return archive;
    }

    public static Archive read(String location) throws IOException {
        return read(Paths.get(location));
    }

    Archive(Path location) {
        this.location = location;
    }

    public Configuration getConfiguration() {
        return config;
    }

    private void load() throws IOException {
        try (FileSystem zip = openConnection()) {
            Path root = zip.getPath("/");
            Path configPath = zip.getPath("/.reserved/config");

            if (Files.notExists(configPath))
                throw new NoSuchFileException(configPath.toString(), null, "Configuration file is missing");

            try (BufferedReader in = Files.newBufferedReader(configPath)) {
                config = Configuration.read(in);
            }

            files = Files.walk(root)
                            .filter(p -> !Files.isDirectory(p))
                            .filter(p -> !p.toString().startsWith("/.reserved"))
                            .map(p -> OS.CURRENT == OS.WINDOWS ? root.relativize(p) : p)
                            .map(path -> getConfiguration().getFiles()
                                            .stream()
                                            .filter(file -> zip.getPath(file.getNormalizedPath().toString())
                                                            .equals(path))
                                            .findAny()
                                            .orElseThrow(() -> new IllegalStateException(path
                                                            + ": Archive entry cannot be linked to a file in the configuration")))
                            .collect(Collectors.toList());

            // Collectors.toUnmodifiableList() was added in JDK 10
            files = Collections.unmodifiableList(files);

            for (FileMetadata file : getFiles()) {
                Path p = zip.getPath(file.getPath().toString());
                if (FileMapper.getChecksum(p) != file.getChecksum()) {
                    throw new IOException(file.getPath() + ": File has been tampered with");
                }
            }
        }
    }

    public List<FileMetadata> getFiles() {
        return files;
    }

    public Path getLocation() {
        return location;
    }

    public FileSystem openConnection() throws IOException {
        if (Files.notExists(getLocation())) {
            // I can't use Map.of("create", "true") since the overload taking a path was only added in JDK 13
            // and using URI overload doesn't support nested zip files
            try (OutputStream out = Files.newOutputStream(getLocation(), StandardOpenOption.CREATE_NEW)) {
                // End of Central Directory Record (EOCD)
                out.write(new byte[] { 0x50, 0x4b, 0x05, 0x06, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 });
            }
        }

        try {
            return FileSystems.newFileSystem(getLocation(), (ClassLoader) null);
        } catch (ProviderNotFoundException e) {
            ModuleFinder.ofSystem()
                            .find("jdk.zipfs")
                            .orElseThrow(() -> new ProviderNotFoundException(
                                            "Accessing the archive depends on the jdk.zipfs module which is missing from the JRE image"));

            throw e;
        }
    }
}
