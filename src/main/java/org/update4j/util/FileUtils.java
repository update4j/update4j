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
package org.update4j.util;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.module.FindException;
import java.lang.module.InvalidModuleDescriptorException;
import java.lang.module.ModuleDescriptor;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.Adler32;
import java.util.zip.ZipFile;

import org.update4j.OS;

public class FileUtils {

    private FileUtils() {
    }

    public static long getChecksum(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            Adler32 checksum = new Adler32();
            byte[] buf = new byte[1024 * 8];

            int read;
            while ((read = input.read(buf, 0, buf.length)) > -1)
                checksum.update(buf, 0, read);

            return checksum.getValue();
        }
    }

    public static String getChecksumString(Path path) throws IOException {
        return Long.toHexString(getChecksum(path));
    }

    public static boolean isJarFile(Path path) throws IOException {
        if (!isZipFile(path)) {
            return false;
        }

        try (ZipFile zip = new ZipFile(path.toFile())) {
            return zip.getEntry("META-INF/MANIFEST.MF") != null;
        }
    }

    public static boolean isZipFile(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return false;
        }
        if (!Files.isReadable(path)) {
            throw new IOException("Cannot read file " + path.toAbsolutePath());
        }
        if (Files.size(path) < 4) {
            return false;
        }

        try (DataInputStream in = new DataInputStream(Files.newInputStream(path))) {
            int test = in.readInt();
            return test == 0x504b0304;
        }
    }

    public static byte[] sign(Path path, PrivateKey key) throws IOException {
        try {
            String alg = key.getAlgorithm().equals("EC") ? "ECDSA" : key.getAlgorithm();
            Signature sign = Signature.getInstance("SHA256with" + alg);
            sign.initSign(key);

            try (InputStream input = Files.newInputStream(path)) {
                byte[] buf = new byte[1024 * 8];
                int len;
                while ((len = input.read(buf, 0, buf.length)) > 0)
                    sign.update(buf, 0, len);
            }

            return sign.sign();
        } catch (InvalidKeyException | SignatureException e) {
            throw new IOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    public static String signAndEncode(Path path, PrivateKey key) throws IOException {
        return Base64.getEncoder().encodeToString(sign(path, key));
    }

    public static Path fromUri(URI uri) {
        String path = uri.getPath();

        if (uri.isAbsolute()) {
            path = path.substring(path.lastIndexOf("/") + 1);
        }

        return Paths.get(path);

    }

    public static URI fromPath(Path path) {
        if (path.isAbsolute()) {
            Path filename = path.getFileName();

            return fromPath(filename);
        }

        try {
            String uri = URLEncoder.encode(path.toString().replace("\\", "/"), "UTF-8");

            uri = uri.replace("%2F", "/") // We still need directory structure
                            .replace("+", "%20"); // "+" only means space in queries, not in paths
            return URI.create(uri);
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }

    }

    public static URI relativize(URI base, URI other) {
        if (base == null || other == null)
            return other;

        return base.relativize(other);
    }

    public static Path relativize(Path base, Path other) {
        if (base == null || other == null)
            return other;

        try {
            return base.relativize(other);
        } catch (IllegalArgumentException e) {
        }

        return other;
    }

    public static OS fromFilename(String filename) {
        Pattern osPattern = Pattern.compile(".+-(linux|win|mac)\\.[^.]+");
        Matcher osMatcher = osPattern.matcher(filename);

        if (osMatcher.matches()) {
            return OS.fromShortName(osMatcher.group(1));
        }

        return null;
    }

    public static boolean isEmptyDirectory(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> dir = Files.newDirectoryStream(path)) {
                return !dir.iterator().hasNext();
            }
        }

        return false;
    }

    public static void windowsHidden(Path file, boolean hidden) {
        if (OS.CURRENT != OS.WINDOWS)
            return;

        try {
            Files.setAttribute(file, "dos:hidden", hidden);
        } catch (Exception e) {
        }
    }

    public static void verifyAccessible(Path path) throws IOException {
        boolean exists = Files.exists(path);

        if (exists && !Files.isWritable(path))
            throw new AccessDeniedException(path.toString());

        try (Writer out = Files.newBufferedWriter(path,
                        exists ? StandardOpenOption.APPEND : StandardOpenOption.CREATE)) {
        } finally {
            if (!exists)
                Files.deleteIfExists(path);
        }
    }

    public static void secureMoveFile(Path source, Path target) throws IOException {
        // for windows we can't go wrong because the OS manages locking
        if (OS.CURRENT == OS.WINDOWS || Files.notExists(target)) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        // At this point we are on non-windows and exists
        // Lets unlink file first so we don't run into file-busy errors.
        Path temp = Files.createTempFile(target.getParent(), null, null);
        Files.move(target, temp, StandardCopyOption.REPLACE_EXISTING);

        try {
            Files.move(source, target);
        } catch (IOException e) {
            Files.move(temp, target);
            throw e;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static void delayedDelete(Collection<Path> files, int secondsDelay) {
        secondsDelay = Math.max(secondsDelay, 1);
        List<String> commands = new ArrayList<>();

        String filenames = files.stream()
                        .map(Path::toString)
                        .map(f -> "\"" + f.replace("\"", "\\\"") + "\"")
                        .collect(Collectors.joining(" "));

        if (OS.CURRENT == OS.WINDOWS) {
            commands.addAll(List.of("cmd", "/c"));
            commands.add("ping localhost -n " + (secondsDelay + 1) + " & del " + filenames);
        } else {
            commands.addAll(List.of("sh", "-c"));
            commands.add("sleep " + secondsDelay + " ; rm " + filenames);
        }

        ProcessBuilder pb = new ProcessBuilder(commands);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                pb.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
    }

    public static ModuleDescriptor deriveModuleDescriptor(Path jar, String filename) throws IOException {
        try (FileSystem zip = FileSystems.newFileSystem(jar, ClassLoader.getSystemClassLoader())) {

            Path moduleInfo = zip.getPath("/module-info.class");
            if (Files.exists(moduleInfo)) {
                
                try (InputStream in = Files.newInputStream(moduleInfo)) {
                    return ModuleDescriptor.read(in, () -> {
                        try {
                            Path root = zip.getPath("/");
                            return Files.walk(root)
                                            .filter(f -> !Files.isDirectory(f))
                                            .map(f -> root.relativize(f))
                                            .map(Path::toString)
                                            .map(FileUtils::toPackageName)
                                            .flatMap(Optional::stream)
                                            .collect(Collectors.toSet());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                }
            }
            
            return automaticModule(zip, filename);
        }
    }
    /*
     *  https://github.com/openjdk/jdk/blob/master/src/java.base/share/classes/jdk/internal/module/ModulePath.java#L459
     */

    private static final String SERVICES_PREFIX = "META-INF/services/";
    private static final Attributes.Name AUTOMATIC_MODULE_NAME = new Attributes.Name("Automatic-Module-Name");

    private static ModuleDescriptor automaticModule(FileSystem zip, String filename) throws IOException {
        // I stripped elements that I don't currently care, as main class and version

        Manifest man = null;
        try (InputStream in = Files.newInputStream(zip.getPath("/META-INF/MANIFEST.MF"))) {
            man = new Manifest(in);
        }

        Attributes attrs = null;
        String moduleName = null;
        if (man != null) {
            attrs = man.getMainAttributes();
            if (attrs != null) {
                moduleName = attrs.getValue(AUTOMATIC_MODULE_NAME);
            }
        }

        // Create builder, using the name derived from file name when
        // Automatic-Module-Name not present
        ModuleDescriptor.Builder builder;
        if (moduleName != null) {
            try {
                builder = ModuleDescriptor.newAutomaticModule(moduleName);
            } catch (IllegalArgumentException e) {
                throw new FindException(AUTOMATIC_MODULE_NAME + ": " + e.getMessage());
            }
        } else {
            builder = ModuleDescriptor.newAutomaticModule(StringUtils.deriveModuleName(filename));
        }

        // scan the names of the entries in the JAR file
        Map<Boolean, Set<String>> map = Files.walk(zip.getPath("/"))
                        .filter(e -> !Files.isDirectory(e))
                        .map(Path::toString)
                        .filter(e -> (e.endsWith(".class") ^ e.startsWith(SERVICES_PREFIX)))
                        .collect(Collectors.partitioningBy(e -> e.startsWith(SERVICES_PREFIX), Collectors.toSet()));

        Set<String> classFiles = map.get(Boolean.FALSE);
        Set<String> configFiles = map.get(Boolean.TRUE);

        // the packages containing class files
        Set<String> packages = classFiles.stream()
                        .map(FileUtils::toPackageName)
                        .flatMap(Optional::stream)
                        .distinct()
                        .collect(Collectors.toSet());

        // all packages are exported and open
        builder.packages(packages);

        // map names of service configuration files to service names
        Set<String> serviceNames = configFiles.stream()
                        .map(FileUtils::toServiceName)
                        .flatMap(Optional::stream)
                        .collect(Collectors.toSet());

        // parse each service configuration file
        for (String sn : serviceNames) {
            Path entry = zip.getPath(SERVICES_PREFIX + sn);
            List<String> providerClasses = new ArrayList<>();
            try (InputStream in = Files.newInputStream(entry)) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                String cn;
                while ((cn = nextLine(reader)) != null) {
                    if (!cn.isEmpty()) {
                        String pn = packageName(cn);
                        if (!packages.contains(pn)) {
                            String msg = "Provider class " + cn + " not in module";
                            throw new InvalidModuleDescriptorException(msg);
                        }
                        providerClasses.add(cn);
                    }
                }
            }
            if (!providerClasses.isEmpty())
                builder.provides(sn, providerClasses);
        }

        return builder.build();

    }

    /**
     * Reads the next line from the given reader and trims it of comments and
     * leading/trailing white space.
     *
     * Returns null if the reader is at EOF.
     */
    private static String nextLine(BufferedReader reader) throws IOException {
        String ln = reader.readLine();
        if (ln != null) {
            int ci = ln.indexOf('#');
            if (ci >= 0)
                ln = ln.substring(0, ci);
            ln = ln.trim();
        }
        return ln;
    }

    /**
     * Maps a type name to its package name.
     */
    private static String packageName(String cn) {
        int index = cn.lastIndexOf('.');
        return (index == -1) ? "" : cn.substring(0, index);
    }

    /**
     * Maps the name of an entry in a JAR or ZIP file to a package name.
     *
     * @throws InvalidModuleDescriptorException
     *             if the name is a class file in the top-level directory of the
     *             JAR/ZIP file (and it's not module-info.class)
     */
    private static Optional<String> toPackageName(String name) {
        int index = name.lastIndexOf("/");
        if (index == -1) {
            if (name.endsWith(".class") && !name.equals("module-info.class")) {
                String msg = name + " found in top-level directory" + " (unnamed package not allowed in module)";
                throw new InvalidModuleDescriptorException(msg);
            }
            return Optional.empty();
        }

        String pn = name.substring(0, index).replace('/', '.');
        if (StringUtils.isClassName(pn)) {
            return Optional.of(pn);
        } else {
            // not a valid package name
            return Optional.empty();
        }
    }

    /**
     * Returns the service type corresponding to the name of a services
     * configuration file if it is a legal type name.
     *
     * For example, if called with "META-INF/services/p.S" then this method returns
     * a container with the value "p.S".
     */
    private static Optional<String> toServiceName(String cf) {
        int index = cf.lastIndexOf("/") + 1;
        if (index < cf.length()) {
            String prefix = cf.substring(0, index);
            if (prefix.equals(SERVICES_PREFIX)) {
                String sn = cf.substring(index);
                if (StringUtils.isClassName(sn))
                    return Optional.of(sn);
            }
        }
        return Optional.empty();
    }
}
