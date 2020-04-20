package org.update4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.update4j.mapper.FileMapper;

public class Archive {

    private Path location;
    private Configuration config;
    private List<FileMetadata> files;

    public static Archive readFrom(Path location) throws IOException {
        Archive archive = new Archive(location);

        archive.loadConfiguration();
        archive.loadFileList();
        archive.verifyFileList();

        return archive;
    }

    public static Archive readFrom(String location) throws IOException {
        return readFrom(Paths.get(location));
    }

    Archive(Path location) {
        this.location = location;
    }

    public Configuration getConfiguration() {
        return config;
    }

    private void loadConfiguration() throws IOException {
        try (ArchiveConnection connection = openConnection()) {
            Path path = connection.getPath(ArchivePaths.CONFIG);
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
        try (ArchiveConnection connection = openConnection()) {
            Path path = connection.getPath(ArchivePaths.INDEX);
            try (BufferedReader in = Files.newBufferedReader(path)) {
                files = in.lines()
                                .map(record -> record.substring(0, record.lastIndexOf(':')))
                                .map(name -> getConfiguration().getFiles()
                                                .stream()
                                                .filter(file -> file.getPath().toString().equals(name))
                                                .findAny()
                                                .orElseThrow(() -> new IllegalStateException(name
                                                                + ": Listed file cannot be linked to a file in the configuration")))
                                .collect(Collectors.toList());
            } catch (NoSuchFileException e) {
                throw new NoSuchFileException(e.getFile(), e.getOtherFile(), "File list is missing");
            }
        }

        files = Collections.unmodifiableList(files);
    }

    private void verifyFileList() throws IOException {
        try (ArchiveConnection connection = openConnection()) {
            for (FileMetadata file : getFiles()) {
                Path p = connection.getIndexedPath(file.getPath());
                if (Files.notExists(p))
                    throw new NoSuchFileException(file.getPath().toString(), null, "Listed file is missing");

                if (FileMapper.getChecksum(p) != file.getChecksum()) {
                    throw new IOException(file.getPath() + ": File has been tampered with");
                }
            }
        }
    }

    public Path getLocation() {
        return location;
    }

    public ArchiveConnection openConnection() throws IOException {
        return new ArchiveConnection(this);
    }

    private static class ArchivePaths {
        private static final String CONFIG = "config";
        private static final String INDEX = "index";
        private static final String FILES = "files";
    }

    public static class ArchiveConnection extends FileSystem {

        private FileSystem zip;

        private ArchiveConnection(Archive archive) throws IOException {
            zip = FileSystems.newFileSystem(URI.create("jar:" + archive.getLocation().toUri()), Map.of("create", "true"));
        }

        @Override
        public FileSystemProvider provider() {
            return zip.provider();
        }

        @Override
        public boolean isOpen() {
            return zip.isOpen();
        }

        @Override
        public boolean isReadOnly() {
            return zip.isReadOnly();
        }

        @Override
        public String getSeparator() {
            return zip.getSeparator();
        }

        @Override
        public Iterable<Path> getRootDirectories() {
            return zip.getRootDirectories();
        }

        @Override
        public Iterable<FileStore> getFileStores() {
            return zip.getFileStores();
        }

        @Override
        public Set<String> supportedFileAttributeViews() {
            return zip.supportedFileAttributeViews();
        }

        public Path getIndexedPath(String path) {
            return getIndexedPath(getPath(path));
        }

        public Path getIndexedPath(Path path) {

            Path index = zip.getPath(ArchivePaths.INDEX);
            Path files = zip.getPath(ArchivePaths.FILES);

            try {
                if (Files.exists(index)) {
                    try (BufferedReader in = Files.newBufferedReader(index)) {
                        Optional<String> real = in.lines()
                                        .filter(record -> record.startsWith(path.toString()))
                                        .findFirst()
                                        .map(record -> record.substring(record.lastIndexOf(":") + 1));
                        if (real.isPresent()) {
                            return files.resolve(real.get());
                        }
                    }
                }

                Files.createDirectories(files);

                try (BufferedWriter out = Files.newBufferedWriter(index, StandardOpenOption.CREATE,
                                StandardOpenOption.APPEND)) {
                    out.append(path.toString());
                    out.append(':');
                    String timestamp = System.currentTimeMillis() + "";
                    out.append(timestamp);
                    out.append('\n');

                    return files.resolve(timestamp);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public Path getPath(String first, String... more) {
            return zip.getPath(first, more);
        }

        @Override
        public PathMatcher getPathMatcher(String syntaxAndPattern) {
            return zip.getPathMatcher(syntaxAndPattern);
        }

        @Override
        public UserPrincipalLookupService getUserPrincipalLookupService() {
            return zip.getUserPrincipalLookupService();
        }

        @Override
        public WatchService newWatchService() throws IOException {
            return zip.newWatchService();
        }

        @Override
        public void close() {
            try {
                zip.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
