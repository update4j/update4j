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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.update4j.mapper.FileMapper;
import org.update4j.util.FileUtils;

public class Archive {

    private Path location;
    private Configuration config;
    private List<FileMetadata> files;

    static final String RESERVED_DIR = "reserved";
    static final String CONFIG_PATH = "config";
    static final String FILES_DIR = "files";

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
            Path filesPath = zip.getPath(FILES_DIR);
            Path reservedPath = zip.getPath(RESERVED_DIR);
            Path configPath = reservedPath.resolve(CONFIG_PATH);
            
            if (Files.notExists(configPath))
                throw new NoSuchFileException(configPath.toString(), null, "Configuration file is missing");

            try (BufferedReader in = Files.newBufferedReader(configPath)) {
                config = Configuration.read(in);
            }

            try (Stream<Path> stream = Files.walk(filesPath)) {
                files = stream.filter(p -> !Files.isDirectory(p))
                                .map(p -> filesPath.relativize(p))
                                .map(Path::toString)
                                .map(p -> OS.CURRENT != OS.WINDOWS ? "/" + p : p)
                                .map(p -> getConfiguration().getFiles()
                                                .stream()
                                                .filter(file -> file.getNormalizedPath()
                                                                .toString()
                                                                .replace("\\", "/")
                                                                .equals(p.toString()))
                                                .findAny()
                                                .orElseThrow(() -> new IllegalStateException(p
                                                                + ": Archive entry cannot be linked to a file in the configuration")))
                                .collect(Collectors.toList());

                // Collectors.toUnmodifiableList() was added in JDK 10
                files = Collections.unmodifiableList(files);
            }

            for (FileMetadata file : getFiles()) {
                Path p = filesPath.resolve(file.getNormalizedPath().toString().replaceFirst("^\\/", ""));
                if (FileMapper.getChecksum(p) != file.getChecksum()) {
                    throw new IOException(p + ": File has been tampered with");
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

    public void install(boolean deleteArchive) throws IOException {
        // we move out the files, so must be writable
        FileUtils.verifyAccessible(getLocation());
        
        try (FileSystem zip = openConnection()) {
            Path filesPath = zip.getPath(FILES_DIR);

            Map<Path, Path> files = new HashMap<>();
            for (FileMetadata file : getFiles()) {
                Path path = filesPath.resolve(file.getNormalizedPath().toString().replaceFirst("^\\/", ""));
                if (!Files.isRegularFile(path))
                    throw new IOException(path + ": File is missing or invalid");

                FileUtils.verifyAccessible(file.getPath());
                files.put(path, file.getNormalizedPath());
            }

            for (Map.Entry<Path, Path> e : files.entrySet()) {
                if(e.getValue().getParent() != null)
                    Files.createDirectories(e.getValue().getParent());
                
                FileUtils.secureMoveFile(e.getKey(), e.getValue());
            }
        }
        
        if(deleteArchive)
            Files.deleteIfExists(getLocation());
    }
    
    public void install() throws IOException {
        install(true);
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
