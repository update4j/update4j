package org.update4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Requires;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

import org.update4j.inject.Injectable;
import org.update4j.inject.UnsatisfiedInjectionException;
import org.update4j.service.Launcher;
import org.update4j.service.Service;
import org.update4j.service.UpdateHandler;
import org.update4j.util.FileUtils;
import org.update4j.util.StringUtils;
import org.update4j.util.Warning;

class ConfigImpl {

	private ConfigImpl() {
	}

	static boolean doUpdate(Configuration config, Path tempDir, PublicKey key, Injectable injectable,
					UpdateHandler handler) {

		if (key == null) {
			Warning.signature();
		}

		boolean updateTemp = tempDir != null;
		boolean success;

		// if no explicit handler were passed
		if (handler == null) {
			handler = Service.loadService(UpdateHandler.class, config.getUpdateHandler());

			if (injectable != null) {
				try {
					Injectable.injectBidirectional(injectable, handler);
				} catch (IllegalAccessException | InvocationTargetException | UnsatisfiedInjectionException e) {
					throw new RuntimeException(e);
				}
			}
		}

		// to be moved in final location after all files completed download
		// or -- in case if updateTemp -- in Update.finalizeUpdate()
		Map<FileMetadata, Path> downloadedCollection = new HashMap<>();

		try {
			List<FileMetadata> requiresUpdate = new ArrayList<>();
			List<FileMetadata> updated = new ArrayList<>();

			UpdateContext ctx = new UpdateContext(config, requiresUpdate, updated, tempDir, key);
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
				if(handler.shouldCheckForUpdate(file) {
					handler.startCheckUpdateFile(file)
				
					boolean needsUpdate = file.requiresUpdate();

					if (needsUpdate)
						requiresUpdate.add(file);

					handler.doneCheckUpdateFile(file, needsUpdate);
				}
				
				updateJobCompleted += file.getSize();
				handler.updateCheckUpdatesProgress((float) (updateJobCompleted / updateJobSize));
			}

			handler.doneCheckUpdates();

			Signature sig = null;
			if (key != null) {
				sig = Signature.getInstance("SHA256with" + key.getAlgorithm());
				sig.initVerify(key);
			}

			long downloadJobSize = requiresUpdate.stream().mapToLong(FileMetadata::getSize).sum();
			double downloadJobCompleted = 0;

			if (!requiresUpdate.isEmpty()) {
				handler.startDownloads();

				for (FileMetadata file : requiresUpdate) {
					handler.startDownloadFile(file);

					int read = 0;
					double currentCompleted = 0;
					byte[] buffer = new byte[1024];

					Path output;
					if (!updateTemp) {
						Files.createDirectories(file.getPath().getParent());
						output = Files.createTempFile(file.getPath().getParent(), null, null);
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

							handler.updateDownloadFileProgress(file, (float) (currentCompleted / file.getSize()));
							handler.updateDownloadProgress((float) downloadJobCompleted / downloadJobSize);
						}

						handler.validatingFile(file, output);
						validateFile(file, output, sig);

						updated.add(file);
						handler.doneDownloadFile(file, output);

					}
				}

				completeDownloads(downloadedCollection, tempDir, updateTemp);

				handler.doneDownloads();
			}

			handler.succeeded();
			success = true;

		} catch (Throwable t) {
			// clean-up as update failed

			try {
				if (!downloadedCollection.isEmpty()) {
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

			if (t instanceof FileSystemException) {
				FileSystemException fse = (FileSystemException) t;

				String msg = t.getMessage();
				if (msg.contains("another process") || msg.contains("lock") || msg.contains("use")) {
					Warning.lock(fse.getFile());
				}
			}

			handler.failed(t);
			success = false;
		}

		handler.stop();

		return success;

	}

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
					FileUtils.verifyNotLocked(fm.getPath());
				}

				// mimic a single transaction.
				// if it fails in between moves, we're doomed
				for (Map.Entry<FileMetadata, Path> entry : files.entrySet()) {
					FileUtils.secureMoveFile(entry.getValue(), entry.getKey().getPath());
				}
			}

			// otherwise if update temp, save to file
			if (isTemp) {
				Path updateDataFile = tempDir.resolve(Update.UPDATE_DATA);

				// Path is not serializable, so convert to file
				Map<File, File> updateTempData = new HashMap<>();
				for (Map.Entry<FileMetadata, Path> entry : files.entrySet()) {

					// gotta swap keys and values to get source -> target
					updateTempData.put(entry.getValue().toFile(), entry.getKey().getPath().toFile());
				}

				try (ObjectOutputStream out = new ObjectOutputStream(
								Files.newOutputStream(updateDataFile, StandardOpenOption.CREATE))) {
					out.writeObject(updateTempData);
				}

				FileUtils.windowsHide(updateDataFile);
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

		if (file.getPath().toString().endsWith(".jar") && !file.isIgnoreBootConflict()) {
			checkBootConflicts(file, output);
		}
	}

	private static void checkBootConflicts(FileMetadata file, Path download) throws IOException {
		if (!FileUtils.isZipFile(download)) {
			Warning.nonZip(file.getPath().getFileName().toString());
			throw new IllegalStateException(
							"File '" + file.getPath().getFileName().toString() + "' is not a valid zip file.");
		}

		Set<Module> modules = ModuleLayer.boot().modules();
		Set<String> moduleNames = modules.stream().map(Module::getName).collect(Collectors.toSet());

		ModuleDescriptor newMod = null;

		// ModuleFinder will not cooperate otherwise
		String newFilename = "a" + download.getFileName().toString();
		Path newPath = download.getParent().resolve(newFilename + ".jar");

		try {
			Files.move(download, newPath);
			newMod = ModuleFinder.of(newPath)
							.findAll()
							.stream()
							.map(ModuleReference::descriptor)
							.findAny()
							.orElse(null);
		} finally {
			Files.move(newPath, download, StandardCopyOption.REPLACE_EXISTING);
		}

		if (newMod == null)
			return;

		// non-modular and no Automatic-Module-Name
		// use real filename as module name
		String newModuleName;
		if (newFilename.equals(newMod.name())) {
			newModuleName = StringUtils.deriveModuleName(file.getPath().getFileName().toString());
			if (!StringUtils.isModuleName(newModuleName)) {
				Warning.illegalAutomaticModule(newModuleName, file.getPath().getFileName().toString());
				throw new IllegalStateException("Automatic module name '" + newModuleName + "' for file '"
								+ file.getPath().getFileName() + "' is not valid.");
			}
		} else {
			newModuleName = newMod.name();
		}

		if (moduleNames.contains(newModuleName)) {
			Warning.moduleConflict(newModuleName);
			throw new IllegalStateException(
							"Module '" + newModuleName + "' conflicts with a module in the boot modulepath");
		}

		Set<String> packages = modules.stream().flatMap(m -> m.getPackages().stream()).collect(Collectors.toSet());
		for (String p : newMod.packages()) {
			if (packages.contains(p)) {
				Warning.packageConflict(p);
				throw new IllegalStateException("Package '" + p + "' in module '" + newMod.name()
								+ "' conflicts with a package in the boot modulepath");

			}
		}

		Set<String> sysMods = ModuleFinder.ofSystem()
						.findAll()
						.stream()
						.map(mr -> mr.descriptor().name())
						.collect(Collectors.toSet());

		for (Requires require : newMod.requires()) {
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

		List<Path> modulepaths = modules.stream().map(FileMetadata::getPath).collect(Collectors.toList());

		List<URL> classpaths = config.getFiles()
						.stream()
						.filter(file -> file.getOs() == null || file.getOs() == OS.CURRENT)
						.filter(FileMetadata::isClasspath)
						.map(FileMetadata::getPath)
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
				for(Requires require : descriptor.requires()) {
					String reqName = require.name();
					if(StringUtils.isSystemModule(reqName)) {
						if(!resolvedSysMods.contains(reqName)) {
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
						.resolveAndBind(ModuleFinder.of(), finder, moduleNames);

		ClassLoader parentClassLoader = Thread.currentThread().getContextClassLoader();
		ClassLoader classpathLoader = new URLClassLoader("classpath", classpaths.toArray(new URL[classpaths.size()]),
						parentClassLoader);

		ModuleLayer.Controller controller = ModuleLayer.defineModulesWithOneLoader(cf, List.of(parent),
						classpathLoader);
		ModuleLayer layer = controller.layer();

		// manipulate exports, opens and reads
		for (FileMetadata mod : modules) {
			if (!mod.getAddExports().isEmpty() || !mod.getAddOpens().isEmpty() || !mod.getAddReads().isEmpty()) {
				ModuleReference reference = finder.findAll()
								.stream()
								.filter(ref -> new File(ref.location().get()).toPath().equals(mod.getPath()))
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

		ClassLoader contextClassLoader = classpathLoader;
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
}
