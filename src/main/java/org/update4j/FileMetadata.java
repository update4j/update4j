/*
 * Copyright 2018 Mordechai Meisels
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * 		http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.update4j;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.update4j.mapper.ConfigMapper;
import org.update4j.mapper.FileMapper;
import org.update4j.service.UpdateHandler;
import org.update4j.util.FileUtils;
import org.update4j.util.PropertyManager;

/**
 * This class represents a managed file (&mdash;in sense of updating and
 * dynamically loading onto a JVM instance upon launch) in this framework. It
 * corresponds with the {@code <file>} XML element.
 * 
 * <p>
 * Some metadata is required for updating only, some are required for launch
 * only, and some are for both. The documentation will try to point that out for
 * each field.
 * 
 * <p>
 * An instance of this class cannot be created directly, only
 * {@link Configuration.Builder} and {@link Configuration#parse(ConfigMapper)}
 * can. For both approaches you use a special interim object: The builder takes
 * a {@link FileMetadata.Reference} object in the {@code file()} method &mdash;
 * created by either {@link FileMetadata#readFrom(Path)} for single files, or
 * {@link FileMetadata#streamDirectory(Path)} for a complete directory. The
 * {@code parse()} method works with the {@link ConfigMapper} class that lists
 * files with {@link FileMapper}s.
 * 
 * <p>
 * An instance of this class is immutable and thus thread-safe.
 * 
 * @author Mordechai Meisels
 *
 */
public class FileMetadata {

    private final URI uri;
    private final Path path;
    private Path normalizedPath;
    private final OS os;
    private final long checksum;
    private final long size;
    private final boolean classpath;
    private final boolean modulepath;
    private final String comment;
    private final boolean ignoreBootConflict;
    private final String signature;

    private final List<AddPackage> addExports;
    private final List<AddPackage> addOpens;
    private final List<String> addReads;

    private FileMetadata(URI uri, Path path, OS os, long checksum, long size, boolean classpath, boolean modulepath,
                    String comment, boolean ignoreBootConflict, String signature, List<AddPackage> addExports,
                    List<AddPackage> addOpens, List<String> addReads) {

        this.uri = uri;

        // parsing properties might fail sometimes when not on current os, so let it through
        if (os == null || os == OS.CURRENT) {
            Objects.requireNonNull(uri, "uri");

            if (!uri.isAbsolute()) {
                throw new IllegalArgumentException("Absolute uri required: " + uri);
            }
        }

        this.path = path;

        if (os == null || os == OS.CURRENT) {
            Objects.requireNonNull(path, "path");

            if (!path.isAbsolute()) {
                throw new IllegalArgumentException("Absolute path required: " + path);
            }
        }

        this.os = os;

        if (checksum < 0)
            throw new IllegalArgumentException("Negative checksum: " + checksum);

        this.checksum = checksum;

        if (size < 0)
            throw new IllegalArgumentException("Negative file size: " + size);

        this.size = size;
        this.classpath = classpath;
        this.modulepath = modulepath;
        this.comment = comment;
        this.ignoreBootConflict = ignoreBootConflict;
        this.signature = signature;

        this.addExports = Collections.unmodifiableList(new ArrayList<>(addExports));
        this.addOpens = Collections.unmodifiableList(new ArrayList<>(addOpens));
        this.addReads = Collections.unmodifiableList(new ArrayList<>(addReads));
    }

    /**
     * Returns the download URI for this file. This might be directly expressed in
     * the {@code uri} attribute as an absolute uri, relative to the base uri, or
     * &mdash; if missing &mdash; inferred from the {@code path} attribute.
     * 
     * <p>
     * When inferring from the path it will use the complete path structure if
     * &mdash; and only if &mdash; the path is relative to the base path. Otherwise
     * it will only use the last part (i.e. the "filename").
     * 
     * 
     * <p>
     * If this file is marked for a foreign OS and the URI has a foreign property in
     * the file (a property marked for a different {@code os}), it will return
     * {@code null}.
     * 
     * <p>
     * This field is only used for updating.
     * 
     * @return The download URI for this file.
     */
    public URI getUri() {
        return uri;
    }

    /**
     * Returns the local path for this file. This might be directly expressed in the
     * {@code path} attribute as an absolute path, relative to the base path, or
     * &mdash; if missing &mdash; inferred from the {@code uri} attribute.
     * 
     * <p>
     * When inferring from the uri it will use the complete path structure if
     * &mdash; and only if &mdash; the uri is relative to the base uri. Otherwise it
     * will only use the last part (i.e. the "filename").
     * 
     * <p>
     * If this file is marked for a foreign OS and the path has a foreign property
     * in the file (a property marked for a different {@code os}), it will return
     * {@code null}.
     * 
     * <p>
     * This field is used for both updating and launching.
     * 
     * @return The local path for this file.
     */
    public Path getPath() {
        return path;
    }

    Path getNormalizedPath() {
        if (getPath() == null)
            return null;
        if (normalizedPath == null)
            normalizedPath = path.normalize();

        return normalizedPath;
    }

    /**
     * Returns the operating system expressed in the {@code os} attribute, or
     * {@code null} if non.
     * 
     * <p>
     * This field is used for both updating and launching.
     * 
     * @return The operating system expressed in the {@code os} attribute, or
     *         {@code null} if non.
     */
    public OS getOs() {
        return os;
    }

    /**
     * Returns the Adler32 checksum of this file. Used to check if an update is
     * needed and to validate the file post-download.
     * 
     * <p>
     * This field is only used for updating.
     * 
     * @return The Adler32 checksum of this file.
     */
    public long getChecksum() {
        return checksum;
    }

    /**
     * Returns the file size. Used to check if an update is needed, validate the
     * file post-download, and to calculate proper download deltas.
     * 
     * <p>
     * This field is only used for updating.
     * 
     * @return The file size.
     */
    public long getSize() {
        return size;
    }

    /**
     * Returns if this file is marked to be loaded on the dynamic classpath. Files
     * in the bootstrap and non-jar files should generally be marked {@code false}.
     * 
     * <p>
     * This field is only used for launching.
     * 
     * @return If this file is marked to be loaded on the dynamic classpath.
     */
    public boolean isClasspath() {
        return classpath;
    }

    /**
     * Returns if this file is marked to be loaded on the dynamic modulepath. Files
     * in the bootstrap should generally be marked {@code false}. Non-jar files must
     * not mark this {@code true} or the JVM will fail.
     * 
     * <p>
     * This field is only used for launching.
     * 
     * @return If this file is marked to be loaded on the dynamic classpath.
     */
    public boolean isModulepath() {
        return modulepath;
    }

    /**
     * Returns a string from the {@code comment} attribute, or {@code null} if
     * missing.
     * 
     * <p>
     * This has no effect on the framework and can be used for just anything. For
     * instance: you might mark this file with {@code requiresRestart} to notify the
     * update handler to restart the application if this file was part of the
     * update. Or you might put an authentication key which might then be used in
     * the download by overriding
     * {@link UpdateHandler#openDownloadStream(FileMetadata)}.
     * 
     * 
     * @return A string from the {@code comment} attribute, or {@code null} if
     *         missing.
     */
    public String getComment() {
        return comment;
    }

    /**
     * Returns whether this file was marked to ignore a boot conflict check.
     * 
     * <p>
     * The boot conflict check is a safety measure put in place to prevent breaking
     * your remote applications, and then being impossible to fix remotely just by
     * pushing a new release.<br>
     * If you push a file that then gets loaded onto the <em>boot modulepath</em>
     * (by placing it in a location that is searched by the modulepath) and that
     * file ends up being a invalid module (as split package, duplicate module name
     * or just not a zip file), the JVM will complain and resist to start up. Since
     * we can't start the application anymore, there is no way to fix this other
     * than reinstalling the application.
     * 
     * <p>
     * This file check is done for each and every file with the {@code .jar} file
     * extension, even if the file was explicitly marked with the {@code modulepath}
     * (meaning it is only loaded on the <em>dynamic</em> modulepath).
     * 
     * <p>
     * In cases where the file is not visible to the boot modulepath (by carefully
     * placing it in the right directory) and you want to circumvent this check you
     * can mark this {@code true}.
     * 
     * <p>
     * This field is only used for updating.
     * 
     * @return If this file is marked to ignore a boot conflict check.
     */
    public boolean isIgnoreBootConflict() {
        return ignoreBootConflict;
    }

    /**
     * Returns the Base64 encoded file signature.
     * 
     * <p>
     * This field is only used for updating.
     * 
     * @return The Base64 encoded file signature.
     */
    public String getSignature() {
        return signature;
    }

    /**
     * Returns an unmodifiable list of packages that should be exported to a module
     * despite not being defined so in the {@code module-info.class} file.
     * 
     * <p>
     * This is ignored if {@link #isModulepath()} returns {@code false}.
     * 
     * <p>
     * This field is only used for launching.
     * 
     * @return A list of packages to be exported to other modules.
     */
    public List<AddPackage> getAddExports() {
        return addExports;
    }

    /**
     * Returns an unmodifiable list of packages that should be opened to a module
     * despite not being defined so in the {@code module-info.class} file.
     * 
     * <p>
     * This is ignored if {@link #isModulepath()} returns {@code false}.
     * 
     * <p>
     * This field is only used for launching.
     * 
     * @return A list of packages to be opened to other modules.
     */
    public List<AddPackage> getAddOpens() {
        return addOpens;
    }

    /**
     * Returns an unmodifiable list modules this module should read despite not
     * being defined so in the {@code module-info.class} file.
     * 
     * <p>
     * This is ignored if {@link #isModulepath()} returns {@code false}.
     * 
     * <p>
     * This field is only used for launching.
     * 
     * @return A list of modules this module should read.
     */
    public List<String> getAddReads() {
        return addReads;
    }

    /**
     * Checks if this file is out of date and requires an update.
     * 
     * @return If this file requires an update.
     * 
     * @throws IOException
     *             If any exception arises while reading the file content.
     */
    public boolean requiresUpdate() throws IOException {
        if (getOs() != null && getOs() != OS.CURRENT)
            return false;

        return Files.notExists(getPath()) || Files.size(getPath()) != getSize()
                        || FileUtils.getChecksum(getPath()) != getChecksum();
    }

    /**
     * Construct a {@link Reference} of the file at the provided location and can be
     * used in the Builder API.
     * 
     * <p>
     * This should point to a real file on the filesystem and cannot contain
     * placeholders.
     * 
     * @param source
     *            The path of the real file to which to refer in the builder.
     * 
     * @return A {@code Reference} to a file to be used in the Builder API.
     */
    public static Reference readFrom(Path source) {
        return new Reference(source);
    }

    /**
     * Construct a {@link Reference} of the file at the provided location and can be
     * used in the Builder API.
     * 
     * <p>
     * This should point to a real file on the filesystem and cannot contain
     * placeholders.
     * 
     * @param source
     *            The path of the real file to which to refer in the builder.
     * 
     * @return A {@code Reference} to a file to be used in the Builder API.
     */
    public static Reference readFrom(String source) {
        return readFrom(Paths.get(source));
    }

    /**
     * Construct a stream of file {@link Reference}s from the provided directory.
     * You can then customize individual files by using
     * {@link Stream#peek(Consumer)}, or filter out files with
     * {@link Stream#filter(Predicate)}. It will only contain files and symlinks,
     * not directories.
     * 
     * <p>
     * For convenience, this method also presets the {@code path()} to the file's
     * source path <em>relative to</em> to streamed directory. So for the directory
     * structure {@code /home/a/b/c.jar}, streaming {@code /home/a} would set the
     * path to {@code b/c.jar}. <br>
     * If you wish to infer the path from the URI (as described in
     * {@link FileMetadata.Reference#path(String)}) you must nullify it by calling
     * {@code path((String)null)}.
     * 
     * <p>
     * This should point to a real directory on the filesystem and cannot contain
     * placeholders.
     * 
     * @param source
     *            The path of the real directory to stream.
     * 
     * @return A {@code Stream<Reference>} of files to be used in the Builder API.
     */
    public static Stream<Reference> streamDirectory(Path dir) {
        try {
            return Files.walk(dir)
                            .filter(p -> Files.isRegularFile(p))
                            .map(FileMetadata::readFrom)
                            .peek(fm -> fm.path(dir.relativize(fm.getSource())));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Construct a stream of file {@link Reference}s from the provided directory.
     * You can then customize individual files by using
     * {@link Stream#peek(Consumer)}, or filter out files with
     * {@link Stream#filter(Predicate)}. It will only contain files and symlinks,
     * not directories.
     * 
     * <p>
     * For convenience, this method also presets the {@code path()} to the file's
     * source path <em>relative to</em> to streamed directory. So for the directory
     * structure {@code /home/a/b/c.jar}, streaming {@code /home/a} would set the
     * path to {@code b/c.jar}. <br>
     * If you wish to infer the path from the URI (as described in
     * {@link FileMetadata.Reference#path(String)}) you must nullify it by calling
     * {@code path((String)null)}.
     * 
     * <p>
     * This should point to a real directory on the filesystem and cannot contain
     * placeholders.
     * 
     * @param source
     *            The path of the real directory to stream.
     * 
     * @return A {@code Stream<Reference>} of files to be used in the Builder API.
     */
    public static Stream<Reference> streamDirectory(String dir) {
        return streamDirectory(Paths.get(dir));
    }

    /**
     * A reference to a file can be used by the Builder API to read its metadata.
     * You can construct a reference by using either
     * {@link FileMetadata#readFrom(Path)} or
     * {@link FileMetadata#streamDirectory(Path)}.
     * 
     * @author Mordechai Meisels
     *
     */
    public static class Reference {
        private Path source;
        private String path;
        private String uri;
        private OS os;
        private Boolean classpath;
        private Boolean modulepath;
        private String comment;
        private Boolean ignoreBootConflict;

        private List<AddPackage> addExports;
        private List<AddPackage> addOpens;
        private List<String> addReads;

        private PlaceholderMatchType matcher;

        private Reference(Path source) {
            this.source = source;

            addExports = new ArrayList<>();
            addOpens = new ArrayList<>();
            addReads = new ArrayList<>();
        }

        /**
         * Returns the real file location that this instance refers to.
         * 
         * @return The real file location that this instance refers to.
         */
        public Path getSource() {
            return source;
        }

        /**
         * Set the download URI for this file. This might be an absolute uri, relative
         * to the base uri, or &mdash; if missing &mdash; inferred from the
         * {@code path}.
         * 
         * <p>
         * When inferring from the path it will use the complete path structure if
         * &mdash; and only if &mdash; the path is relative to the base path. Otherwise
         * it will only use the last part (i.e. the "filename").
         * 
         * @return This instance for chaining.
         */
        public Reference uri(URI uri) {
            return uri(uri == null ? null : uri.toString());
        }

        /**
         * Set the download URI for this file. This might be an absolute uri, relative
         * to the base uri, or &mdash; if missing &mdash; inferred from the
         * {@code path}.
         * 
         * <p>
         * When inferring from the path it will use the complete path structure if
         * &mdash; and only if &mdash; the path is relative to the base path. Otherwise
         * it will only use the last part (i.e. the "filename").
         * 
         * <p>
         * This field may contain placeholders.
         * 
         * @return This instance for chaining.
         */
        public Reference uri(String uri) {
            this.uri = uri;

            return this;
        }

        /**
         * Returns the URI passed in {@link #uri(String)} or {@code null} if non.
         * 
         * @return The URI passed in {@link #uri(String)} or {@code null}.
         */
        public String getUri() {
            return uri;
        }

        /**
         * Sets the local path for this file. This might be an absolute path, relative
         * to the base path, or &mdash; if missing &mdash; inferred from the {@code uri}
         * attribute, if both are missing, the source returned by {@link #getSource()}.
         * 
         * <p>
         * When inferring from the uri it will use the complete path structure if
         * &mdash; and only if &mdash; the uri is relative to the base uri. Otherwise it
         * will only use the last part (i.e. the "filename").
         * 
         * 
         * @return This instance for chaining.
         */
        public Reference path(Path path) {
            return path(path == null ? null : path.toString());
        }

        /**
         * Sets the local path for this file. This might be an absolute path, relative
         * to the base path, or &mdash; if missing &mdash; inferred from the {@code uri}
         * attribute, if both are missing, the source returned by {@link #getSource()}.
         * 
         * <p>
         * When inferring from the uri it will use the complete path structure if
         * &mdash; and only if &mdash; the uri is relative to the base uri. Otherwise it
         * will only use the last part (i.e. the "filename").
         * 
         * 
         * @return This instance for chaining.
         */
        public Reference path(String path) {
            this.path = path;

            return this;
        }

        /**
         * Returns the path passed in {@link #path(String)} or {@code null} if non.
         * 
         * @return The path passed in {@link #path(String)} or {@code null}.
         */
        String getPath() {
            return path;
        }

        /**
         * Sets the os of this file to exclude it from other operating systems when
         * updating and launching.
         * 
         * @param os
         *            The operating system to associate this file with.
         * @return This instance for chaining.
         */
        public Reference os(OS os) {
            this.os = os;

            return this;
        }

        /**
         * Sets the os by parsing the filename. The os is detected if it matches this
         * pattern:
         * 
         * <pre>
         * filename-<b>os</b>.extension
         * </pre>
         * 
         * where {@code os} can be {@code win}, {@code mac} or {@code linux}.
         * 
         * <p>
         * Examples include:
         * 
         * <pre>
         * appicon-win.ico
         * appicon-mac.icns
         * appicon-linux.png
         * 
         * javafx-base-11.0.1-win.jar
         * javafx-base-11.0.1-mac.jar
         * javafx-base-11.0.1-linux.jar
         * </pre>
         * 
         * <p>
         * If a match is not found, the old value will not be changed.
         * 
         * @return This instance for chaining.
         */
        public Reference osFromFilename() {
            OS os = FileUtils.fromFilename(source.toString());
            if (os != null)
                os(os);

            return this;
        }

        /**
         * Returns the os passed in {@link #os(OS)} or {@code null} if non.
         * 
         * @return The os passed in {@link #os(OS)} or {@code null}.
         */
        public OS getOs() {
            return os;
        }

        public Reference classpath(boolean cp) {
            this.classpath = cp;

            return this;
        }

        public Reference classpath() {
            return classpath(true);
        }

        public boolean isClasspath() {
            return Boolean.TRUE.equals(classpath);
        }

        public Reference modulepath(boolean mp) {
            this.modulepath = mp;

            return this;
        }

        public Reference modulepath() {
            return modulepath(true);
        }

        public boolean isModulepath() {
            return Boolean.TRUE.equals(modulepath);
        }

        private boolean isFinalModulepath() throws IOException {
            if (!isModulepath()) {
                return false;
            }

            // If not a zip file, completely ignore modulepath
            return FileUtils.isZipFile(getSource());
        }

        public Reference comment(String c) {
            comment = c;

            return this;
        }

        public String getComment() {
            return comment;
        }

        public Reference ignoreBootConflict(boolean b) {
            ignoreBootConflict = b;

            return this;
        }

        public Reference ignoreBootConflict() {
            return ignoreBootConflict(true);
        }

        public boolean isIgnoreBootConflict() {
            return Boolean.TRUE.equals(ignoreBootConflict);
        }

        public Reference exports(String pkg, String targetModule) {
            addExports.add(new AddPackage(Objects.requireNonNull(pkg), Objects.requireNonNull(targetModule)));

            return this;
        }

        public Reference exports(Collection<AddPackage> exports) {
            addExports.addAll(exports);

            return this;
        }

        public List<AddPackage> getAddExports() {
            return addExports;
        }

        public Reference opens(String pkg, String targetModule) {
            addOpens.add(new AddPackage(pkg, targetModule));

            return this;
        }

        public Reference opens(Collection<AddPackage> opens) {
            addOpens.addAll(opens);

            return this;
        }

        public List<AddPackage> getAddOpens() {
            return addOpens;
        }

        public Reference reads(String module) {
            addReads.add(module);

            return this;
        }

        public Reference reads(Collection<String> reads) {
            addReads.addAll(reads);

            return this;
        }

        public List<String> getAddReads() {
            return addReads;
        }

        public Reference matchAndReplace(PlaceholderMatchType matcher) {
            this.matcher = matcher;

            return this;
        }

        public PlaceholderMatchType getMatchType() {
            return matcher;
        }

        public long getSize() throws IOException {
            return Files.size(source);
        }

        public long getChecksum() throws IOException {
            return FileUtils.getChecksum(source);
        }

        public byte[] getSignature(PrivateKey key) throws IOException {
            if (key == null)
                return null;

            return FileUtils.sign(source, key);
        }

        FileMapper getFileMapper(PropertyManager pm, String baseUri, String basePath, PlaceholderMatchType matchType,
                        PrivateKey key) {
            try {

                String path = getPath();
                if (uri == null && getPath() == null) {
                    path = source.toString();
                }

                if (uri != null) {
                    uri = uri.replace("\\", "/");
                }

                if (path != null) {
                    path = path.replace("\\", "/");
                }

                if (uri != null && uri.equals(path)) {
                    uri = null;
                }

                PlaceholderMatchType matcher = getMatchType();
                if (matcher == null) {
                    matcher = matchType;
                }

                FileMapper mapper = new FileMapper();

                mapper.uri = pm.implyPlaceholders(getUri(), matcher, true);
                mapper.path = pm.implyPlaceholders(path, matcher, true);

                // Changes behavior of cross-resolution, so watch out
                String phBaseUri = pm.implyPlaceholders(baseUri, matcher, true);
                if (mapper.uri != null && phBaseUri != null && mapper.uri.startsWith(phBaseUri)) {
                    mapper.uri = mapper.uri.substring(phBaseUri.length());
                }

                String phBasePath = pm.implyPlaceholders(basePath, matcher, true);
                if (mapper.path != null && phBasePath != null && mapper.path.startsWith(phBasePath)) {
                    mapper.path = mapper.path.substring(phBasePath.length());
                }

                mapper.os = getOs();
                mapper.size = getSize();
                mapper.checksum = Long.toHexString(getChecksum());
                mapper.classpath = isClasspath();
                mapper.modulepath = isFinalModulepath();
                mapper.ignoreBootConflict = isIgnoreBootConflict();

                byte[] sig = getSignature(key);
                if (sig != null)
                    mapper.signature = Base64.getEncoder().encodeToString(sig);

                mapper.comment = pm.implyPlaceholders(getComment(), matcher, false);
                mapper.addExports.addAll(getAddExports());
                mapper.addOpens.addAll(getAddOpens());
                mapper.addReads.addAll(getAddReads());

                return mapper;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    static Builder builder() {
        return new Builder();
    }

    static class Builder {
        private URI baseUri;
        private Path basePath;

        private URI uri;
        private Path path;
        private OS os;
        private long checksum;
        private long size;
        private boolean classpath;
        private boolean modulepath;
        private String comment;
        private boolean ignoreBootConflict;
        private String signature;

        private List<AddPackage> addExports;
        private List<AddPackage> addOpens;
        private List<String> addReads;

        private Builder() {
            addExports = new ArrayList<>();
            addOpens = new ArrayList<>();
            addReads = new ArrayList<>();
        }

        Builder baseUri(URI uri) {
            this.baseUri = uri;

            return this;
        }

        Builder basePath(Path path) {
            this.basePath = path;

            return this;
        }

        Builder uri(URI uri) {
            this.uri = uri;

            return this;
        }

        Builder path(Path path) {
            this.path = path;

            return this;
        }

        Builder os(OS os) {
            this.os = os;

            return this;
        }

        Builder checksum(long checksum) {
            this.checksum = checksum;

            return this;
        }

        Builder checksum(String checksum) {
            return checksum(Long.parseLong(checksum, 16));
        }

        Builder size(long size) {
            this.size = size;

            return this;
        }

        Builder classpath(boolean cp) {
            this.classpath = cp;

            return this;
        }

        Builder modulepath(boolean mp) {
            this.modulepath = mp;

            return this;
        }

        Builder ignoreBootConflict(boolean b) {
            this.ignoreBootConflict = b;

            return this;
        }

        Builder comment(String comment) {
            this.comment = comment;

            return this;
        }

        Builder signature(String signature) {
            this.signature = signature;

            return this;
        }

        private void validateAddReads(List<String> list) {
            for (String read : list) {
                Objects.requireNonNull(read);
                if (read.isEmpty()) {
                    throw new IllegalArgumentException("Missing module name.");
                }
            }
        }

        private void validateAddPackages(List<AddPackage> list) {
            for (AddPackage add : list) {
                Objects.requireNonNull(add);
            }
        }

        Builder exports(List<AddPackage> exports) {
            validateAddPackages(exports);

            addExports.addAll(exports);

            return this;
        }

        Builder opens(List<AddPackage> opens) {
            validateAddPackages(opens);

            addOpens.addAll(opens);

            return this;
        }

        Builder reads(List<String> reads) {
            validateAddReads(reads);

            addReads.addAll(reads);

            return this;
        }

        FileMetadata build() {
            if (path == null && uri != null) {
                path(FileUtils.fromUri(uri));
            }
            if (uri == null && path != null) {
                uri(FileUtils.fromPath(path));
            }

            // relativization gets messed up if relative path has a leading slash
            if (uri != null && !uri.isAbsolute() && uri.getPath().startsWith("/")) {
                uri = URI.create("/").relativize(uri);
            }

            if (path != null && !path.isAbsolute() && path.startsWith("/")) {
                path = Paths.get("/").relativize(path);
            }

            if (baseUri != null && uri != null) {
                this.uri = baseUri.resolve(uri);
            }

            if (basePath != null && path != null) {
                this.path = basePath.resolve(path);
            }

            return new FileMetadata(uri, path, os, checksum, size, classpath, modulepath, comment, ignoreBootConflict,
                            signature, addExports, addOpens, addReads);
        }
    }
}
