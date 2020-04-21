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

        archive.loadConfiguration();
        archive.loadFileList();
        archive.verifyFileList();

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

    private void loadConfiguration() throws IOException {
        try (FileSystem zip = openConnection()) {
            Path path = zip.getPath("/.reserved/config");
            try (BufferedReader in = Files.newBufferedReader(path)) {
                config = Configuration.read(in);
            } catch (NoSuchFileException e) {
                throw new NoSuchFileException(e.getFile(), e.getOtherFile(), "Configuration file is missing");
            }
        }
    }

    public List<FileMetadata> getFiles() {
        return files;
    }

    private void loadFileList() throws IOException {
        try (FileSystem zip = openConnection()) {
            Path root = zip.getPath("/");

            files = Files.walk(root)
                            .filter(p -> !Files.isDirectory(p))
                            .filter(p -> !p.toString().startsWith("/.reserved"))
                            .map(p -> OS.CURRENT == OS.WINDOWS ? root.relativize(p) : p)
                            .map(path -> getConfiguration().getFiles()
                                            .stream()
                                            .filter(file -> zip.getPath(file.getPath().toString()).equals(path))
                                            .findAny()
                                            .orElseThrow(() -> new IllegalStateException(path
                                                            + ": Archive entry cannot be linked to a file in the configuration")))
                            .collect(Collectors.toList());
        }

        files = Collections.unmodifiableList(files);
    }

    private void verifyFileList() throws IOException {
        try (FileSystem zip = openConnection()) {
            for (FileMetadata file : getFiles()) {
                Path p = zip.getPath(file.getPath().toString());
                if (FileMapper.getChecksum(p) != file.getChecksum()) {
                    throw new IOException(file.getPath() + ": File has been tampered with");
                }
            }
        }
    }

    public Path getLocation() {
        return location;
    }

    public FileSystem openConnection() throws IOException {
        if (Files.notExists(getLocation())) {
            // I can't use Map.of("create", "true") since the overloading taking a path was only added in JDK 13
            // and using URI overload doesn't support nested zip files
            try (OutputStream out = Files.newOutputStream(getLocation(), StandardOpenOption.CREATE_NEW)) {
                // zip magic and END
            // @formatter:off
            out.write(new byte[] { 0x50,0x4b,0x05,0x06,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0 });
            // @formatter:on
            }
        }

        try {
            return FileSystems.newFileSystem(getLocation(), (ClassLoader) null);
        } catch (ProviderNotFoundException e) {
            ModuleFinder.ofSystem()
                            .findAll()
                            .stream()
                            .map(mr -> mr.descriptor().name())
                            .filter(name -> name.equals("jdk.zipfs"))
                            .findAny()
                            .orElseThrow(() -> new ProviderNotFoundException(
                                            "Accessing the archive depends on the jdk.zipfs module which is missing from the JRE image"));
            
            throw e;
        }
    }
}
