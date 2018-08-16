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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.update4j.mapper.ConfigMapper;
import org.update4j.mapper.FileMapper;
import org.update4j.service.Launcher;
import org.update4j.service.Service;
import org.update4j.service.UpdateHandler;
import org.update4j.util.FileUtils;
import org.update4j.util.PropertyManager;
import org.update4j.util.PropertyUtils;
import org.update4j.util.StringUtils;
import org.update4j.util.Warning;

/**
 * This class is the heart of the framework. It essentially wraps around a
 * configuration XML file and does the update/launch logic.
 * 
 * 
 * @author Mordechai Meisels
 *
 */
public class Configuration {

	private Instant timestamp;

	private URI baseUri;
	private Path basePath;
	private String updateHandler;
	private String launcher;

	private List<FileMetadata> files;
	private List<FileMetadata> unmodifiableFiles;
	private PropertyManager propertyManager;

	private ConfigMapper mapper;

	private Configuration() {
	}

	/**
	 * Returns the timestamp this configuration was last updated, using the
	 * {@link Configuration.Builder} API. This is read from the {@code timestamp}
	 * attribute in the root element. It does not have any effect on the behavior of
	 * anything else; it is rather just for reference purposes (i.e. "Last Updated:
	 * 2 Weeks Ago"), or for clients willing to act according to this value.
	 * 
	 * 
	 * @return The timestamp this configuration was last updated.
	 */
	public Instant getTimestamp() {
		return timestamp;
	}

	/**
	 * Returns the base URI against whom all <i>relative</i> URIs in individual
	 * files are resolved. The URI points to the remote (or if it has a
	 * {@code file:///} scheme, local) location from where the file should be
	 * downloaded.
	 * 
	 * 
	 * <p>
	 * This is read from the {@code uri} attribute from the {@code <base>} element.
	 * If the attribute is missing this will return {@code null}.
	 * 
	 * @return The base URI.
	 */
	public URI getBaseUri() {
		return baseUri;
	}

	/**
	 * Returns the base path against whom all <i>relative</i> paths in individual
	 * files are resolved. The path points to the location the files should be saved
	 * to on the client's local machine.
	 * 
	 * <p>
	 * This is read from the {@code path} attribute from the {@code <base>} element.
	 * If the attribute is missing this will return {@code null}.
	 * 
	 * @return The base path.
	 */
	public Path getBasePath() {
		return basePath;
	}

	/**
	 * Returns the {@link UpdateHandler} class name that should be used instead of
	 * of the default highest version currently present in the classpath or
	 * modulepath.
	 * 
	 * <p>
	 * <b>Note:</b> This is completely optional. If this is missing the framework
	 * will automatically load the highest version currently present in the
	 * classpath or modulepath. Please refer to <a
	 * href=https://github.com/update4j/update4j/wiki/Documentation#dealing-with-providers>
	 * Dealing with Providers</a> for more info.
	 * 
	 * 
	 * <p>
	 * This is read from the {@code updateHandler} attribute from the
	 * {@code <provider>} element. If the attribute is missing this will return
	 * {@code null}.
	 * 
	 * @return The {@link UpdateHandler} class name that should be used instead of
	 *         of the default highest version.
	 */
	public String getUpdateHandler() {
		return updateHandler;
	}

	/**
	 * Returns the {@link Launcher} class name that should be used instead of of the
	 * default highest version currently present in the classpath or modulepath.
	 * 
	 * <p>
	 * <b>Note:</b> This is completely optional. If this is missing the framework
	 * will automatically load the highest version currently present in the
	 * classpath or modulepath. Please refer to <a
	 * href=https://github.com/update4j/update4j/wiki/Documentation#dealing-with-providers>
	 * Dealing with Providers</a> for more info.
	 * 
	 * 
	 * <p>
	 * This is read from the {@code launcher} attribute from the {@code <provider>}
	 * element. If the attribute is missing this will return {@code null}.
	 * 
	 * @return The {@link Launcher} class name that should be used instead of of the
	 *         default highest version.
	 */
	public String getLauncher() {
		return launcher;
	}

	/**
	 * Returns the list of files listed in the configuration file. This will never
	 * return {@code null}.
	 * 
	 * <p>
	 * These are read from the {@code <files>} element.
	 * 
	 * @return The {@link FileMetadata} instances listed in the configuration file.
	 */
	public List<FileMetadata> getFiles() {
		return unmodifiableFiles;
	}

	/**
	 * Returns an unmodifiable list of properties listed in the configuration file.
	 * This will never return {@code null}.
	 * 
	 * <p>
	 * This is read from the {@code <properties>} element.
	 * 
	 * @return The {@link Property} instances listed in the configuration file.
	 */
	public List<Property> getUserProperties() {
		return propertyManager.getUserProperties();
	}

	/**
	 * Returns the {@link Property} with the corresponding key, or {@code null} if
	 * missing. If there are more than one property with the given key (if they are
	 * platform specific), only one will be returned.
	 * 
	 * @param key
	 *            The key of the property.
	 * @return The {@link Property} with the given key.
	 */
	public Property getUserProperty(String key) {
		return propertyManager.getUserProperty(key);
	}

	/**
	 * Returns a list of properties with the corresponding key, or empty if non are
	 * found. There might be more than one property with the given key, if they are
	 * platform specific.
	 * 
	 * @param key
	 *            The key of the property.
	 * @return A list of properties with the given key.
	 */
	public List<Property> getUserProperties(String key) {
		return propertyManager.getUserProperties(key);
	}

	/**
	 * Returns the value of the property with the corresponding key, or {@code null}
	 * if missing. This method will ignore properties marked for foreign operating
	 * systems.
	 * 
	 * @param key
	 *            The key of the property.
	 * @return The value of the property with the given key.
	 */
	public String getUserPropertyForCurrent(String key) {
		return propertyManager.getUserPropertyForCurrent(key);
	}

	/**
	 * Returns an unmodifiable map of keys and values after resolving the
	 * placeholders. This will not include properties marked for foreign operating
	 * systems.
	 * 
	 * @return A map of the keys and real values of the properties, after resolving
	 *         the placeholders.
	 */
	public Map<String, String> getResolvedProperties() {
		return propertyManager.getResolvedProperties();
	}

	/**
	 * Returns the real value of the property with the given key, after resolving
	 * the placeholders.
	 * 
	 * @param key
	 *            The key of the property.
	 * @return The real value of the property after resolving the placeholders.
	 */
	public String getResolvedProperty(String key) {
		return propertyManager.getResolvedProperty(key);
	}

	/**
	 * Returns a string where all placeholders are replaced with the real values.
	 * 
	 * <p>
	 * If it includes a reference to a foreign property that could not be resolved
	 * (as if that property refers to a system dependent system property), the
	 * placeholder will not be replaced.
	 * 
	 * @param str
	 *            The source string to try to resolve.
	 * @return The resolved string.
	 * @throws IllegalArgumentException
	 *             if the source string contains a placeholder that could not be
	 *             resolved.
	 */
	public String resolvePlaceholders(String str) {
		return propertyManager.resolvePlaceholders(str);
	}

	protected String resolvePlaceholders(String str, boolean isPath) {
		return propertyManager.resolvePlaceholders(str, isPath);
	}

	/*
	 * ignoreForeignProperty will not throw an exception if the key is found in an
	 * unresolved foreign property.
	 */
	protected String resolvePlaceholders(String str, boolean isPath, boolean ignoreForeignProperty) {
		return propertyManager.resolvePlaceholders(str, isPath, ignoreForeignProperty);
	}

	public String implyPlaceholders(String str) {
		return propertyManager.implyPlaceholders(str);
	}

	public String implyPlaceholders(String str, boolean isPath) {
		return propertyManager.implyPlaceholders(str, isPath);
	}

	public String implyPlaceholders(String str, PlaceholderMatchType matchType) {
		return propertyManager.implyPlaceholders(str, matchType);
	}

	public String implyPlaceholders(String str, PlaceholderMatchType matchType, boolean isPath) {
		return propertyManager.implyPlaceholders(str, matchType, isPath);
	}

	public boolean requiresUpdate() throws IOException {
		for (FileMetadata file : getFiles()) {
			if (file.requiresUpdate())
				return true;
		}

		return false;
	}

	public boolean update() {
		return update((PublicKey) null);
	}

	public boolean update(Consumer<? super UpdateHandler> handlerSetup) {
		return update((PublicKey) null, handlerSetup);
	}

	public boolean update(PublicKey key) {
		return update(key, null);
	}

	public boolean update(PublicKey key, Consumer<? super UpdateHandler> handlerSetup) {
		return updateImpl(null, key, handlerSetup);
	}

	public boolean updateTemp(Path tempDir) {
		return updateTemp(tempDir, (PublicKey) null);
	}

	public boolean updateTemp(Path tempDir, Consumer<? super UpdateHandler> handlerSetup) {
		return updateTemp(tempDir, (PublicKey) null, handlerSetup);
	}

	public boolean updateTemp(Path tempDir, PublicKey key) {
		return updateTemp(tempDir, key, null);
	}

	public boolean updateTemp(Path tempDir, PublicKey key, Consumer<? super UpdateHandler> handlerSetup) {
		return updateImpl(Objects.requireNonNull(tempDir), key, handlerSetup);
	}

	private boolean updateImpl(Path tempDir, PublicKey key, Consumer<? super UpdateHandler> handlerSetup) {
		boolean success;

		UpdateHandler handler = Service.loadService(UpdateHandler.class, updateHandler);

		if (handlerSetup != null) {
			handlerSetup.accept(handler);
		}

		Map<File, File> updateData = null;

		try {
			List<FileMetadata> requiresUpdate = new ArrayList<>();
			List<FileMetadata> updated = new ArrayList<>();

			UpdateContext ctx = new UpdateContext(this, requiresUpdate, updated, tempDir, key);
			handler.init(ctx);

			handler.startCheckUpdates();
			handler.updateCheckUpdatesProgress(0f);

			List<FileMetadata> osFiles = getFiles().stream()
							.filter(file -> file.getOs() == null || file.getOs() == OS.CURRENT)
							.collect(Collectors.toList());

			long updateJobSize = osFiles.stream()
							.mapToLong(FileMetadata::getSize)
							.sum();
			double updateJobCompleted = 0;

			for (FileMetadata file : osFiles) {
				handler.startCheckUpdateFile(file);

				boolean needsUpdate = file.requiresUpdate();

				if (needsUpdate)
					requiresUpdate.add(file);

				handler.doneCheckUpdateFile(file, needsUpdate);

				updateJobCompleted += file.getSize();
				handler.updateCheckUpdatesProgress((float) (updateJobCompleted / updateJobSize));
			}

			handler.doneCheckUpdates();

			if (tempDir != null) {
				updateData = new HashMap<>();
			}

			Signature sig = null;
			if (key != null) {
				sig = Signature.getInstance("SHA256with" + key.getAlgorithm());
				sig.initVerify(key);
			}

			long downloadJobSize = requiresUpdate.stream()
							.mapToLong(FileMetadata::getSize)
							.sum();
			double downloadJobCompleted = 0;

			if (!requiresUpdate.isEmpty()) {
				handler.startDownloads();

				for (FileMetadata file : requiresUpdate) {
					handler.startDownloadFile(file);

					int read = 0;
					double currentCompleted = 0;
					byte[] buffer = new byte[1024];

					Path output;
					if (tempDir == null) {
						Files.createDirectories(file.getPath()
										.getParent());
						output = Files.createTempFile(file.getPath()
										.getParent(), null, null);
					} else {
						Files.createDirectories(tempDir);
						output = Files.createTempFile(tempDir, null, null);
						updateData.put(output.toFile(), file.getPath()
										.toFile());
					}

					URLConnection connection = file.getUri()
									.toURL()
									.openConnection();

					// Some downloads may fail with HTTP/403, this may solve it
					connection.addRequestProperty("User-Agent", "Mozilla/5.0");
					try (InputStream in = connection.getInputStream();
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

						if (sig != null) {
							handler.verifyingFileSignature(file);

							if (file.getSignature() == null)
								throw new SecurityException("Missing signature.");

							if (!sig.verify(file.getSignature()))
								throw new SecurityException("Signature verification failed.");
						}

						if (file.getPath()
										.toString()
										.endsWith(".jar") && !file.isIgnoreBootConflict()) {
							checkConflicts(output);
						}

						if (tempDir == null) {
							Files.move(output, file.getPath(), StandardCopyOption.REPLACE_EXISTING);
						}

						updated.add(file);
						handler.doneDownloadFile(file);

					} finally {
						// clean up if it failed
						if (tempDir == null) {
							Files.deleteIfExists(output);
						}
					}
				}

				if (tempDir != null && updateData.size() > 0) {
					Path updateDataFile = tempDir.resolve(Update.UPDATE_DATA);

					try (ObjectOutputStream out = new ObjectOutputStream(
									Files.newOutputStream(updateDataFile, StandardOpenOption.CREATE))) {
						out.writeObject(updateData);
					}

					FileUtils.windowsHide(updateDataFile);
				}

				handler.doneDownloads();
			}

			handler.succeeded();
			success = true;

		} catch (Throwable t) {
			// clean-up as updateHandler failed
			if (tempDir != null) {
				try {
					Files.deleteIfExists(tempDir.resolve(Update.UPDATE_DATA));
					if (updateData != null) {
						for (File file : updateData.keySet()) {
							Files.deleteIfExists(file.toPath());
						}
					}

					if (Files.isDirectory(tempDir)) {
						try (DirectoryStream<Path> dir = Files.newDirectoryStream(tempDir)) {
							if (!dir.iterator()
											.hasNext()) {
								Files.deleteIfExists(tempDir);
							}
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
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

	private void checkConflicts(Path download) throws IOException {

		Set<Module> modules = ModuleLayer.boot()
						.modules();
		Set<String> moduleNames = modules.stream()
						.map(Module::getName)
						.collect(Collectors.toSet());

		ModuleDescriptor newMod = null;
		Path newPath = download.getParent()
						.resolve(download.getFileName()
										.toString() + ".jar");

		try {
			// ModuleFinder will not cooperate otherwise
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

		if (moduleNames.contains(newMod.name())) {
			Warning.moduleConflict(newMod.name());
			throw new IllegalStateException(
							"Module '" + newMod.name() + "' conflicts with a module in the boot modulepath");
		}

		Set<String> packages = modules.stream()
						.flatMap(m -> m.getPackages()
										.stream())
						.collect(Collectors.toSet());
		for (String p : newMod.packages()) {
			if (packages.contains(p)) {
				Warning.packageConflict(p);
				throw new IllegalStateException("Package '" + p + "' conflicts with a package in the boot modulepath");

			}
		}
	}

	public void launch() {
		launch(null, null);
	}

	public void launch(Consumer<? super Launcher> launcherSetup) {
		launch(null, launcherSetup);
	}

	public void launch(List<String> args) {
		launch(args, null);
	}

	public void launch(List<String> args, Consumer<? super Launcher> launcherSetup) {
		args = args == null ? List.of() : Collections.unmodifiableList(args);

		List<FileMetadata> modules = getFiles().stream()
						.filter(file -> file.getOs() == null || file.getOs() == OS.CURRENT)
						.filter(FileMetadata::isModulepath)
						.collect(Collectors.toList());

		List<Path> modulepaths = modules.stream()
						.map(FileMetadata::getPath)
						.collect(Collectors.toList());

		List<URL> classpaths = getFiles().stream()
						.filter(file -> file.getOs() == null || file.getOs() == OS.CURRENT)
						.filter(FileMetadata::isClasspath)
						.map(FileMetadata::getPath)
						.map(path -> {
							try {
								return path.toUri()
												.toURL();
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
		List<String> moduleNames = finder.findAll()
						.stream()
						.map(ModuleReference::descriptor)
						.map(ModuleDescriptor::name)
						.collect(Collectors.toList());

		ModuleLayer parent = ModuleLayer.boot();
		java.lang.module.Configuration cf = parent.configuration()
						.resolveAndBind(ModuleFinder.of(), finder, moduleNames);

		ClassLoader parentClassLoader = Thread.currentThread()
						.getContextClassLoader();
		ClassLoader classpathLoader = new URLClassLoader("classpath", classpaths.toArray(new URL[classpaths.size()]),
						parentClassLoader);

		ModuleLayer.Controller controller = ModuleLayer.defineModulesWithOneLoader(cf, List.of(parent),
						classpathLoader);
		ModuleLayer layer = controller.layer();

		// manipulate exports, opens and reads
		for (FileMetadata mod : modules) {
			if (!mod.getAddExports()
							.isEmpty()
							|| !mod.getAddOpens()
											.isEmpty()
							|| !mod.getAddReads()
											.isEmpty()) {
				ModuleReference reference = finder.findAll()
								.stream()
								.filter(ref -> new File(ref.location()
												.get()).toPath()
																.equals(mod.getPath()))
								.findFirst()
								.orElseThrow(IllegalStateException::new);

				Module source = layer.findModule(reference.descriptor()
								.name())
								.orElseThrow(IllegalStateException::new);

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

		LaunchContext ctx = new LaunchContext(layer, contextClassLoader, this, args);

		Launcher launcher = Service.loadService(layer, contextClassLoader, Launcher.class, this.launcher);

		if (launcherSetup != null) {
			launcherSetup.accept(launcher);
		}

		Thread t = new Thread(() -> {
			try {
				launcher.run(ctx);
			} catch (NoClassDefFoundError e) {
				if (launcher.getClass()
								.getClassLoader() == ClassLoader.getSystemClassLoader()) {
					Warning.access(launcher);
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

	public static Configuration read(Reader reader) throws IOException {
		ConfigMapper configMapper = ConfigMapper.read(reader);
		return parse(configMapper);
	}

	public static Configuration parse(ConfigMapper mapper) {
		PropertyManager propManager = new PropertyManager(mapper.properties, null);
		return parse(mapper, propManager);
	}

	static Configuration parse(ConfigMapper configMapper, PropertyManager propManager) {
		Configuration config = new Configuration();
		config.propertyManager = propManager;

		if (configMapper.timestamp != null)
			config.timestamp = Instant.parse(configMapper.timestamp);
		else
			config.timestamp = Instant.now();

		if (configMapper.baseUri != null) {
			String uri = config.resolvePlaceholders(configMapper.baseUri, true);
			if (!uri.endsWith("/"))
				uri = uri + "/";

			config.baseUri = URI.create(uri);
		}

		if (configMapper.basePath != null)
			config.basePath = Paths.get(config.resolvePlaceholders(configMapper.basePath, true));

		if (configMapper.updateHandler != null) {
			config.updateHandler = config.resolvePlaceholders(configMapper.updateHandler, false);
			if (!StringUtils.isClassName(config.updateHandler)) {
				throw new IllegalStateException(config.updateHandler + " is not a valid Java class name.");
			}
		}
		if (configMapper.launcher != null) {
			config.launcher = config.resolvePlaceholders(configMapper.launcher, false);
			if (!StringUtils.isClassName(config.launcher)) {
				throw new IllegalStateException(config.launcher + " is not a valid Java class name.");
			}
		}

		List<FileMetadata> files = new ArrayList<>();

		if (configMapper.files != null) {
			for (FileMapper fm : configMapper.files) {
				FileMetadata.Builder fileBuilder = FileMetadata.builder()
								.baseUri(config.getBaseUri())
								.basePath(config.getBasePath());

				if (fm.uri != null) {
					String s = config.resolvePlaceholders(fm.uri, true, fm.os != null && fm.os != OS.CURRENT);

					// Might happen when trying to parse foreign os properties
					if (!PropertyUtils.containsPlaceholder(s)) {
						fileBuilder.uri(URI.create(s));
					}
				}

				if (fm.path != null) {
					String s = config.resolvePlaceholders(fm.path, true, fm.os != null && fm.os != OS.CURRENT);

					if (!PropertyUtils.containsPlaceholder(s)) {
						fileBuilder.path(Paths.get(s));
					}
				}

				if (fm.checksum != null)
					fileBuilder.checksum(fm.checksum);

				if (fm.size != null)
					fileBuilder.size(fm.size);

				if (fm.os != null)
					fileBuilder.os(fm.os);

				// defaults to false
				fileBuilder.modulepath(fm.modulepath != null && fm.modulepath);
				fileBuilder.classpath(fm.classpath != null && fm.classpath);
				fileBuilder.ignoreBootConflict(fm.ignoreBootConflict != null && fm.ignoreBootConflict);

				if (fm.comment != null)
					fileBuilder.comment(config.resolvePlaceholders(fm.comment, false));

				if (fm.signature != null)
					fileBuilder.signature(fm.signature);

				if (fm.addExports != null) {
					fileBuilder.exports(fm.addExports);
				}
				if (fm.addOpens != null) {
					fileBuilder.opens(fm.addOpens);
				}
				if (fm.addReads != null) {
					fileBuilder.reads(fm.addReads);
				}

				files.add(fileBuilder.build());
			}
		}

		config.files = files;
		config.unmodifiableFiles = Collections.unmodifiableList(config.files);

		config.mapper = configMapper;

		return config;
	}

	public ConfigMapper getMapper() {
		return new ConfigMapper(mapper);
	}

	public void write(Writer writer) throws IOException {
		mapper.write(writer);
	}

	public String toString() {
		StringWriter out = new StringWriter();
		try {
			write(out);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return out.toString();
	}

	@Override
	public boolean equals(Object other) {
		if (other == null || !(other instanceof Configuration)) {
			return false;
		}

		Configuration otherConfig = (Configuration) other;
		if (!this.getTimestamp()
						.equals(otherConfig.getTimestamp())) {
			return false;
		}

		return toString().equals(other.toString());
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private String baseUri;
		private String basePath;
		private String updateHandler;
		private String launcher;

		private List<FileMetadata.Reference> files;

		private List<Property> properties;
		private List<String> systemProperties;

		private PrivateKey signer;
		private PlaceholderMatchType matcher = PlaceholderMatchType.WHOLE_WORD;

		private Builder() {
			files = new ArrayList<>();
			properties = new ArrayList<>();
			systemProperties = new ArrayList<>();

			resolveSystemProperty("user.home");
			resolveSystemProperty("user.dir");
		}

		public Builder baseUri(String uri) {
			this.baseUri = uri;

			return this;
		}

		public Builder baseUri(URI uri) {
			return this.baseUri(uri.toString());
		}

		public String getBaseUri() {
			return baseUri;
		}

		public Builder basePath(String path) {
			this.basePath = path;

			return this;
		}

		public Builder basePath(Path path) {
			return basePath(path.toString());
		}

		public String getBasePath() {
			return basePath;
		}

		public Builder signer(PrivateKey key) {
			this.signer = key;

			return this;
		}

		public PrivateKey getSigner() {
			return signer;
		}

		public Builder file(FileMetadata.Reference reference) {
			files.add(reference);

			return this;
		}

		public Builder files(Collection<FileMetadata.Reference> files) {
			files.addAll(files);

			return this;
		}

		public List<FileMetadata.Reference> getFiles() {
			return files;
		}

		public Builder property(String key, String value) {
			return property(key, value, null);
		}

		public Builder property(String key, String value, OS os) {
			value = Objects.requireNonNull(value);

			for (Property p : properties) {
				if (key.equals(p.getKey()) && p.getOs() == os) {
					throw new IllegalArgumentException("Duplicate property: " + key);
				}
			}

			properties.add(new Property(key, value, os));

			return this;
		}

		public Builder property(Property p) {
			properties.add(p);

			return this;
		}

		public Builder properties(Collection<Property> p) {
			properties.addAll(p);

			return this;
		}

		public List<Property> getProperties() {
			return properties;
		}

		public Builder resolveSystemProperty(String str) {
			systemProperties.add(str);

			return this;
		}

		public Builder resolveSystemProperties(Collection<String> p) {
			systemProperties.addAll(p);

			return this;
		}

		public List<String> getSystemPropertiesToResolve() {
			return systemProperties;
		}

		public Builder updateHandler(Class<? extends UpdateHandler> clazz) {
			this.updateHandler = clazz.getCanonicalName();

			return this;
		}

		public Builder updateHandler(String className) {
			this.updateHandler = className;

			return this;
		}

		public String getUpdateHandler() {
			return updateHandler;
		}

		public Builder launcher(Class<? extends Launcher> clazz) {
			this.launcher = clazz.getCanonicalName();

			return this;
		}

		public Builder launcher(String className) {
			this.launcher = className;

			return this;
		}

		public String getLauncher() {
			return launcher;
		}

		public Builder matching(PlaceholderMatchType matcher) {
			this.matcher = matcher;

			return this;
		}

		public PlaceholderMatchType getMatcher() {
			return matcher;
		}

		public Configuration build() {
			ConfigMapper mapper = new ConfigMapper();
			PropertyManager pm = new PropertyManager(properties, systemProperties);

			mapper.timestamp = Instant.now()
							.toString();

			if (baseUri != null)
				mapper.baseUri = pm.implyPlaceholders(baseUri, matcher, true);

			if (basePath != null)
				mapper.basePath = pm.implyPlaceholders(basePath.replace("\\", "/"), matcher, true);

			if (updateHandler != null)
				mapper.updateHandler = pm.implyPlaceholders(updateHandler, matcher, false);

			if (launcher != null)
				mapper.launcher = pm.implyPlaceholders(launcher, matcher, false);

			if (properties.size() > 0)
				mapper.properties = properties;

			if (files.size() > 0) {
				mapper.files = new ArrayList<>();
				for (FileMetadata.Reference fileRef : files) {
					try {
						mapper.files.add(fileRef.getFileMapper(pm, matcher, signer));
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			}

			return Configuration.parse(mapper, pm);

		}

	}
}
