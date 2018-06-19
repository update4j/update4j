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

		baseUri = URI.create("");  // if null
		basePath = Paths.get("");
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public URI getBaseUri() {
		return baseUri;
	}

	public Path getBasePath() {
		return basePath;
	}

	public String getUpdateHandler() {
		return updateHandler;
	}

	public String getLauncher() {
		return launcher;
	}

	public List<Library> getLibraries() {
		return unmodifiableLibraries;
	}

	public List<Property> getUserProperties() {
		return unmodifiableProperties;
	}

	public Property getUserProperty(String key) {
		return PropertyUtils.getUserProperty(properties, key);
	}

	public String getUserPropertyForCurrent(String key) {
		return PropertyUtils.getUserPropertyForCurrent(properties, key);
	}

	public Map<String, String> getResolvedProperties() {
		return unmodifiableResolvedProperties;
	}

	public String getResolvedProperty(String key) {
		return resolvedProperties.get(key);
	}

	public String resolvePlaceholders(String str) {
		return resolvePlaceholders(str, false);
	}

	/*
	 * ignoreForeignProperty will not throw an exception if the key is found in an
	 * unresolved foreign property.
	 */
	public String resolvePlaceholders(String str, boolean ignoreForeignProperty) {
		return PropertyUtils.resolvePlaceholders(resolvedProperties, properties, str, ignoreForeignProperty);
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

	/**
	 * @deprecated In favor of {@link #update(PublicKey)}
	 */
	@Deprecated(forRemoval = true)
	public boolean update(Certificate cert) {
		return update(cert.getPublicKey());
	}

	public boolean update(PublicKey key, Consumer<? super UpdateHandler> handlerSetup) {
		return updateImpl(null, key, handlerSetup);
	}

	/**
	 * @deprecated In favor of {@link #update(PublicKey, Consumer)}
	 */
	@Deprecated(forRemoval = true)
	public boolean update(Certificate cert, Consumer<? super UpdateHandler> handlerSetup) {
		return update(cert.getPublicKey(), handlerSetup);
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

	/**
	 * @deprecated In favor of {@link #updateTemp(Path, PublicKey)}
	 */
	@Deprecated(forRemoval = true)
	public boolean updateTemp(Path tempDir, Certificate cert) {
		return updateTemp(tempDir, cert.getPublicKey());
	}

	public boolean updateTemp(Path tempDir, PublicKey key, Consumer<? super UpdateHandler> handlerSetup) {
		return updateImpl(Objects.requireNonNull(tempDir), key, handlerSetup);
	}

	/**
	 * @deprecated In favor of {@link #updateTemp(Path, PublicKey, Consumer)}
	 */
	@Deprecated(forRemoval = true)
	public boolean updateTemp(Path tempDir, Certificate cert, Consumer<? super UpdateHandler> handlerSetup) {
		return updateTemp(Objects.requireNonNull(tempDir), cert.getPublicKey(), handlerSetup);
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

			handler.failed(t);
			success = false;
		}

		handler.stop();

		return success;

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
		if(modulepaths.isEmpty() && classpaths.isEmpty()) {
			if(!"true".equals(System.getProperty("suppress.warning.path"))) {
				System.err.println("WARNING: No libraries were found that are set with 'classpath' or 'modulepath' to true; although perfectly valid it's rarely what you want."
								+ "\nPlease refer to: https://github.com/update4j/update4j/wiki/Documentation#classpath-and-modulepath");
			}
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

		ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
		ClassLoader classpathLoader = new URLClassLoader("classpath", classpaths.toArray(new URL[classpaths.size()]),
						systemClassLoader);

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

		Thread t = new Thread(() -> launcher.run(ctx));
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
				String uri = config.resolvePlaceholders(configBinding.base.uri);
				if (!uri.endsWith("/"))
					uri = uri + "/";

				config.baseUri = URI.create(uri);
			}

			if (configBinding.base.path != null)
				config.basePath = Paths.get(config.resolvePlaceholders(configBinding.base.path));
		}

		if (configBinding.provider != null) {
			if (configBinding.provider.updateHandler != null) {
				config.updateHandler = config.resolvePlaceholders(configBinding.provider.updateHandler);
				if (!StringUtils.isClassName(config.updateHandler)) {
					throw new IllegalStateException(config.updateHandler + " is not a valid Java class name.");
				}
			}
			if (configBinding.provider.launcher != null) {
				config.launcher = config.resolvePlaceholders(configBinding.provider.launcher);
				if (!StringUtils.isClassName(config.launcher)) {
					throw new IllegalStateException(config.launcher + " is not a valid Java class name.");
				}
			}
		}

		List<Library> libraries = new ArrayList<>();

		for (LibraryBinding lib : configBinding.libraries) {
			Library.Builder libBuilder = Library.withBase(config.getBaseUri(), config.getBasePath());

			if (lib.uri != null) {
				String s = config.resolvePlaceholders(lib.uri, lib.os != null && lib.os != OS.CURRENT);

				// Might happen when trying to parse foreign os properties
				if (!PropertyUtils.containsPlaceholder(s)) {
					libBuilder.uri(URI.create(s));
				}
			}

			if (lib.path != null) {
				String s = config.resolvePlaceholders(lib.path, lib.os != null && lib.os != OS.CURRENT);

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

			if (lib.comment != null)
				libBuilder.comment(config.resolvePlaceholders(lib.comment));

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

			if (!getBaseUri().equals(URI.create(""))) {
				if (binding.base == null) {
					binding.base = new BaseBinding();
				}

				binding.base.uri = implyPlaceholders(getBaseUri().toString(), matchType, true);
			}

			if (!getBasePath().equals(Paths.get(""))) {
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

					URI uri = getBaseUri().relativize(lib.getUri());
					Path path = getBasePath().relativize(lib.getPath());

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
		return withBase(null, null);
	}

	public static Builder withBase(URI uri) {
		return withBase(uri, null);
	}

	public static Builder withBase(Path path) {
		return withBase(null, path);
	}

	public static Builder withBase(URI uri, Path path) {
		return new Builder(uri, path);
	}

	public static class Builder {
		private URI uri;
		private Path path;
		private String updateHandler;
		private String launcher;

		private List<Library.Reference> libraries;

		private List<Property> properties;
		private List<String> systemProperties;

		private PrivateKey signer;

		private Builder(URI uri, Path path) {
			if (uri != null) {
				if (!uri.getPath() // relativization gets messed up if trailing slash is missing
								.endsWith("/")) {
					try {
						uri = new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(),
										uri.getPath() + "/", uri.getQuery(), uri.getFragment());
					} catch (URISyntaxException e) {
						throw new AssertionError(e);
					}
				}
			}

			this.uri = uri;
			this.path = path;

			libraries = new ArrayList<>();
			properties = new ArrayList<>();
			systemProperties = new ArrayList<>();
		}

		public Builder signer(PrivateKey key) {
			this.signer = key;

			return this;
		}

		public Builder library(Library.Reference reference) {
			libraries.add(reference);

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

		public Builder launcher(Class<? extends Launcher> clazz) {
			this.launcher = clazz.getCanonicalName();

			return this;
		}

		public Configuration build() throws IOException {
			// should check if 2 files resolve to same location;

			List<Library> libs = new ArrayList<>();
			for (Library.Reference ref : libraries) {
				Library file = ref.getLibrary(uri, path, signer);

				for (Library l : libs) {
					if (Files.isSameFile(l.getPath(), file.getPath())) {
						throw new IllegalStateException("2 files resolve to same path: " + l.getPath());
					}
				}

				libs.add(file);
			}

			Configuration config = new Configuration();

			if (uri != null)
				config.baseUri = uri;
			if (path != null)
				config.basePath = path;
			
			config.updateHandler = updateHandler;
			config.launcher = launcher;

			config.libraries = libs;
			config.unmodifiableLibraries = Collections.unmodifiableList(config.libraries);
			config.properties = properties;
			config.unmodifiableProperties = Collections.unmodifiableList(config.properties);

			// Resolves for "this" machine only!

			Map<String, String> resolved = PropertyUtils.extractPropertiesForCurrentMachine(systemProperties,
							properties);
			resolved = PropertyUtils.resolveDependencies(resolved);

			config.resolvedProperties = resolved;
			config.unmodifiableResolvedProperties = Collections.unmodifiableMap(config.resolvedProperties);

			return config;

		}
	}
}
