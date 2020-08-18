package org.update4j.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.module.FindException;
import java.lang.module.InvalidModuleDescriptorException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

public class ModuleUtils {

    public static Set<Module> userBootModules() {
        Set<String> sysmods = ModuleFinder.ofSystem()
                        .findAll()
                        .stream()
                        .map(ModuleReference::descriptor)
                        .map(ModuleDescriptor::name)
                        .collect(Collectors.toSet());
        
        return ModuleLayer.boot()
                        .modules()
                        .stream()
                        .filter(mod -> !sysmods.contains(mod.getName()))
                        .collect(Collectors.toSet());
    }

    public static ModuleDescriptor deriveModuleDescriptor(Path jar, String filename, boolean readZip)
                    throws IOException {
        if (!readZip)
            return primitiveModuleDescriptor(jar, filename);

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
                                            .map(ModuleUtils::toPackageName)
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

    private static ModuleDescriptor primitiveModuleDescriptor(Path jar, String filename) throws IOException {
        String tempName = "a" + jar.getFileName();
        Path temp = Paths.get(System.getProperty("user.home"), tempName + ".jar");
        ModuleDescriptor mod;
        try {
            Files.copy(jar, temp);
            mod = ModuleFinder.of(temp) //
                            .findAll()
                            .stream()
                            .map(ModuleReference::descriptor)
                            .findAny()
                            .orElseThrow(IllegalStateException::new);

        } finally {
            Files.deleteIfExists(temp);
        }

        if (tempName.equals(mod.name())) {
            String newModuleName = StringUtils.deriveModuleName(filename);
            if (!StringUtils.isModuleName(newModuleName)) {
                Warning.illegalModule(jar.getFileName().toString());
                throw new IllegalStateException("Automatic module name '" + newModuleName + "' for file '"
                                + jar.getFileName() + "' is not valid.");
            }

            return ModuleDescriptor.newAutomaticModule(newModuleName).packages(mod.packages()).build();

        }

        return mod;
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
                        .map(ModuleUtils::toPackageName)
                        .flatMap(Optional::stream)
                        .distinct()
                        .collect(Collectors.toSet());

        // all packages are exported and open
        builder.packages(packages);

        // map names of service configuration files to service names
        Set<String> serviceNames = configFiles.stream()
                        .map(ModuleUtils::toServiceName)
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
