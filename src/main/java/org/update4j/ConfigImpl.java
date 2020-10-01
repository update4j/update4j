/*
 * Copyright 2020 Mordechai Meisels
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.update4j;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.module.FindException;
import java.lang.module.InvalidModuleDescriptorException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.update4j.UpdateOptions.ArchiveUpdateOptions;
import org.update4j.inject.Injectable;
import org.update4j.inject.UnsatisfiedInjectionException;
import org.update4j.mapper.MapMapper;
import org.update4j.service.Launcher;
import org.update4j.service.Service;
import org.update4j.service.UpdateHandler;
import org.update4j.util.FileUtils;
import org.update4j.util.ModuleUtils;
import org.update4j.util.StringUtils;
import org.update4j.util.Warning;

class ConfigImpl {

    private ConfigImpl() {
    }

    @Deprecated
    static boolean doLegacyUpdate(Configuration config, Path tempDir, PublicKey key, Injectable injectable,
                    UpdateHandler handler) {

        boolean updateTemp = tempDir != null;
        boolean doneDownloads = false;
        boolean success;

        // if no explicit handler were passed
        if (handler == null) {
            handler = Service.loadService(UpdateHandler.class, config.getUpdateHandler());
        }

        if (injectable != null) {
            try {
                Injectable.injectBidirectional(injectable, handler);
            } catch (IllegalAccessException | InvocationTargetException | UnsatisfiedInjectionException e) {
                throw new RuntimeException(e);
            }
        }

        // to be moved in final location after all files completed download
        // or -- in case if updateTemp -- in Update.finalizeUpdate()
        Map<FileMetadata, Path> downloadedCollection = new HashMap<>();

        try {
            List<FileMetadata> requiresUpdate = new ArrayList<>();
            List<FileMetadata> updated = new ArrayList<>();

            UpdateContext ctx = new UpdateContext(config, requiresUpdate, updated, tempDir, key, null);
            handler.init(ctx);

            handler.startCheckUpdates();
            handler.updateCheckUpdatesProgress(0f);

            List<FileMetadata> osFiles = config.getFiles()
                            .stream()
                            .filter(file -> file.getOs() == null || file.getOs() == OS.CURRENT)
                            .collect(Collectors.toList());

            long updateJobSize = osFiles.stream().mapToLong(FileMetadata::getSize).sum();
            double updateJobCompleted = 0;

            for (FileMetadata file : osFiles) {
                if (handler.shouldCheckForUpdate(file)) {
                    handler.startCheckUpdateFile(file);

                    boolean needsUpdate = file.requiresUpdate();

                    if (needsUpdate)
                        requiresUpdate.add(file);

                    handler.doneCheckUpdateFile(file, needsUpdate);
                }

                updateJobCompleted += file.getSize();
                handler.updateCheckUpdatesProgress(clamp((float) (updateJobCompleted / updateJobSize)));
            }

            handler.doneCheckUpdates();

            Signature sig = null;
            if (key != null) {
                String alg = key.getAlgorithm().equals("EC") ? "ECDSA" : key.getAlgorithm();
                sig = Signature.getInstance("SHA256with" + alg);
                sig.initVerify(key);
            }

            long downloadJobSize = requiresUpdate.stream().mapToLong(FileMetadata::getSize).sum();
            double downloadJobCompleted = 0;

            if (!requiresUpdate.isEmpty()) {
                if (key == null) {
                    Warning.signature();
                }
                
                handler.startDownloads();

                for (FileMetadata file : requiresUpdate) {
                    handler.startDownloadFile(file);

                    int read = 0;
                    double currentCompleted = 0;
                    byte[] buffer = new byte[1024 * 8];

                    Path output;
                    if (!updateTemp) {
                        Files.createDirectories(file.getNormalizedPath().getParent());
                        output = Files.createTempFile(file.getNormalizedPath().getParent(), null, null);
                    } else {
                        Files.createDirectories(tempDir);
                        output = Files.createTempFile(tempDir, null, null);
                    }
                    downloadedCollection.put(file, output);

                    try (InputStream in = handler.openDownloadStream(file);
                                    OutputStream out = Files.newOutputStream(output)) {

                        // We should set download progress only AFTER the request has returned.
                        // The delay can be monitored by the difference between calls from startDownload to this.
                        if (downloadJobCompleted == 0) {
                            handler.updateDownloadProgress(0f);
                        }
                        handler.updateDownloadFileProgress(file, 0f);

                        while ((read = in.read(buffer, 0, buffer.length)) > -1) {
                            out.write(buffer, 0, read);

                            if (sig != null) {
                                sig.update(buffer, 0, read);
                            }

                            downloadJobCompleted += read;
                            currentCompleted += read;

                            handler.updateDownloadFileProgress(file, clamp((float) (currentCompleted / file.getSize())));
                            handler.updateDownloadProgress(clamp((float) downloadJobCompleted / downloadJobSize));
                        }

                        handler.validatingFile(file, output);
                        validateFile(file, output, sig);

                        updated.add(file);
                        handler.doneDownloadFile(file, output);

                    }
                }

                completeDownloads(downloadedCollection, tempDir, updateTemp);
                doneDownloads = true;

                handler.doneDownloads();
            }

            success = true;
        } catch (Throwable t) {
            // clean-up as update failed

            try {
                // if an exception was thrown in handler.doneDownloads()
                // done delete files, as they are now in their final location
                if ((updateTemp || !doneDownloads) && !downloadedCollection.isEmpty()) {
                    for (Path p : downloadedCollection.values()) {
                        Files.deleteIfExists(p);
                    }

                    if (updateTemp) {
                        Files.deleteIfExists(tempDir.resolve(Update.UPDATE_DATA));

                        if (FileUtils.isEmptyDirectory(tempDir)) {
                            Files.deleteIfExists(tempDir);
                        }
                    }

                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            Warning.lock(t);

            success = false;
            handler.failed(t);
        }

        if (success)
            handler.succeeded();

        handler.stop();

        return success;

    }

    static UpdateResult doUpdate(Configuration config, ArchiveUpdateOptions options) {
        PublicKey key = options.getPublicKey();

        Throwable exception = null;
        boolean doneDownloads = false;
        boolean success;

        UpdateHandler handler = options.getUpdateHandler();
        // if no explicit handler were passed
        if (handler == null) {
            handler = Service.loadService(UpdateHandler.class, config.getUpdateHandler());
        }

        if (options.getInjectable() != null) {
            try {
                Injectable.injectBidirectional(options.getInjectable(), handler);
            } catch (IllegalAccessException | InvocationTargetException | UnsatisfiedInjectionException e) {
                throw new RuntimeException(e);
            }
        }

        try {
            List<FileMetadata> requiresUpdate = new ArrayList<>();
            List<FileMetadata> updated = new ArrayList<>();

            UpdateContext ctx = new UpdateContext(config, requiresUpdate, updated, null, options.getPublicKey(),
                            options.getArchiveLocation());

            handler.init(ctx);

            handler.startCheckUpdates();
            handler.updateCheckUpdatesProgress(0f);

            List<FileMetadata> osFiles = config.getFiles()
                            .stream()
                            .filter(file -> file.getOs() == null || file.getOs() == OS.CURRENT)
                            .collect(Collectors.toList());

            long updateJobSize = osFiles.stream().mapToLong(FileMetadata::getSize).sum();
            double updateJobCompleted = 0;

            for (FileMetadata file : osFiles) {
                if (handler.shouldCheckForUpdate(file)) {
                    handler.startCheckUpdateFile(file);

                    boolean needsUpdate = file.requiresUpdate();

                    if (needsUpdate)
                        requiresUpdate.add(file);

                    handler.doneCheckUpdateFile(file, needsUpdate);
                }

                updateJobCompleted += file.getSize();
                handler.updateCheckUpdatesProgress(clamp((float) (updateJobCompleted / updateJobSize)));
            }

            handler.doneCheckUpdates();

            Signature sig = null;
            if (key != null) {
                String alg = key.getAlgorithm().equals("EC") ? "ECDSA" : key.getAlgorithm();
                sig = Signature.getInstance("SHA256with" + alg);
                sig.initVerify(key);
            }

            long downloadJobSize = requiresUpdate.stream().mapToLong(FileMetadata::getSize).sum();
            double downloadJobCompleted = 0;

            if (!requiresUpdate.isEmpty()) {
                if (key == null) {
                    Warning.signature();
                }
                
                Archive archive = new Archive(options.getArchiveLocation());
                try (FileSystem zip = archive.openConnection()) {

                    // first save the config in the archive
                    Path configPath = zip.getPath(Archive.RESERVED_DIR, Archive.CONFIG_PATH);
                    Files.createDirectories(configPath.getParent());
                    try (BufferedWriter out = Files.newBufferedWriter(configPath)) {
                        config.write(out);
                    }
                    
                    // Save dynamic properties, if any. #110
                    if(!config.getDynamicProperties().isEmpty()) {
                        Path dynamicPath = zip.getPath(Archive.RESERVED_DIR,  Archive.DYNAMIC_PATH);
                        try(BufferedWriter out = Files.newBufferedWriter(dynamicPath)) {
                            MapMapper.write(out, config.getDynamicProperties(), Archive.DYNAMIC_NODE);
                        }
                    }
                    

                    handler.startDownloads();
                    for (FileMetadata file : requiresUpdate) {
                        handler.startDownloadFile(file);

                        Path output = zip.getPath(Archive.FILES_DIR).resolve(file.getNormalizedPath().toString()
                                .replaceFirst("^\\/", ""));
                        Files.createDirectories(output.getParent());

                        int read = 0;
                        double currentCompleted = 0;
                        byte[] buffer = new byte[1024 * 8];

                        try (InputStream in = handler.openDownloadStream(file);
                                        OutputStream out = Files.newOutputStream(output)) {

                            // We should set download progress only AFTER the request has returned.
                            // The delay can be monitored by the difference between calls from startDownload to this.
                            if (downloadJobCompleted == 0) {
                                handler.updateDownloadProgress(0f);
                            }
                            handler.updateDownloadFileProgress(file, 0f);

                            while ((read = in.read(buffer, 0, buffer.length)) > -1) {
                                out.write(buffer, 0, read);

                                if (sig != null) {
                                    sig.update(buffer, 0, read);
                                }

                                downloadJobCompleted += read;
                                currentCompleted += read;

                                handler.updateDownloadFileProgress(file, clamp((float) (currentCompleted / file.getSize())));
                                handler.updateDownloadProgress(clamp((float) downloadJobCompleted / downloadJobSize));
                            }

                        }

                        handler.validatingFile(file, output);
                        validateFile(file, output, sig);

                        updated.add(file);
                        handler.doneDownloadFile(file, output);
                    }

                    doneDownloads = true;
                    handler.doneDownloads();
                }
            }

            success = true;
        } catch (Throwable t) {
            exception = t;

            if (!doneDownloads) {
                try {
                    Files.deleteIfExists(options.getArchiveLocation());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            Warning.lock(t);

            success = false;
            handler.failed(t);
        }

        if (success)
            handler.succeeded();

        handler.stop();
        return new UpdateResult(handler, exception);

    }

    @Deprecated
    private static void completeDownloads(Map<FileMetadata, Path> files, Path tempDir, boolean isTemp)
                    throws IOException {

        if (!files.isEmpty()) {

            // if update regular
            if (!isTemp) {
                for (Path p : files.values()) {

                    // these update handlers may do some interference
                    if (Files.notExists(p)) {
                        throw new NoSuchFileException(p.toString());
                    }
                }

                for (FileMetadata fm : files.keySet()) {
                    FileUtils.verifyAccessible(fm.getNormalizedPath());
                }

                // mimic a single transaction.
                // if it fails in between moves, we're doomed
                for (Map.Entry<FileMetadata, Path> entry : files.entrySet()) {
                    FileUtils.secureMoveFile(entry.getValue(), entry.getKey().getNormalizedPath());
                }
            }

            // otherwise if update temp, save to file
            else {
                Path updateDataFile = tempDir.resolve(Update.UPDATE_DATA);

                // Path is not serializable, so convert to file
                Map<File, File> updateTempData = new HashMap<>();
                for (Map.Entry<FileMetadata, Path> entry : files.entrySet()) {

                    // gotta swap keys and values to get source -> target
                    updateTempData.put(entry.getValue().toFile(), entry.getKey().getNormalizedPath().toFile());
                }

                try (ObjectOutputStream out = new ObjectOutputStream(
                                Files.newOutputStream(updateDataFile, StandardOpenOption.CREATE))) {
                    out.writeObject(updateTempData);
                }

                FileUtils.windowsHidden(updateDataFile, true);
            }
        }
    }

    private static void validateFile(FileMetadata file, Path output, Signature sig)
                    throws IOException, SignatureException {

        long actualSize = Files.size(output);
        if (actualSize != file.getSize()) {
            throw new IllegalStateException("Size of file '" + file.getPath().getFileName()
                            + "' does not match size in configuration. Expected: " + file.getSize() + ", found: "
                            + actualSize);
        }

        long actualChecksum = FileUtils.getChecksum(output);
        if (actualChecksum != file.getChecksum()) {
            throw new IllegalStateException("Checksum of file '" + file.getPath().getFileName()
                            + "' does not match checksum in configuration. Expected: "
                            + Long.toHexString(file.getChecksum()) + ", found: " + Long.toHexString(actualChecksum));
        }

        if (sig != null) {
            if (file.getSignature() == null)
                throw new SecurityException("Missing signature.");

            if (!sig.verify(Base64.getDecoder().decode(file.getSignature())))
                throw new SecurityException("Signature verification failed.");
        }

        if (file.getPath().toString().endsWith(".jar") && !file.isIgnoreBootConflict()
                        && !ModuleUtils.userBootModules().isEmpty()) {
            checkBootConflicts(file, output);
        }
    }

    private static void checkBootConflicts(FileMetadata file, Path download) throws IOException {
        String filename = file.getPath().getFileName().toString();

        if (!FileUtils.isZipFile(download)) {
            Warning.nonZip(filename);
            throw new IllegalStateException("File '" + filename + "' is not a valid zip file.");
        }

        Set<Module> modules = ModuleLayer.boot().modules();
        Set<String> moduleNames = modules.stream().map(Module::getName).collect(Collectors.toSet());

        Set<String> sysMods = ModuleFinder.ofSystem()
                        .findAll()
                        .stream()
                        .map(mr -> mr.descriptor().name())
                        .collect(Collectors.toSet());

        ModuleDescriptor newMod = null;
        try {
            newMod = ModuleUtils.deriveModuleDescriptor(download, filename, sysMods.contains("jdk.zipfs"));
        } catch (IllegalArgumentException | InvalidModuleDescriptorException | FindException e) {
            Warning.illegalModule(filename);
            throw e;
        }

        if (moduleNames.contains(newMod.name())) {
            Warning.moduleConflict(newMod.name());
            throw new IllegalStateException(
                            "Module '" + newMod.name() + "' conflicts with a module in the boot modulepath");
        }

        Set<String> packages = modules.stream().flatMap(m -> m.getPackages().stream()).collect(Collectors.toSet());
        for (String p : newMod.packages()) {
            if (packages.contains(p)) {
                Warning.packageConflict(p);
                throw new IllegalStateException("Package '" + p + "' in module '" + newMod.name()
                                + "' conflicts with a package in the boot modulepath");

            }
        }

        for (Requires require : newMod.requires()) {

            // static requires are not mandatory
            if (require.modifiers().contains(Requires.Modifier.STATIC))
                continue;

            String reqName = require.name();
            if (StringUtils.isSystemModule(reqName)) {
                if (!sysMods.contains(reqName)) {
                    Warning.missingSysMod(reqName);
                    throw new IllegalStateException("System module '" + reqName
                                    + "' is missing from JVM image, required by '" + newMod.name() + "'");
                }

            }
        }
    }

    static void doLaunch(Configuration config, Injectable injectable, Launcher launcher) {
        List<FileMetadata> modules = config.getFiles()
                        .stream()
                        .filter(file -> file.getOs() == null || file.getOs() == OS.CURRENT)
                        .filter(FileMetadata::isModulepath)
                        .collect(Collectors.toList());

        List<Path> modulepaths = modules.stream().map(FileMetadata::getNormalizedPath).collect(Collectors.toList());

        List<URL> classpaths = config.getFiles()
                        .stream()
                        .filter(file -> file.getOs() == null || file.getOs() == OS.CURRENT)
                        .filter(FileMetadata::isClasspath)
                        .map(FileMetadata::getNormalizedPath)
                        .map(path -> {
                            try {
                                return path.toUri().toURL();
                            } catch (MalformedURLException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .collect(Collectors.toList());

        //Warn potential problems
        if (modulepaths.isEmpty() && classpaths.isEmpty()) {
            Warning.path();
        }

        ModuleFinder finder = ModuleFinder.of(modulepaths.toArray(new Path[modulepaths.size()]));

        Set<ModuleDescriptor> moduleDescriptors = finder.findAll()
                        .stream()
                        .map(mr -> mr.descriptor())
                        .collect(Collectors.toSet());

        // Warn if any module requires an unresolved system module
        if (Warning.shouldWarn("unresolvedSystemModules")) {
            Set<String> resolvedSysMods = ModuleLayer.boot()
                            .modules()
                            .stream()
                            .map(m -> m.getName())
                            .collect(Collectors.toSet());

            List<String> missingSysMods = new ArrayList<>();

            for (ModuleDescriptor descriptor : moduleDescriptors) {
                for (Requires require : descriptor.requires()) {

                    // static requires are not mandatory
                    if (require.modifiers().contains(Requires.Modifier.STATIC))
                        continue;

                    String reqName = require.name();
                    if (StringUtils.isSystemModule(reqName)) {
                        if (!resolvedSysMods.contains(reqName)) {
                            missingSysMods.add(reqName);
                        }
                    }
                }
            }
            if (missingSysMods.size() > 0)
                Warning.unresolvedSystemModules(missingSysMods);

        }

        List<String> moduleNames = moduleDescriptors.stream().map(ModuleDescriptor::name).collect(Collectors.toList());

        ModuleLayer parent = ModuleLayer.boot();
        java.lang.module.Configuration cf = parent.configuration()
                        .resolveAndBind(finder, ModuleFinder.of(), moduleNames);

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        DynamicClassLoader dynamic = DynamicClassLoader.findAncestor(contextClassLoader);
        if (dynamic != null) {
            for (URL url : classpaths) {
                dynamic.add(url);
            }
        } else if (classpaths.size() > 0) {
            contextClassLoader = new URLClassLoader("classpath", classpaths.toArray(new URL[classpaths.size()]),
                            contextClassLoader);
        }

        ModuleLayer.Controller controller = ModuleLayer.defineModulesWithOneLoader(cf, List.of(parent),
                        contextClassLoader);
        ModuleLayer layer = controller.layer();

        // manipulate exports, opens and reads
        for (FileMetadata mod : modules) {
            if (!mod.getAddExports().isEmpty() || !mod.getAddOpens().isEmpty() || !mod.getAddReads().isEmpty()) {
                ModuleReference reference = finder.findAll()
                                .stream()
                                .filter(ref -> new File(ref.location().get()).toPath().equals(mod.getNormalizedPath()))
                                .findFirst()
                                .orElseThrow(IllegalStateException::new);

                Module source = layer.findModule(reference.descriptor().name()).orElseThrow(IllegalStateException::new);

                for (AddPackage export : mod.getAddExports()) {
                    Module target = layer.findModule(export.getTargetModule())
                                    .orElseThrow(() -> new IllegalStateException("Module '" + export.getTargetModule()
                                                    + "' is not known to the layer."));

                    controller.addExports(source, export.getPackageName(), target);
                }

                for (AddPackage open : mod.getAddOpens()) {
                    Module target = layer.findModule(open.getTargetModule())
                                    .orElseThrow(() -> new IllegalStateException("Module '" + open.getTargetModule()
                                                    + "' is not known to the layer."));

                    controller.addOpens(source, open.getPackageName(), target);
                }

                for (String read : mod.getAddReads()) {
                    Module target = layer.findModule(read)
                                    .orElseThrow(() -> new IllegalStateException(
                                                    "Module '" + read + "' is not known to the layer."));

                    controller.addReads(source, target);
                }
            }
        }

        if (moduleNames.size() > 0) {
            contextClassLoader = layer.findLoader(moduleNames.get(0));
        }

        LaunchContext ctx = new LaunchContext(layer, contextClassLoader, config);

        boolean usingSpi = launcher == null;
        if (usingSpi) {
            launcher = Service.loadService(layer, contextClassLoader, Launcher.class, config.getLauncher());

            if (injectable != null) {
                try {
                    Injectable.injectBidirectional(injectable, launcher);
                } catch (IllegalAccessException | InvocationTargetException | UnsatisfiedInjectionException e) {
                    throw new RuntimeException(e);
                }
            }

        }

        Launcher finalLauncher = launcher;

        Thread t = new Thread(() -> {
            try {
                finalLauncher.run(ctx);
            } catch (NoClassDefFoundError e) {
                if (usingSpi) {
                    if (finalLauncher.getClass().getClassLoader() == ClassLoader.getSystemClassLoader()) {
                        Warning.access(finalLauncher);
                    }
                } else {
                    Warning.reflectiveAccess(finalLauncher);
                }

                throw e;
            }
        });
        t.setContextClassLoader(contextClassLoader);
        t.start();

        while (t.isAlive()) {
            try {
                t.join();
            } catch (InterruptedException e) {
            }
        }
    }
    
    private static float clamp(float val) {
        return Math.max(0,  Math.min(1, val));
    }
    
}
