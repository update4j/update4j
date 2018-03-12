package uptodate;

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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.PrivateKey;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.Adler32;

import javax.xml.bind.DataBindingException;
import javax.xml.bind.JAXB;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import uptodate.binding.BaseBinding;
import uptodate.binding.ConfigBinding;
import uptodate.binding.LibraryBinding;
import uptodate.service.LaunchHandler;
import uptodate.service.Service;
import uptodate.service.UpdateHandler;
import uptodate.util.FileUtils;
import uptodate.util.PropertyUtils;
import uptodate.binding.HandlerBinding;

public class Configuration {

	private Instant timestamp;

	private URI baseUri;
	private Path basePath;
	private String updateHandler;
	private String launchHandler;

	private List<Library> libraries;
	private List<Library> unmodifiableLibraries;
	private List<Property> properties;
	private List<Property> unmodifiableProperties;

	private Map<String, String> resolvedProperties;
	private Map<String, String> unmodifiableResolvedProperties;

	private Configuration() {
		timestamp = Instant.now();

		baseUri = URI.create(""); // if null
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

	public String getLaunchHandler() {
		return launchHandler;
	}

	public List<Library> getLibraries() {
		return unmodifiableLibraries;
	}

	public List<Property> getUserProperties() {
		return unmodifiableProperties;
	}

	public Property getUserProperty(String key) {
		return properties.stream().filter(p -> key.equals(p.getKey())).findAny().orElse(null);
	}

	public String getUserPropertyForCurrent(String key) {
		return properties.stream() // First try to locate os specific properties
						.filter(p -> key.equals(p.getKey()) && p.getOs() == OS.CURRENT)
						.map(Property::getValue)
						.findAny()
						.orElseGet(() -> properties.stream()
										.filter(p -> key.equals(p.getKey()) && p.getOs() == null)
										.map(Property::getValue)
										.findAny()
										.orElse(null));
	}

	public Map<String, String> getResolvedProperties() {
		return unmodifiableResolvedProperties;
	}

	public String getResolvedProperty(String key) {
		return resolvedProperties.get(key);
	}

	public String resolvePlaceholders(String str) {
		if (str == null) {
			return null;
		}

		Matcher match = PropertyUtils.PLACEHOLDER.matcher(str);

		while (match.find()) {
			String key = match.group(1);
			String value = getResolvedProperty(key);

			if (value == null) {
				value = PropertyUtils.trySystemProperty(key);
				resolvedProperties.put(key, value);
			}

			str = str.replace(PropertyUtils.wrap(key), value);
		}

		return str;
	}

	public String implyPlaceholders(String str) {
		return implyPlaceholders(str, false);
	}

	public String implyPlaceholders(String str, boolean isPath) {
		return implyPlaceholders(str, ImplicationType.WHOLE_WORD, isPath);
	}

	public String implyPlaceholders(String str, ImplicationType implication) {
		return implyPlaceholders(str, implication, false);
	}

	public String implyPlaceholders(String str, ImplicationType implication, boolean isPath) {
		if (str == null) {
			return null;
		}

		Objects.requireNonNull(implication);

		if (isPath) {
			str = str.replace("\\", "/");
		}

		if (implication == ImplicationType.NONE) {
			return str;
		}

		// Get a list sorted by longest value

		List<Map.Entry<String, String>> resolved = resolvedProperties.entrySet()
						.stream()
						.sorted((e1, e2) -> e2.getValue().length() - e1.getValue().length())
						.peek(e -> {
							if (isPath) {
								e.setValue(e.getValue().replace("\\", "/"));
							}
						})
						.collect(Collectors.toList());

		if (implication == ImplicationType.FULL_MATCH) {
			for (Map.Entry<String, String> e : resolved) {
				if (str.equals(e.getValue())) {
					return PropertyUtils.wrap(e.getKey());
				}
			}

			return str;
		}

		/*
		 * https://stackoverflow.com/a/34464459/1751640
		 * 
		 * This regex will not replace characters inside an existing placeholder.
		 */
		if (implication == ImplicationType.EVERY_OCCURRENCE) {
			for (Map.Entry<String, String> e : resolved) {
				String pattern = "(?<!\\$\\{[^{}]{0,500})" + Pattern.quote(e.getValue());

				str = str.replaceAll(pattern, Matcher.quoteReplacement(PropertyUtils.wrap(e.getKey())));
			}

			return str;
		}

		if (implication == ImplicationType.WHOLE_WORD) {
			for (Map.Entry<String, String> e : resolved) {
				String pattern = "(?<!\\$\\{[^{}]{0,500})\\b" + Pattern.quote(e.getValue()) + "\\b";
				str = str.replaceAll(pattern, Matcher.quoteReplacement(PropertyUtils.wrap(e.getKey())));
			}

			return str;
		}

		throw new UnsupportedOperationException("Unknown implication type");
	}

	public boolean update() {
		return update((Certificate) null);
	}

	public boolean update(Consumer<? super UpdateHandler> handlerSetup) {
		return update(null, handlerSetup);
	}

	public boolean update(Certificate cert) {
		return update(cert, null);
	}

	public boolean update(Certificate cert, Consumer<? super UpdateHandler> handlerSetup) {
		return updateImpl(null, cert, handlerSetup);
	}

	public boolean updateTemp(Path tempDir) {
		return updateTemp(tempDir, (Certificate) null);
	}

	public boolean updateTemp(Path tempDir, Consumer<? super UpdateHandler> handlerSetup) {
		return updateTemp(tempDir, null, handlerSetup);
	}

	public boolean updateTemp(Path tempDir, Certificate cert) {
		return updateTemp(tempDir, cert, null);
	}

	public boolean updateTemp(Path tempDir, Certificate cert, Consumer<? super UpdateHandler> handlerSetup) {
		return updateImpl(Objects.requireNonNull(tempDir), cert, handlerSetup);
	}

	private boolean updateImpl(Path tempDir, Certificate cert, Consumer<? super UpdateHandler> handlerSetup) {
		boolean success;

		UpdateHandler handler = Service.loadService(UpdateHandler.class, updateHandler);

		if (handlerSetup != null) {
			handlerSetup.accept(handler);
		}

		Map<File, File> updateData = null;

		try {
			List<Library> requiresUpdate = new ArrayList<>();
			List<Library> updated = new ArrayList<>();

			UpdateContext ctx = new UpdateContext(this, requiresUpdate, updated, tempDir, cert);
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
			if (cert != null) {
				sig = Signature.getInstance("SHA256with" + cert.getPublicKey().getAlgorithm());
				sig.initVerify(cert);
			}

			long downloadJobSize = requiresUpdate.stream().mapToLong(Library::getSize).sum();
			double downloadJobCompleted = 0;

			if (!requiresUpdate.isEmpty()) {
				handler.startDownloads();
				handler.updateDownloadProgress(0f);

				for (Library lib : requiresUpdate) {
					handler.startDownloadLibrary(lib);
					handler.updateDownloadLibraryProgress(lib, 0f);

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
					Path updateDataFile = tempDir.resolve(UpToDate.UPDATE_DATA);

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
			// clean-up as update failed
			if (tempDir != null) {
				try {
					Files.deleteIfExists(tempDir.resolve(UpToDate.UPDATE_DATA));
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

	public boolean launch() {
		return launch(null, null);
	}

	public boolean launch(Consumer<? super LaunchHandler> handlerSetup) {
		return launch(null, handlerSetup);
	}

	public boolean launch(List<String> args) {
		return launch(args, null);
	}

	public boolean launch(List<String> args, Consumer<? super LaunchHandler> handlerSetup) {
		args = args == null ? List.of() : Collections.unmodifiableList(args);

		LaunchHandler handler = Service.loadService(LaunchHandler.class, launchHandler);

		if (handlerSetup != null) {
			handlerSetup.accept(handler);
		}

		List<Path> paths = getLibraries().stream()
						.filter(lib -> lib.getOs() == null || lib.getOs() == OS.CURRENT)
						.filter(Library::isModulepath)
						.map(Library::getPath)
						.collect(Collectors.toList());

		ModuleFinder finder = ModuleFinder.of(paths.toArray(new Path[paths.size()]));
		List<String> mods = finder.findAll()
						.stream()
						.map(ModuleReference::descriptor)
						.map(ModuleDescriptor::name)
						.collect(Collectors.toList());

		ModuleLayer parent = ModuleLayer.boot();
		java.lang.module.Configuration cf = parent.configuration().resolveAndBind(ModuleFinder.of(), finder, mods);
		ClassLoader scl = ClassLoader.getSystemClassLoader();
		ModuleLayer layer = parent.defineModulesWithOneLoader(cf, scl);

		LaunchContext ctx = new LaunchContext(layer, this, args);

		try {
			handler.start(ctx);
			handler.stop();

			return true;
		} catch (Throwable t) {
			handler.failed(t);
			handler.stop();

			return false;
		}
	}

	public static Configuration read(Reader reader) throws IOException {
		ConfigBinding configBinding = null;
		try {
			configBinding = JAXB.unmarshal(reader, ConfigBinding.class);
		} catch (DataBindingException e) {
			if (e.getCause() instanceof IOException) {
				throw (IOException) e.getCause();
			}

			throw new IOException(e);
		}

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

		if (configBinding.handler != null) {
			if (configBinding.handler.update != null) {
				config.updateHandler = config.resolvePlaceholders(configBinding.handler.update);
				if (!config.updateHandler.matches("([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*")) {
					throw new IllegalStateException(config.updateHandler + " is not a valid java class name.");
				}
			}
			if (configBinding.handler.launch != null) {
				config.launchHandler = config.resolvePlaceholders(configBinding.handler.launch);
				if (!config.launchHandler.matches("([\\p{L}_$][\\p{L}\\p{N}_$]*\\.)*[\\p{L}_$][\\p{L}\\p{N}_$]*")) {
					throw new IllegalStateException(config.launchHandler + " is not a valid java class name.");
				}
			}
		}

		List<Library> libraries = new ArrayList<>();

		for (LibraryBinding lib : configBinding.libraries) {
			Library.Builder libBuilder = Library.withBase(config.getBaseUri(), config.getBasePath());

			if (lib.uri != null)
				libBuilder.uri(URI.create(config.resolvePlaceholders(lib.uri)));

			if (lib.path != null)
				libBuilder.path(Paths.get(config.resolvePlaceholders(lib.path)));

			if (lib.checksum != null)
				libBuilder.checksum(lib.checksum);

			if (lib.size != null)
				libBuilder.size(lib.size);

			if (lib.os != null)
				libBuilder.os(lib.os);

			// defaults to false
			libBuilder.modulepath(lib.modulepath != null && lib.modulepath);

			if (lib.comment != null)
				libBuilder.comment(config.resolvePlaceholders(lib.comment));

			if (lib.signature != null)
				libBuilder.signature(lib.signature);

			libraries.add(libBuilder.build());
		}

		config.libraries = libraries;
		config.unmodifiableLibraries = Collections.unmodifiableList(config.libraries);

		return config;
	}

	public void write(Writer writer) throws IOException {
		write(writer, ImplicationType.WHOLE_WORD);
	}

	public void write(Writer writer, ImplicationType implication) throws IOException {
		ConfigBinding configBinding = new ConfigBinding();

		if (!getBaseUri().equals(URI.create(""))) {
			if (configBinding.base == null) {
				configBinding.base = new BaseBinding();
			}

			configBinding.base.uri = implyPlaceholders(getBaseUri().toString(), implication, true);
		}

		if (!getBasePath().equals(Paths.get(""))) {
			if (configBinding.base == null) {
				configBinding.base = new BaseBinding();
			}

			configBinding.base.path = implyPlaceholders(getBasePath().toString().replace("\\", "/"), implication, true);
		}

		if (getTimestamp() != null)
			configBinding.timestamp = getTimestamp().toString();

		if (getUpdateHandler() != null) {
			configBinding.handler = new HandlerBinding();
			configBinding.handler.update = implyPlaceholders(getUpdateHandler(), implication, false);
		}

		if (getLaunchHandler() != null) {
			if (configBinding.handler == null) {
				configBinding.handler = new HandlerBinding();
			}

			configBinding.handler.launch = implyPlaceholders(getLaunchHandler(), implication, false);
		}

		if (getUserProperties().size() > 0)
			configBinding.properties = getUserProperties();

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
					libBinding.uri = implyPlaceholders(uri.toString(), implication, true);

				if (pathStr != null)
					libBinding.path = implyPlaceholders(pathStr, implication, true);

				libBinding.checksum = Long.toHexString(lib.getChecksum());
				libBinding.size = lib.getSize();
				libBinding.os = lib.getOs();
				libBinding.modulepath = lib.isModulepath() ? true : null;
				libBinding.comment = implyPlaceholders(lib.getComment(), implication, false);

				if (lib.getSignature() != null)
					libBinding.signature = Base64.getEncoder().encodeToString(lib.getSignature());

				libraries.add(libBinding);
			}

			configBinding.libraries = libraries;
		}

		/*
		 * https://stackoverflow.com/a/16959146/1751640
		 */
		writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n\n");
		writer.write("<!-- Generated by UpToDate Project. Licensed under Apache Software License 2.0 -->\n");

		try {
			JAXBContext jc = JAXBContext.newInstance(ConfigBinding.class);

			Marshaller marshaller = jc.createMarshaller();
			marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
			marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
			marshaller.marshal(configBinding, writer);
		} catch (DataBindingException e) {
			if (e.getCause() instanceof IOException) {
				throw (IOException) e.getCause();
			}

			throw new IOException(e);
		} catch (JAXBException e) {
			throw new IOException(e);
		}

		//	JAXB.marshal(configBinding, writer);
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
		private String launchHandler;

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

		public Builder launchHandler(Class<? extends LaunchHandler> clazz) {
			this.launchHandler = clazz.getCanonicalName();

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

			config.baseUri = uri;
			config.basePath = path;
			config.updateHandler = updateHandler;
			config.launchHandler = launchHandler;

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
