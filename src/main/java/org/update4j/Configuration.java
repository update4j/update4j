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
import java.net.URISyntaxException;
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
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.update4j.binding.BaseBinding;
import org.update4j.binding.ConfigBinding;
import org.update4j.binding.ProviderBinding;
import org.update4j.binding.ReadsBinding;
import org.update4j.binding.LibraryBinding;
import org.update4j.service.Launcher;
import org.update4j.service.Service;
import org.update4j.service.UpdateHandler;
import org.update4j.util.FileUtils;
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

	private List<Library> libraries;
	private List<Library> unmodifiableLibraries;
	private List<Property> properties;
	private List<Property> unmodifiableProperties;

	private Map<String, String> resolvedProperties;
	private Map<String, String> unmodifiableResolvedProperties;

	private ConfigBinding binding;

	private Configuration() {
		timestamp = Instant.now();
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
	 * libraries are resolved. The URI points to the remote (or if it has a
	 * {@code file:///} schema, local) location from where the file should be
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
	 * libraries are resolved. The path points to the location the files should be
	 * saved to on the client's local machine.
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
	 * Returns the list of libraries -- or files -- listed in the configuration
	 * file. This will never return {@code null}.
	 * 
	 * <p>
	 * These are read from the {@code <libraries>} element.
	 * 
	 * @return The {@link Library} instances listed in the configuration file.
	 */
	public List<Library> getLibraries() {
		return unmodifiableLibraries;
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
		return unmodifiableProperties;
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
		return PropertyUtils.getUserProperty(properties, key);
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
		return PropertyUtils.getUserProperties(properties, key);
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
		return PropertyUtils.getUserPropertyForCurrent(properties, key);
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
		return unmodifiableResolvedProperties;
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
		return resolvedProperties.get(key);
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
		return resolvePlaceholders(str, false);
	}

	protected String resolvePlaceholders(String str, boolean isPath) {
		return resolvePlaceholders(str, isPath, false);
	}

	/*
	 * ignoreForeignProperty will not throw an exception if the key is found in an
	 * unresolved foreign property.
	 */
	protected String resolvePlaceholders(String str, boolean isPath, boolean ignoreForeignProperty) {
		return PropertyUtils.resolvePlaceholders(resolvedProperties, properties, str, isPath, ignoreForeignProperty);
	}

	public String implyPlaceholders(String str) {
		return implyPlaceholders(str, false);
	}

	public String implyPlaceholders(String str, boolean isPath) {
		return implyPlaceholders(str, PlaceholderMatchType.WHOLE_WORD, isPath);
	}

	public String implyPlaceholders(String str, PlaceholderMatchType matchType) {
		return implyPlaceholders(str, matchType, false);
	}

	public String implyPlaceholders(String str, PlaceholderMatchType matchType, boolean isPath) {
		return PropertyUtils.implyPlaceholders(resolvedProperties, str, matchType, isPath);
	}

	public boolean requiresUpdate() throws IOException {
		for (Library lib : getLibraries()) {
			if (lib.requiresUpdate())
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
			List<Library> requiresUpdate = new ArrayList<>();
			List<Library> updated = new ArrayList<>();

			UpdateContext ctx = new UpdateContext(this, requiresUpdate, updated, tempDir, key);
			handler.init(ctx);

			handler.startCheckUpdates();
			handler.updateCheckUpdatesProgress(0f);

			List<Library> osLibs = getLibraries().stream()
							.filter(lib -> lib.getOs() == null || lib.getOs() == OS.CURRENT)
							.collect(Collectors.toList());

			long updateJobSize = osLibs.stream().mapToLong(Library::getSize).sum();
			double updateJobCompleted = 0;

			for (Library lib : osLibs) {
				handler.startCheckUpdateLibrary(lib);

				boolean needsUpdate = lib.requiresUpdate();

				if (needsUpdate)
					requiresUpdate.add(lib);

				handler.doneCheckUpdateLibrary(lib, needsUpdate);

				updateJobCompleted += lib.getSize();
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

			long downloadJobSize = requiresUpdate.stream().mapToLong(Library::getSize).sum();
			double downloadJobCompleted = 0;

			if (!requiresUpdate.isEmpty()) {
				handler.startDownloads();

				for (Library lib : requiresUpdate) {
					handler.startDownloadLibrary(lib);

					int read = 0;
					double currentCompleted = 0;
					byte[] buffer = new byte[1024];

					Path output;
					if (tempDir == null) {
						Files.createDirectories(lib.getPath().getParent());
						output = Files.createTempFile(lib.getPath().getParent(), null, null);
					} else {
						Files.createDirectories(tempDir);
						output = Files.createTempFile(tempDir, null, null);
						updateData.put(output.toFile(), lib.getPath().toFile());
					}

					URLConnection connection = lib.getUri().toURL().openConnection();

					// Some downloads may fail with HTTP/403, this may solve it
					connection.addRequestProperty("User-Agent", "Mozilla/5.0");
					try (InputStream in = connection.getInputStream();
									OutputStream out = Files.newOutputStream(output)) {

						// We should set download progress only AFTER the request has returned.
						// The delay can be monitored by the difference between calls from startDownload to this.
						if (downloadJobCompleted == 0) {
							handler.updateDownloadProgress(0f);
						}
						handler.updateDownloadLibraryProgress(lib, 0f);

						while ((read = in.read(buffer, 0, buffer.length)) > -1) {
							out.write(buffer, 0, read);

							if (sig != null) {
								sig.update(buffer, 0, read);
							}

							downloadJobCompleted += read;
							currentCompleted += read;

							handler.updateDownloadLibraryProgress(lib, (float) (currentCompleted / lib.getSize()));
							handler.updateDownloadProgress((float) downloadJobCompleted / downloadJobSize);
						}

						if (sig != null) {
							handler.verifyingLibrary(lib);

							if (lib.getSignature() == null)
								throw new SecurityException("Missing signature.");

							if (!sig.verify(lib.getSignature()))
								throw new SecurityException("Signature verification failed.");
						}

						if (lib.getPath().toString().endsWith(".jar") && !lib.isIgnoreBootConflict()) {
							checkConflicts(output);
						}

						if (tempDir == null) {
							Files.move(output, lib.getPath(), StandardCopyOption.REPLACE_EXISTING);
						}

						updated.add(lib);
						handler.doneDownloadLibrary(lib);

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

			handler.succedded();
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
							if (!dir.iterator().hasNext()) {
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

		Set<Module> modules = ModuleLayer.boot().modules();
		Set<String> moduleNames = modules.stream().map(Module::getName).collect(Collectors.toSet());

		ModuleDescriptor newMod = null;
		Path newPath = download.getParent().resolve(download.getFileName().toString() + ".jar");

		try {
			// ModuleFinder will not cooperate otherwise
			Files.move(download, newPath);
			newMod = ModuleFinder.of(newPath).findAll().stream().map(ModuleReference::descriptor).findAny().orElse(
							null);
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

		Set<String> packages = modules.stream().flatMap(m -> m.getPackages().stream()).collect(Collectors.toSet());
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

		List<Library> modules = getLibraries().stream()
						.filter(lib -> lib.getOs() == null || lib.getOs() == OS.CURRENT)
						.filter(Library::isModulepath)
						.collect(Collectors.toList());

		List<Path> modulepaths = modules.stream().map(Library::getPath).collect(Collectors.toList());

		List<URL> classpaths = getLibraries().stream()
						.filter(lib -> lib.getOs() == null || lib.getOs() == OS.CURRENT)
						.filter(Library::isClasspath)
						.map(Library::getPath)
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
		List<String> moduleNames = finder.findAll()
						.stream()
						.map(ModuleReference::descriptor)
						.map(ModuleDescriptor::name)
						.collect(Collectors.toList());

		ModuleLayer parent = ModuleLayer.boot();
		java.lang.module.Configuration cf = parent.configuration().resolveAndBind(ModuleFinder.of(), finder,
						moduleNames);

		ClassLoader parentClassLoader = Thread.currentThread().getContextClassLoader();
		ClassLoader classpathLoader = new URLClassLoader("classpath", classpaths.toArray(new URL[classpaths.size()]),
						parentClassLoader);

		ModuleLayer.Controller controller = ModuleLayer.defineModulesWithOneLoader(cf, List.of(parent),
						classpathLoader);
		ModuleLayer layer = controller.layer();

		// manipulate exports, opens and reads
		for (Library mod : modules) {
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
					Module target = layer.findModule(read).orElseThrow(() -> new IllegalStateException(
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
				if (launcher.getClass().getClassLoader() == ClassLoader.getSystemClassLoader()) {
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
		ConfigBinding configBinding = ConfigBinding.read(reader);

		Map<String, String> resolved = PropertyUtils.extractPropertiesForCurrentMachine(null, configBinding.properties);
		resolved = PropertyUtils.resolveDependencies(resolved);

		Configuration config = new Configuration();

		config.properties = configBinding.properties == null ? new ArrayList<>() : configBinding.properties;
		config.unmodifiableProperties = Collections.unmodifiableList(config.properties);

		config.resolvedProperties = resolved;
		config.unmodifiableResolvedProperties = Collections.unmodifiableMap(config.resolvedProperties);

		if (configBinding.timestamp != null)
			config.timestamp = Instant.parse(configBinding.timestamp);

		if (configBinding.base != null) {
			if (configBinding.base.uri != null) {
				String uri = config.resolvePlaceholders(configBinding.base.uri, true);
				if (!uri.endsWith("/"))
					uri = uri + "/";

				config.baseUri = URI.create(uri);
			}

			if (configBinding.base.path != null)
				config.basePath = Paths.get(config.resolvePlaceholders(configBinding.base.path, true));
		}

		if (configBinding.provider != null) {
			if (configBinding.provider.updateHandler != null) {
				config.updateHandler = config.resolvePlaceholders(configBinding.provider.updateHandler, false);
				if (!StringUtils.isClassName(config.updateHandler)) {
					throw new IllegalStateException(config.updateHandler + " is not a valid Java class name.");
				}
			}
			if (configBinding.provider.launcher != null) {
				config.launcher = config.resolvePlaceholders(configBinding.provider.launcher, false);
				if (!StringUtils.isClassName(config.launcher)) {
					throw new IllegalStateException(config.launcher + " is not a valid Java class name.");
				}
			}
		}

		List<Library> libraries = new ArrayList<>();

		for (LibraryBinding lib : configBinding.libraries) {
			Library.Builder libBuilder = Library.withBase(config.getBaseUri(), config.getBasePath());

			if (lib.uri != null) {
				String s = config.resolvePlaceholders(lib.uri, true, lib.os != null && lib.os != OS.CURRENT);

				// Might happen when trying to parse foreign os properties
				if (!PropertyUtils.containsPlaceholder(s)) {
					libBuilder.uri(URI.create(s));
				}
			}

			if (lib.path != null) {
				String s = config.resolvePlaceholders(lib.path, true, lib.os != null && lib.os != OS.CURRENT);

				if (!PropertyUtils.containsPlaceholder(s)) {
					libBuilder.path(Paths.get(s));
				}
			}

			if (lib.checksum != null)
				libBuilder.checksum(lib.checksum);

			if (lib.size != null)
				libBuilder.size(lib.size);

			if (lib.os != null)
				libBuilder.os(lib.os);

			// defaults to false
			libBuilder.modulepath(lib.modulepath != null && lib.modulepath);
			libBuilder.classpath(lib.classpath != null && lib.classpath);
			libBuilder.ignoreBootConflict(lib.ignoreBootConflict != null && lib.ignoreBootConflict);

			if (lib.comment != null)
				libBuilder.comment(config.resolvePlaceholders(lib.comment, false));

			if (lib.signature != null)
				libBuilder.signature(lib.signature);

			if (lib.addExports != null) {
				libBuilder.exports(lib.addExports);
			}
			if (lib.addOpens != null) {
				libBuilder.opens(lib.addOpens);
			}
			if (lib.addReads != null) {
				libBuilder.reads(lib.addReads.stream().map(read -> read.module).collect(Collectors.toList()));
			}

			libraries.add(libBuilder.build(true));
		}

		config.libraries = libraries;
		config.unmodifiableLibraries = Collections.unmodifiableList(config.libraries);

		config.binding = configBinding; // used for write

		return config;
	}

	public void write(Writer writer) throws IOException {
		write(writer, PlaceholderMatchType.WHOLE_WORD);
	}

	public void write(Writer writer, PlaceholderMatchType matchType) throws IOException {
		if (binding == null) {
			binding = new ConfigBinding();

			if (getBaseUri() != null) {
				if (binding.base == null) {
					binding.base = new BaseBinding();
				}

				binding.base.uri = implyPlaceholders(getBaseUri().toString(), matchType, true);
			}

			if (getBasePath() != null) {
				if (binding.base == null) {
					binding.base = new BaseBinding();
				}

				binding.base.path = implyPlaceholders(getBasePath().toString().replace("\\", "/"), matchType, true);
			}

			if (getTimestamp() != null)
				binding.timestamp = getTimestamp().toString();

			if (getUpdateHandler() != null) {
				binding.provider = new ProviderBinding();
				binding.provider.updateHandler = implyPlaceholders(getUpdateHandler(), matchType, false);
			}

			if (getLauncher() != null) {
				if (binding.provider == null) {
					binding.provider = new ProviderBinding();
				}

				binding.provider.launcher = implyPlaceholders(getLauncher(), matchType, false);
			}

			if (getUserProperties().size() > 0)
				binding.properties = getUserProperties();

			List<LibraryBinding> libraries = new ArrayList<>();

			if (getLibraries().size() > 0) {

				for (Library lib : getLibraries()) {
					LibraryBinding libBinding = new LibraryBinding();

					URI uri = FileUtils.relativize(getBaseUri(), lib.getUri());
					Path path = FileUtils.relativize(getBasePath(), lib.getPath());

					// just the path part, for comparison with path string
					String uriStr = uri.getPath();
					String pathStr = path.toString().replace("\\", "/");

					// Not absolute and matches, keep only one - preferably the path - but not if
					// the uri contains more info
					if (!uri.isAbsolute() && uriStr.equals(pathStr)) {
						if (uri.getQuery() != null || uri.getFragment() != null) {
							pathStr = null;
						} else {
							uri = null;
						}
					}

					// Absolute uri, and filename is same
					else if (uri.isAbsolute() && !pathStr.contains("/")) {
						if (uri.getPath().endsWith(pathStr)) {
							pathStr = null;
						}
					}

					// Flip from above
					else if (path.isAbsolute() && !uriStr.contains("/")) {
						if (uri.getQuery() == null && uri.getFragment() == null)
							uri = null;
					}

					if (uri != null)
						libBinding.uri = implyPlaceholders(uri.toString(), matchType, true);

					if (pathStr != null)
						libBinding.path = implyPlaceholders(pathStr, matchType, true);

					libBinding.checksum = Long.toHexString(lib.getChecksum());
					libBinding.size = lib.getSize();
					libBinding.os = lib.getOs();
					libBinding.classpath = lib.isClasspath() ? true : null;
					libBinding.modulepath = lib.isModulepath() ? true : null;
					libBinding.ignoreBootConflict = lib.isIgnoreBootConflict() ? true : null;
					libBinding.comment = implyPlaceholders(lib.getComment(), matchType, false);

					if (lib.getSignature() != null)
						libBinding.signature = Base64.getEncoder().encodeToString(lib.getSignature());

					if (!lib.getAddExports().isEmpty())
						libBinding.addExports = lib.getAddExports();
					if (!lib.getAddOpens().isEmpty())
						libBinding.addOpens = lib.getAddOpens();
					if (!lib.getAddReads().isEmpty())
						libBinding.addReads = lib.getAddReads().stream().map(ReadsBinding::new).collect(
										Collectors.toList());

					libraries.add(libBinding);
				}

				binding.libraries = libraries;
			}
		}

		binding.write(writer);
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
		if (!this.getTimestamp().equals(otherConfig.getTimestamp())) {
			return false;
		}

		return toString().equals(other.toString());
	}

	public static Builder absolute() {
		return withBase((String) null, null);
	}

	public static Builder withBase(URI uri) {
		return withBase(uri, null);
	}

	public static Builder withBaseUri(String uri) {
		return withBase(uri, null);
	}

	public static Builder withBase(Path path) {
		return withBase(null, path);
	}

	public static Builder withBasePath(String path) {
		return withBase(null, path);
	}

	public static Builder withBase(URI uri, Path path) {
		return withBase(uri == null ? null : uri.toString(), path == null ? null : path.toString());
	}

	public static Builder withBase(String uri, String path) {
		return new Builder(uri, path);
	}

	public static class Builder {
		private String uri;
		private String path;
		private String updateHandler;
		private String launcher;

		private List<Library.Reference> libraries;

		private List<Property> properties;
		private List<String> systemProperties;

		private PrivateKey signer;

		private Builder(String uri, String path) {
			this.uri = uri;
			this.path = path;

			libraries = new ArrayList<>();
			properties = new ArrayList<>();
			systemProperties = new ArrayList<>();

			resolveSystemProperty("user.home");
			resolveSystemProperty("user.dir");
		}

		public Builder signer(PrivateKey key) {
			this.signer = key;

			return this;
		}

		public Builder library(Library.Reference reference) {
			libraries.add(reference);

			return this;
		}

		public Builder library(Library.Reference.Builder builder) {
			libraries.add(builder.build());

			return this;
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

		public Builder resolveSystemProperty(String str) {
			if (!systemProperties.add(Objects.requireNonNull(str))) {
				throw new IllegalArgumentException("Duplicate system property: " + str);
			}

			return this;
		}

		public Builder updateHandler(Class<? extends UpdateHandler> clazz) {
			this.updateHandler = clazz.getCanonicalName();

			return this;
		}

		public Builder updateHandler(String className) {
			if (StringUtils.isClassName(className)) {
				this.updateHandler = className;

				return this;
			}

			throw new IllegalArgumentException("'" + className + "' is not a valid Java class name");
		}

		public Builder launcher(Class<? extends Launcher> clazz) {
			this.launcher = clazz.getCanonicalName();

			return this;
		}

		public Builder launcher(String className) {
			if (StringUtils.isClassName(className)) {
				this.launcher = className;

				return this;
			}

			throw new IllegalArgumentException("'" + className + "' is not a valid Java class name");
		}

		public Configuration build() throws IOException {

			Configuration config = new Configuration();

			// Resolves for "this" machine only!
			Map<String, String> resolved = PropertyUtils.extractPropertiesForCurrentMachine(systemProperties,
							properties);
			resolved = PropertyUtils.resolveDependencies(resolved);
			config.resolvedProperties = resolved;
			config.unmodifiableResolvedProperties = Collections.unmodifiableMap(config.resolvedProperties);
			config.properties = properties;
			config.unmodifiableProperties = Collections.unmodifiableList(config.properties);

			if (uri != null) {
				uri = config.resolvePlaceholders(uri, true);

				// relativization gets messed up if trailing slash is missing
				if (!uri.endsWith("/"))
					uri = uri + "/";

				config.baseUri = URI.create(uri);
			}
			if (path != null) {
				config.basePath = Paths.get(config.resolvePlaceholders(path, true));
			}

			config.updateHandler = config.resolvePlaceholders(updateHandler);
			config.launcher = config.resolvePlaceholders(launcher);

			List<Library> libs = new ArrayList<>();
			for (Library.Reference ref : libraries) {
				Library file = ref.getLibrary(config, signer);

				for (Library l : libs) {
					if ((l.getOs() == null || l.getOs() == OS.CURRENT)
									&& (file.getOs() == null || file.getOs() == OS.CURRENT)
									&& l.getPath().equals(file.getPath())) {
						throw new IllegalStateException("2 files resolve to same path: " + l.getPath());
					}
				}

				libs.add(file);
			}

			config.libraries = libs;
			config.unmodifiableLibraries = Collections.unmodifiableList(config.libraries);

			return config;

		}
	}
}
