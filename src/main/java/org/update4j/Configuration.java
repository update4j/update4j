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
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.update4j.mapper.ConfigMapper;
import org.update4j.mapper.FileMapper;
import org.update4j.service.Launcher;
import org.update4j.service.Service;
import org.update4j.service.UpdateHandler;
import org.update4j.util.*;

/**
 * This class is the heart of the framework. It contains all the logic required
 * to draft new releases at the development side, or start the application at
 * the client side.
 * 
 * <p>
 * Although everything is contained in a single class; some methods are intended
 * to run either on the dev machine -- to draft new releases -- or client
 * machine, not both. The documentation of each method will point that out.
 * 
 * <p>
 * A configuration is linked to an XML file, and this class provide methods to
 * read, write, generate and sync configurations. Once a configuration has been
 * created, it is immutable and cannot be modified. There are methods to
 * manipulate the XML elements and create new configurations from them, but the
 * original remains untouched.
 * 
 * <h2>Terminology</h2>
 * <p>
 * <ul>
 * <li><b>Config</b> &mdash; For the purpose of brevity we will refer to a
 * configuration as a 'config'.</li>
 * <li><b>Bootstrap Application</b> &mdash; The JVM startup application that
 * solely does the update and launch logic or anything at that level apart from
 * the Business Application.</li>
 * <li><b>Business Application</b> &mdash; The application you actually want to
 * make updatable.</li>
 * <li><b>Boot Classpath/Modulepath</b> &mdash; The JVM native
 * classpath/modulepath where the bootstrap is usually loaded.</li>
 * <li><b>Dynamic Classpath/Modulepath</b> &mdash; The dynamically loaded
 * classpath/modulepath where the business application is loaded.</li>
 * <li><b>URI</b> &mdash; The remote (or in case of {@code file:///} -- local)
 * location <em>from where</em> the file is downloaded.</li>
 * <li><b>Path</b> &mdash; The local location <em>to where</em> the file should
 * be downloaded on the client machine.</li>
 * <li><b>Service</b> &mdash; Any of the service interfaces that can be provided
 * by developers and easily updatable by releasing newer providers with a higher
 * version number.</li>
 * <li><b>Property</b> &mdash; Any of the key-value pairs listed in the config
 * file. Can be used as placeholders in URI's, paths, class names, file comments
 * or in values of other properties.</li>
 * </ul>
 * 
 * <h1>Developer Side</h1>
 * <p>
 * You should primarily use this class whenever you want to draft a new release
 * to generate new configs. This might be done directly in code or be called by
 * build tools.
 * 
 * <p>
 * There are 3 ways generate configs.
 * 
 * <h2>1. Using the Builder API</h2>
 * <p>
 * {@link Configuration#builder()} is the entry point to the config builder API.
 * 
 * <p>
 * Here's a sample config created with this approach:
 * 
 * <pre>{@code
 * Configuration config = Configuration.builder()
 * 				// resolve uri and path of each individual file against the base.
 * 				// if not present you must provider the absolute location to every individual file
 * 				// with the uri() and path() method
 * 				.baseUri("http://example.com/")
 * 
 * 				// reads actual value from client system property "user.home"
 * 				.basePath("${user.home}/myapp/")
 * 
 * 				// list all files from the given directory
 * 				.files(FileMetadata.streamDirectory("build/mylibs")
 * 								// mark all jar files for classpath
 * 								.peek(r -> r.classpath(r.getSource().toString().endsWith(".jar"))))
 * 
 * 				.file(FileMetadata.readFrom("otherDirectory/my-logo.png")
 * 
 * 								//override http://example.com above
 * 								.uri("https://s3.aws.com/some-location/img.png")
 * 
 * 								// resolves base from basePath but
 * 								// overrides my-logo.png from source
 * 								.path("application-logo.png"))
 * 
 * 				// we're done!
 * 				.build();
 * }</pre>
 * 
 * 
 * <h2>2. Synchronizing Existing Configuration</h2>
 * <p>
 * If you already have a configuration but you changed files without adding or
 * removing files, you might synchronize the file size, checksum and signature
 * (if you use signature validation) without using the builder API, via
 * {@link Configuration#sync()}. A new config will be returned, the existing
 * config remains untouched:
 * 
 * <p>
 * Given this XML {@code config.xml}:
 * 
 * <pre>{@code
 * <xmp>
 * <configuration timestamp="2018-08-22T19:31:40.448450500Z">
 *     <base uri="https://example.com/" path="${user.loc}"/>
 *     <properties>
 *         <property key="user.loc" value="${user.home}/Desktop/"/>
 *     </properties>
 *     <files>
 *         <file path="file1.jar" size="1348" checksum="fd7adfb7"/>
 *     </files>
 * </configuration>
 * </xmp>
 * }</pre>
 * 
 * <p>
 * 
 * You can synchronize it as:
 * 
 * <pre>
 * Configuration config = Configuration.read(Files.newBufferedReader(Paths.get("config.xml")));
 * 
 * // read files from actual locations listed in config
 * // in our case ${user.loc}/Desktop/file1.jar
 * Configuration newConfig = config.sync();
 * 
 * // read files from different base path but same individual file name
 * // in our case ./build/file1.jar where "." is current directory
 * Configuration newConfig = config.sync(Paths.get("build"));
 * 
 * // read files from actual locations listed in config
 * // and sign with given PrivateKey
 * KeyStore ks = KeyStore.getInstance("JKS");
 * ks.load(Files.newInputStream(keystorePath), "Password1".toCharArray());
 * 
 * PrivateKey pk = (PrivateKey) ks.getKey("alias", "Password2".toCharArray());
 * Configuration newConfig = config.sync(pk);
 * </pre>
 * 
 * <p>
 * If you want to add a new file you should manually add the filename in the
 * config XML and {@code sync()} will do the rest:
 * 
 * <pre>{@code
 * <xmp>
 * <files>
 *         <file path="file1.jar" size="1348" checksum="fd7adfb7"/>
 *         
 *         <!-- The new file -->
 *         <file path="file2.jar" />
 * </files>
 * </xmp>
 * }</pre>
 * 
 * 
 * <h2>3. Manual XML Manipulation</h2>
 * <p>
 * You can access the XML DOM with the {@link ConfigMapper} class and load a
 * config using {@link Configuration#parse(ConfigMapper)} to obtain a new
 * configuration.
 * 
 * <pre>
 * ConfigMapper mapper = new ConfigMapper();
 * mapper.baseUri = "https://example.com/"
 * 
 * FileMapper file = new FileMapper();
 * file.path = "/root/home/file.jar";
 * file.size = 3082;
 * file.checksum = "ac29bfa0";
 * mapper.files.add(file);
 * 
 * Configuration config = Configuration.parse(mapper);
 * </pre>
 * 
 * <p>
 * Or access the underlying mapper of an existing config (it creates a defensive
 * copy, so cache them once):
 * 
 * <pre>
 * ConfigMapper mapper = config.generateXmlMapper();
 * mapper.files.remove(0);
 * 
 * Configuration newConfig = Configuration.parse(mapper);
 * </pre>
 * 
 * <h2>Writing the Configuration</h2>
 * <p>
 * Once you successfully created a config, write them with
 * {@link Configuration#write(Writer)}:
 * 
 * <pre>
 * try (Writer out = Files.newBufferedWriter(location)) {
 * 	config.write(location);
 * }
 * 
 * // or get a String
 * String theXml = config.toString();
 * system.out.println(theXml);
 * </pre>
 * 
 * <h1>Client Side</h1>
 * <p>
 * A config in the client serves two purposes: 1. Know when a file is outdated
 * and requires an update when {@code update()} is called 2. List of files to be
 * dynamically loaded onto the running JVM when {@code launch()} is called.
 * 
 * 
 * <p>
 * All logic on the client side is generally done in the bootstrap application,
 * like reading the config (and usually caching it somewhere locally in case
 * there's no Internet connection next time), updating and launching or whatever
 * needs to be done before launch or after business app shutdown or anywhere in
 * between, according to your needs.
 * 
 * <p>
 * In order to use the config you must first read it from a file or remote
 * location.
 * 
 * <h2>Reading a Configuration</h2>
 * <p>
 * Read a config using the {@link Configuration#read(Reader)}:
 * 
 * <pre>
 * Configuration config = null;
 * 
 * try (InputStream in = new URL("https://example.com/config.xml").openStream()) {
 * 	config = Configuration.read(new InputStreamReader(in));
 * }
 * </pre>
 * 
 * <h2>Updating</h2>
 * <p>
 * Updating is done by calling any of the {@code update()} methods. You can
 * update without signature validation, or with, by using the {@link PublicKey}
 * overloads.
 * 
 * <p>
 * You can update before launching so the subsequent launch always has the
 * newest version, or you can update afterwards and only get the new version on
 * next restart. In the latter case you cannot update existing files, since the
 * JVM locks them upon launch; you can call any of the {@code updateTemp()}
 * overloads and complete the launch on next restart via
 * {@link Update#finalizeUpdate(Path)}.
 * 
 * <p>
 * When update is called without explicitly passing an {@link UpdateHandler},
 * the framework will try to locate one between the registered service providers
 * and will use the one with the highest {@code version()} number. If
 * {@link Configuration#getUpdateHandler()} returns a class name, it will ignore
 * the versioning and use that one, <em>it is completely optional to list the
 * update handler in the config</em>.
 * 
 * <p>
 * For more info how to register providers please refer to the <a
 * href=https://github.com/update4j/update4j/wiki/Documentation#dealing-with-providers>Github
 * Wiki</a>.
 * 
 * <p>
 * Regular updating (not {@code updateTemp()}):
 * 
 * <pre>{@code
 * // loads a registered provider or DefaultUpdateHandler if non are found.
 * config.update(); // returns a boolean if succeeded
 * 
 * // updates with given update handler
 * config.update(new MyUpdateHandler());
 * 
 * // update with registered handler and fish out the instance *before* the
 * // update process starts
 * config.update(handler -> processHandler(handler));
 * // or just
 * config.update(this::processHandler);
 * 
 * // update and validate against the public key
 * config.update(myPubKey);
 * }</pre>
 * 
 * <p>
 * Or you can update to a temporary location and finalize on next restart.
 * Here's a sample lifecycle:
 * 
 * <pre>{@code
 * public static void main(String[] args) throws IOException {
 * 	// the temporary location
 * 	Path temp = Paths.get("update");
 * 
 * 	// first check if last run made a temp update
 * 	if (Update.containsUpdate(temp)) {
 * 		Update.finalizeUpdate(temp);
 * 	}
 * 
 * 	// some random method
 * 	Configuration config = getConfig();
 * 	// we don't want to hang, so we can update immediately
 * 	new Thread(() -> config.launch());
 * 
 * 	// and *after* launch do the update
 * 	if (config.requiresUpdate()) {
 * 		config.updateTemp(temp);
 * 	}
 * }
 * }</pre>
 * 
 * <h3>Consistency</h3>
 * <p>
 * If even a single file failed to download or if any other exception arises,
 * all downloads before the exception is rolled back to its original state as
 * before the call for update.
 * 
 * <h3>Boot Modulepath Conflicts</h3>
 * <p>
 * Every jar file gets checked if it were a valid file in boot modulepath; as if
 * it's a valid zip file, duplicate module name, split package, valid automatic
 * module name etc., no matter if the file was actually intended to be present
 * in the boot modulepath. This was put in place to prevent accidentally making
 * the file visible to the boot modulepath and completely breaking the
 * application, as the JVM would resist to startup, thus not allowing this to be
 * fixed remotely.
 * 
 * <p>
 * If great care was taken that the given file will not be visible to the boot
 * modulepath (i.e. only to the dynamic path or boot <em>classpath</em>), it is
 * legal and you may override the check by marking
 * {@code ignoreBootConflict="true"} in the config file or via the corresponding
 * builder method.
 * 
 * <h3>Signature</h3>
 * <p>
 * Optionally, to secure your clients in the event of server compromise, you can
 * sign the files via the {@code signer()} method in the Builder API, or
 * {@link Configuration#sync(PrivateKey)}. If you used the {@link PublicKey}
 * overload of {@code update()} or {@code updateTemp()} it will verify all files
 * and reject the download if they fail.
 * 
 * <h1>Launching</h1>
 * <p>
 * Launching loads files onto the dynamic classpath or modulepath (or does not
 * load it if not marked with either), depending on their configuration and
 * launches it by using either a passed {@link Launcher} or locate one by
 * looking at registered providers.
 * 
 * <p>
 * When launch is called without explicitly passing a {@link Launcher}, the
 * framework will try to locate one between the registered service providers and
 * will use the one with the highest {@code version()} number. If
 * {@link Configuration#getLauncher()} returns a class name, it will ignore the
 * versioning and use that one, <em>it is completely optional to list the
 * launcher in the config</em>.
 * 
 * <p>
 * If a launcher instance was used and not loaded via the service providing
 * mechanism, it only has reflective access to the Business Application by
 * reflecting against {@link LaunchContext#getClassLoader()}.
 * 
 * 
 * <pre>
 * // launch with registered launcher or DefaultLauncher if non were found
 * config.launch();
 * 
 * // launch with passed launcher, *only reflective access*
 * config.launch(new MyLauncher());
 * </pre>
 * 
 * <p>
 * A call to launch will launch the application in a new thread (to give it
 * seperate context), but it will still not return until
 * {@link Launcher#run(LaunchContext)} returned (not counting new threads in the
 * business app).
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

	private List<FileMetadata> unmodifiableFiles;
	private PropertyManager propertyManager;

	private ConfigMapper mapper;

    static{
        PathUtils.init(); //init, so that variables in configuration are replaced
    }

	private Configuration() {
	}

	/**
	 * Returns the timestamp this configuration was last updated using the
	 * {@link Configuration.Builder} API or {@code sync()}. This is read from the
	 * {@code timestamp} attribute in the root element. If the attribute is missing
	 * it will return the time this instance was created.
	 * 
	 * <p>
	 * It does not have any effect on the behavior of anything else; it is rather
	 * just for reference purposes (i.e. "Last Updated: 2 Weeks Ago"), or for
	 * clients willing to act according to this value.
	 * 
	 * 
	 * @return The timestamp this configuration was last updated, or when currently
	 *         loaded, if missing from XML.
	 */
	public Instant getTimestamp() {
		return timestamp;
	}

	/**
	 * Returns the base URI against whom all <em>relative</em> URIs in individual
	 * files are resolved. The URI points to the remote (or if it has a
	 * {@code file:///} scheme, local) location from where the file should be
	 * downloaded.
	 * 
	 * 
	 * <p>
	 * This is read from the {@code uri} attribute from the {@code <base>} element.
	 * If the attribute is missing this will return {@code null}.
	 * 
	 * @return The base URI, or {@code null} if missing.
	 */
	public URI getBaseUri() {
		return baseUri;
	}

	/**
	 * Returns the base path against whom all <em>relative</em> paths in individual
	 * files are resolved. The path points to the location the files should be saved
	 * to on the client's local machine.
	 * 
	 * <p>
	 * This is read from the {@code path} attribute from the {@code <base>} element.
	 * If the attribute is missing this will return {@code null}.
	 * 
	 * @return The base path, or {@code null} if missing.
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
	 *         of the default highest version, or {@code null} if missing.
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
	 *         default highest version, or {@code null} if missing.
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
	 * @return The {@link FileMetadata} instances listed in the configuration file,
	 *         never {@code null}.
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
	 * @return The {@link Property} instances listed in the configuration file,
	 *         never {@code null}.
	 */
	public List<Property> getUserProperties() {
		return propertyManager.getUserProperties();
	}

	/**
	 * Returns the {@link Property} with the corresponding key, or {@code null} if
	 * missing. If there are more than one property with the given key (if they are
	 * platform specific), only the one corresponding to this system will be
	 * returned.
	 * 
	 * @param key
	 *            The key of the property.
	 * @return The {@link Property} with the given key, or {@code null} if missing.
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
	 * @return A list of properties with the given key, never {@code null}
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
	public String getUserPropertyForCurrentOs(String key) {
		return propertyManager.getUserPropertyForCurrentOs(key);
	}

	/**
	 * Returns an unmodifiable map of keys and values with their <em>real</em>
	 * values after resolving the placeholders. This will not include properties
	 * marked for foreign operating systems. This will also include system
	 * properties that were referenced anywhere in the XML or after a call to
	 * {@link Configuration#resolvePlaceholders(String)} referred to a system
	 * property.
	 * 
	 * <p>
	 * 
	 * @apiNote Although everything in this class is immutable, this is the only
	 *          thing that can change after construction by calling
	 *          {@link Configuration#resolvePlaceholders(String)} and the passed
	 *          string has a reference to a system property.
	 * 
	 * @return A map of the keys and real values of the properties, after resolving
	 *         the placeholders, never {@code null}.
	 */
	public Map<String, String> getResolvedProperties() {
		return propertyManager.getResolvedProperties();
	}

	/**
	 * Returns the <em>real</em> value of the property with the given key, after
	 * resolving the placeholders. This is usually the method you will call to read
	 * some property from a configuration.
	 * 
	 * @param key
	 *            The key of the property.
	 * @return The real value of the property after resolving the placeholders, or
	 *         {@code null} if missing.
	 */
	public String getResolvedProperty(String key) {
		return propertyManager.getResolvedProperty(key);
	}

	/**
	 * Returns a string where all placeholders are replaced with the real values. If
	 * the given string is {@code null}, the same value will be returned.
	 * 
	 * <p>
	 * If it includes a reference to a placeholder that could not be resolved, it
	 * will fail.
	 * 
	 * @param str
	 *            The source string to try to resolve.
	 * @return The resolved string, or {@code null} if {@code null} was passed.
	 * @throws IllegalArgumentException
	 *             if the source string contains a placeholder that could not be
	 *             resolved.
	 */
	public String resolvePlaceholders(String str) {
		return propertyManager.resolvePlaceholders(str);
	}

	private String resolvePlaceholders(String str, boolean isPath) {
		return propertyManager.resolvePlaceholders(str, isPath);
	}

	/*
	 * ignoreForeignProperty will not throw an exception if the key is found in an
	 * unresolved foreign property.
	 */
	private String resolvePlaceholders(String str, boolean isPath, boolean ignoreForeignProperty) {
		return propertyManager.resolvePlaceholders(str, isPath, ignoreForeignProperty);
	}

	/**
	 * Returns a string with real values replaced with placeholders. This method
	 * will never break up words, it acts exactly as:
	 * 
	 * <pre>
	 * implyPlaceholders(str, PlaceholderMatchType.WHOLE_WORD);
	 * </pre>
	 * 
	 * <p>
	 * If the given string is {@code null}, the same value will be returned.
	 * 
	 * @param str
	 *            The string to attempt to replace with placeholders.
	 * @return The replaced string, or {@code null} if {@code null} was passed.
	 */
	public String implyPlaceholders(String str) {
		return propertyManager.implyPlaceholders(str);
	}

	/**
	 * Returns a string with real values replaced with placeholders. This method
	 * will never break up words, it acts exactly as:
	 * 
	 * <pre>
	 * implyPlaceholders(str, isPath, PlaceholderMatchType.WHOLE_WORD);
	 * </pre>
	 * 
	 * <p>
	 * This overload allows you to specify whether the given string is a
	 * hierarchical string in a path manner. If {@code true}, then {@code "\\"} and
	 * {@code "/"} are treated identical. This is intended to match more strings in
	 * a platform independent way.
	 * 
	 * <pre>{@code
	 * String old = "C:/Users/User/Desktop";
	 * String newString = config.implyPlaceholders(old, true);
	 * 
	 * // -> newString is "${user.home}/Desktop" even though the real system
	 * // property value is "C:\\Users\\User" on Windows
	 * }</pre>
	 * 
	 * <p>
	 * If the given string is {@code null}, the same value will be returned.
	 * 
	 * @param str
	 *            The string to attempt to replace with placeholders.
	 * @param isPath
	 *            Whether the given string is a path like string.
	 * @return The replaced string, or {@code null} if {@code null} was passed.
	 */
	public String implyPlaceholders(String str, boolean isPath) {
		return propertyManager.implyPlaceholders(str, isPath);
	}

	/**
	 * Returns a string with real values replaced with placeholders.
	 * 
	 * <p>
	 * You can specify how matches should be found by passing the
	 * {@link PlaceholderMatchType}.
	 * <ul>
	 * <li>{@code EVERY_OCCURENCE} &mdash; Will break words with placeholders if it
	 * finds a match.</li>
	 * <li>{@code WHOLE_WORD} &mdash; Will only replace with placeholders if the it
	 * doesn't break a word (using rexeg {@code \b} word boundary).</li>
	 * <li>{@code FULL_MATCH} &mdash; Will only replace if the complete string
	 * matches with one placeholder.</li>
	 * </ul>
	 * 
	 * <p>
	 * If the given string is {@code null}, the same value will be returned.
	 * 
	 * @param str
	 *            The string to attempt to replace with placeholders.
	 * @return The replaced string, or {@code null} if {@code null} was passed.
	 */
	public String implyPlaceholders(String str, PlaceholderMatchType matchType) {
		return propertyManager.implyPlaceholders(str, matchType);
	}

	/**
	 * Returns a string with real values replaced with placeholders.
	 * <p>
	 * This overload allows you to specify whether the given string is a
	 * hierarchical string in a path manner. If {@code true}, then {@code "\\"} and
	 * {@code "/"} are treated identical. This is intended to match more strings in
	 * a platform independent way.
	 * 
	 * <pre>{@code
	 * String old = "C:/Users/User/Desktop";
	 * String newString = config.implyPlaceholders(old, true);
	 * 
	 * // -> newString is "${user.home}/Desktop" even though the real system
	 * // property value is "C:\\Users\\User" on Windows
	 * }</pre>
	 * 
	 * <p>
	 * You can specify how matches should be found by passing the
	 * {@link PlaceholderMatchType}.
	 * <ul>
	 * <li>{@code EVERY_OCCURENCE} &mdash; Will break words with placeholders if it
	 * finds a match.</li>
	 * <li>{@code WHOLE_WORD} &mdash; Will only replace with placeholders if the it
	 * doesn't break a word (using rexeg {@code \b} word boundary).</li>
	 * <li>{@code FULL_MATCH} &mdash; Will only replace if the complete string
	 * matches with one placeholder.</li>
	 * </ul>
	 * 
	 * <p>
	 * If the given string is {@code null}, the same value will be returned.
	 * 
	 * @param str
	 *            The string to attempt to replace with placeholders.
	 * @param isPath
	 *            Whether the given string is a path like string.
	 * @return The replaced string, or {@code null} if {@code null} was passed.
	 */
	public String implyPlaceholders(String str, PlaceholderMatchType matchType, boolean isPath) {
		return propertyManager.implyPlaceholders(str, matchType, isPath);
	}

	/**
	 * Checks the metadata of every file and returns {@code true} if at-least one
	 * file requires an update, and {@code false} if no file requires an update.
	 * 
	 * @return If at-least one file requires an update.
	 * @throws IOException
	 *             If any {@code IOException} arises will reading the files.
	 */
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

	public boolean update(UpdateHandler handler) {
		return update((PublicKey) null, handler);
	}

	public boolean update(Consumer<? super UpdateHandler> handlerSetup) {
		return update((PublicKey) null, handlerSetup);
	}

	public boolean update(PublicKey key) {
		return update(key, (UpdateHandler) null);
	}

	public boolean update(PublicKey key, UpdateHandler handler) {
		return updateImpl(null, key, handler, null);
	}

	public boolean update(PublicKey key, Consumer<? super UpdateHandler> handlerSetup) {
		return updateImpl(null, key, null, handlerSetup);
	}

	public boolean updateTemp(Path tempDir) {
		return updateTemp(tempDir, (PublicKey) null);
	}

	public boolean updateTemp(Path tempDir, UpdateHandler handler) {
		return updateTemp(tempDir, (PublicKey) null, handler);
	}

	public boolean updateTemp(Path tempDir, Consumer<? super UpdateHandler> handlerSetup) {
		return updateTemp(tempDir, (PublicKey) null, handlerSetup);
	}

	public boolean updateTemp(Path tempDir, PublicKey key) {
		return updateTemp(tempDir, key, (UpdateHandler) null);
	}

	public boolean updateTemp(Path tempDir, PublicKey key, UpdateHandler handler) {
		return updateImpl(Objects.requireNonNull(tempDir), key, handler, null);
	}

	public boolean updateTemp(Path tempDir, PublicKey key, Consumer<? super UpdateHandler> handlerSetup) {
		return updateImpl(Objects.requireNonNull(tempDir), key, null, handlerSetup);
	}

	private boolean updateImpl(Path tempDir, PublicKey key, UpdateHandler handler,
					Consumer<? super UpdateHandler> handlerSetup) {
		boolean updateTemp = tempDir != null;
		boolean success;

		// if no explicit handler were passed
		if (handler == null) {
			handler = Service.loadService(UpdateHandler.class, updateHandler);

			if (handlerSetup != null) {
				handlerSetup.accept(handler);
			}
		}

		// to be moved in final location after all files completed download
		// or -- in case if updateTemp -- in Update.finalizeUpdate()
		Map<FileMetadata, Path> downloadedCollection = new HashMap<>();

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

			long updateJobSize = osFiles.stream().mapToLong(FileMetadata::getSize).sum();
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

					URLConnection connection = file.getUri().toURL().openConnection();

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

	private void completeDownloads(Map<FileMetadata, Path> files, Path tempDir, boolean isTemp) throws IOException {

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

	private void validateFile(FileMetadata file, Path output, Signature sig) throws IOException, SignatureException {

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

		//sig: signature in config, file.getSignature: local signature, if public key is available
		if (sig != null || file.getSignature() != null) {
			if (file.getSignature() == null)
				throw new SecurityException("Missing signature.");

			if (!sig.verify(file.getSignature()))
				throw new SecurityException("Signature verification failed.");
		}

		if (file.getPath().toString().endsWith(".jar") && !file.isIgnoreBootConflict()) {
			checkBootConflicts(file, output);
		}
	}

	private void checkBootConflicts(FileMetadata file, Path download) throws IOException {
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
			newMod = ModuleFinder.of(newPath).findAll().stream().map(ModuleReference::descriptor).findAny().orElse(
							null);
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
				throw new IllegalStateException("Package '" + p + "' conflicts with a package in the boot modulepath");

			}
		}
	}

	public void launch() {
		launch(null, (Launcher) null);
	}

	public void launch(Consumer<? super Launcher> launcherSetup) {
		launch(null, launcherSetup);
	}

	public void launch(List<String> args) {
		launch(args, (Launcher) null);
	}

	public void launch(Launcher launcher) {
		launch(null, launcher);
	}

	public void launch(List<String> args, Consumer<? super Launcher> launcherSetup) {
		launchImpl(args, null, launcherSetup);
	}

	public void launch(List<String> args, Launcher launcher) {
		launchImpl(args, launcher, null);
	}

	private void launchImpl(List<String> args, Launcher launcher, Consumer<? super Launcher> launcherSetup) {
		args = args == null ? List.of() : Collections.unmodifiableList(args);

		List<FileMetadata> modules = getFiles().stream()
						.filter(file -> file.getOs() == null || file.getOs() == OS.CURRENT)
						.filter(FileMetadata::isModulepath)
						.collect(Collectors.toList());

		List<Path> modulepaths = modules.stream().map(FileMetadata::getPath).collect(Collectors.toList());

		List<URL> classpaths = getFiles().stream()
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

		boolean usingSpi = launcher == null;
		if (usingSpi) {
			launcher = Service.loadService(layer, contextClassLoader, Launcher.class, this.launcher);

			if (launcherSetup != null) {
				launcherSetup.accept(launcher);
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

	/**
	 * Reads and parses a configuration XML.
	 * 
	 * @param reader
	 *            The {@code Reader} for reading the XML.
	 * @return A {@code Configuration} as parsed from the given XML.
	 * @throws IOException
	 *             Any exception that arises while reading.
	 */
	public static Configuration read(Reader reader) throws IOException {
		ConfigMapper configMapper = ConfigMapper.read(reader);

		return parseImpl(configMapper);
	}

	/**
	 * Parses a configuration from the given XML mapper.
	 * 
	 * @param mapper
	 *            The mapper to parse.
	 * @return A {@code Configuration} as parsed from the mapper.
	 */

	public static Configuration parse(ConfigMapper mapper) {
		return parseImpl(new ConfigMapper(mapper));
	}

	private static Configuration parseImpl(ConfigMapper configMapper) {
		PropertyManager propManager = new PropertyManager(configMapper.properties, null);
		return parseImpl(configMapper, propManager);
	}

	private static Configuration parseImpl(ConfigMapper configMapper, PropertyManager propManager) {
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

		for (FileMapper fm : configMapper.files) {
			FileMetadata.Builder fileBuilder = FileMetadata.builder().baseUri(config.getBaseUri()).basePath(
							config.getBasePath());

			if (fm.uri != null) {
				String s = config.resolvePlaceholders(fm.uri, true, fm.os != null && fm.os != OS.CURRENT);

				// Might happen when trying to parse foreign os properties
				if (!PropertyManager.containsPlaceholder(s)) {
					fileBuilder.uri(URI.create(s));
				}
			}

			if (fm.path != null) {
				String s = config.resolvePlaceholders(fm.path, true, fm.os != null && fm.os != OS.CURRENT);

				if (!PropertyManager.containsPlaceholder(s)) {
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

			if (fm.comment != null) {
				fileBuilder.comment(config.resolvePlaceholders(fm.comment, false));
			}

			if (fm.signature != null) {
				fileBuilder.signature(fm.signature);
			}

			fileBuilder.exports(fm.addExports);
			fileBuilder.opens(fm.addOpens);
			fileBuilder.reads(fm.addReads);

			FileMetadata file = fileBuilder.build();
			for (FileMetadata prevFile : files) {
				if (prevFile.getPath().equals(file.getPath())) {
					throw new IllegalStateException("2 files resolve to same 'path': " + file.getPath());
				}
			}

			files.add(file);
		}

		config.unmodifiableFiles = Collections.unmodifiableList(files);
		config.mapper = configMapper;

		return config;
	}

	/**
	 * Returns a new {@code Configuration} where all file sizes and checksums are
	 * synced with the real locations as listed in the current config. If changes
	 * were made it will also update the {@code timestamp}.
	 * 
	 * <p>
	 * This method is intended to be used on the development machine only to draft a
	 * new release when changes are made to files but it's still the same files.
	 * 
	 * <p>
	 * If you want to change the files you should generally use the Builder API. If
	 * you want, you can manually add or remove files in the config file; for new
	 * files, just put the {@code path} and call sync to automatically fill the
	 * rest.
	 * 
	 * @return A new {@code Configuration} with synced file metadata.
	 * @throws IOException
	 *             If any exception arises while reading file metadata.
	 */
	public Configuration sync() throws IOException {
		return sync(null, null);
	}

	/**
	 * Returns a new {@code Configuration} where all file sizes and checksums are
	 * synced with the real locations as listed in the current config with the base
	 * path overriden to the given {@code Path}. This is generally used to point to
	 * the build output location. Files listed with an explicit absolute path will
	 * not be overriden. If changes were made it will also update the
	 * {@code timestamp}.
	 * 
	 * <p>
	 * This method is intended to be used on the development machine only to draft a
	 * new release when changes are made to files but it's still the same files.
	 * 
	 * <p>
	 * If you want to change the files you should generally use the Builder API. If
	 * you want, you can manually add or remove files in the config file; for new
	 * files, just put the {@code path} and call sync to automatically fill the
	 * rest.
	 * 
	 * @param overrideBasePath
	 *            The {@code Path} to use instead of the base path to lookup files.
	 * @return A new {@code Configuration} with synced file metadata.
	 * @throws IOException
	 *             If any exception arises while reading file metadata
	 */
	public Configuration sync(Path overrideBasePath) throws IOException {
		return sync(overrideBasePath, null);
	}

	/**
	 * Returns a new {@code Configuration} where all file sizes, checksums and
	 * signatures are synced with the real locations as listed in the current
	 * config. If changes were made it will also update the {@code timestamp}.
	 * 
	 * <p>
	 * This method is intended to be used on the development machine only to draft a
	 * new release when changes are made to files but it's still the same files.
	 * 
	 * <p>
	 * If you want to change the files you should generally use the Builder API. If
	 * you want, you can manually add or remove files in the config file; for new
	 * files, just put the {@code path} and call sync to automatically fill the
	 * rest.
	 * 
	 * @param signer
	 *            The {@link PrivateKey} to use for file signing.
	 * @return A new {@code Configuration} with synced file metadata.
	 * @throws IOException
	 *             If any exception arises while reading file metadata.
	 */
	public Configuration sync(PrivateKey signer) throws IOException {
		return sync(null, signer);
	}

	/**
	 * Returns a new {@code Configuration} where all file sizes, checksums and
	 * signatures are synced with the real locations as listed in the current config
	 * with the base path overriden to the given {@code Path}. This is generally
	 * used to point to the build output location. Files listed with an explicit
	 * absolute path will not be overriden. If changes were made it will also update
	 * the {@code timestamp}.
	 * 
	 * <p>
	 * This method is intended to be used on the development machine only to draft a
	 * new release when changes are made to files but it's still the same files.
	 * 
	 * <p>
	 * If you want to change the files you should generally use the Builder API. If
	 * you want, you can manually add or remove files in the config file; for new
	 * files, just put the {@code path} and call sync to automatically fill the
	 * rest.
	 * 
	 * @param overrideBasePath
	 *            The {@code Path} to use instead of the base path to lookup files.
	 * @param signer
	 *            The {@link PrivateKey} to use for file signing.
	 * @return A new {@code Configuration} with synced file metadata.
	 * @throws IOException
	 *             If any exception arises while reading file metadata
	 */
	public Configuration sync(Path overrideBasePath, PrivateKey signer) throws IOException {
		ConfigMapper newMapper = generateXmlMapper();

		for (int i = 0; i < getFiles().size(); i++) {

			FileMetadata fm = getFiles().get(i);
			Path path;
			if (overrideBasePath == null || getBasePath().relativize(fm.getPath()).isAbsolute()) {
				path = fm.getPath();
			} else {
				path = overrideBasePath.resolve(getBasePath().relativize(fm.getPath()));
			}

			if (Files.notExists(path)) {
				System.err.println("[WARNING] File '" + path.getFileName() + "' is missing; skipped.");

				continue;
			}

			FileMapper fileMapper = newMapper.files.get(i);

			long checksum = FileUtils.getChecksum(path);
			fileMapper.size = Files.size(path);
			fileMapper.checksum = Long.toString(checksum, 16);

			if (signer == null) {
				fileMapper.signature = null;
			} else {
				fileMapper.signature = Base64.getEncoder().encodeToString(FileUtils.sign(path, signer));
			}

			if (fm.getSize() != fileMapper.size || fm.getChecksum() != checksum) {
				System.out.println("[INFO] Synced '" + path.getFileName() + "'.");

				newMapper.timestamp = Instant.now().toString();
			}

		}

		return parseImpl(newMapper);
	}

	/**
	 * Generates a new XML mapper for direct XML manipulation with values populated
	 * identical to this configuration. More formally:
	 * 
	 * <pre>
	 * this.equals(Configuration.parse(this.generateXmlMapper())) == true
	 * </pre>
	 * 
	 * <p>
	 * Any change to the mapper has no effect to the current configuration. A new
	 * copy is created on each call.
	 * 
	 * @return A new XML mapper with values from this configuration.
	 */
	public ConfigMapper generateXmlMapper() {
		return new ConfigMapper(mapper);
	}

	public void write(Writer writer) throws IOException {
		mapper.write(writer);
	}

	/**
	 * Returns an XML string exactly as {@code #write(Writer)} would output.
	 * 
	 * @return An XML string exactly as {@code #write(Writer)} would output.
	 */
	@Override
	public String toString() {
		StringWriter out = new StringWriter();
		try {
			write(out);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return out.toString();
	}

	/**
	 * Returns whether the given configuration is equals to this. More formally:
	 * 
	 * <pre>
	 * this.equals(other) == this.toString().equals(other.toString())
	 * </pre>
	 * 
	 * @return Whether the given configuration is equals to this.
	 */
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

	/**
	 * The entry point to the Builder API.
	 * 
	 * <p>
	 * This should <em>only</em> be used on the development machine when drafting a
	 * new release. It should not be used to load a config on the client side.
	 * 
	 * @return A configuration builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * This class is used to generate new configurations when a new draft is
	 * released. This might be called directly in code or used in various build
	 * plugins.
	 * 
	 * <p>
	 * In the builder process you refer to actual files on your machine, it will
	 * read the file metadata and create a new configuration. With the exceptions of
	 * {@link FileMetadata#readFrom(String)} and
	 * {@link FileMetadata#streamDirectory(String)} all string methods may take
	 * placeholder values that may refer to listed properties, system properties or
	 * system environment variables.
	 * 
	 * <p>
	 * To refer to a property {@code my.prop} with the value {@code Hello}:
	 * 
	 * <pre>{@code
	 * "${my.prop} World!" -> "Hello World"
	 * }</pre>
	 * 
	 * <p>
	 * Or, on Windows:
	 * 
	 * <pre>{@code
	 * "${LOCALAPPDATA}/My App" -> "C:/Users/<user-name>/AppData/Local/My App"
	 * }</pre>
	 * 
	 * <p>
	 * Placeholder references are resolved when {@code build()} is called.
	 * 
	 * <p>
	 * If a string has a value that can be replaced with a property placeholder but
	 * was hardcoded, it will be replaced for you. You can control how to replace
	 * via {@link #matchAndReplace(PlaceholderMatchType)} in both the config and in
	 * each individual file.
	 * 
	 * 
	 * 
	 * @author Mordechai Meisels
	 *
	 */
	public static class Builder {
		private String baseUri;
		private String basePath;
		private String updateHandler;
		private String launcher;

		private List<FileMetadata.Reference> files;

		private List<Property> properties;
		private List<String> systemProperties;

		private PrivateKey signer;
		private PlaceholderMatchType matcher;

		private Builder() {
			files = new ArrayList<>();
			properties = new ArrayList<>();
			systemProperties = new ArrayList<>();

			resolveSystemProperty("user.home");
			resolveSystemProperty("user.dir");
		}

		/**
		 * Set the base URI that files with a relative {@code uri} should resolve
		 * against. Files with an absolute URI will ignore this field.
		 * 
		 * <p>
		 * You may use a placeholder value for this field.
		 * 
		 * <p>
		 * If this is not set, all files <em>must</em> have an absolute URI.
		 * 
		 * @param uri
		 *            The base URI that files with a relative {@code uri} should resolve
		 *            against.
		 * @return The builder for chaining.
		 */
		public Builder baseUri(String uri) {
			this.baseUri = uri;

			return this;
		}

		/**
		 * Set the base URI that files with a relative {@code uri} should resolve
		 * against. Files with an absolute URI will ignore this field.
		 * 
		 * <p>
		 * This is equivalent to:
		 * 
		 * <pre>
		 * baseUri(uri.toString())
		 * </pre>
		 * 
		 * <p>
		 * If this is not set, all files <em>must</em> have an absolute URI.
		 * 
		 * @param uri
		 *            The base URI that files with a relative {@code uri} should resolve
		 *            against.
		 * @return The builder for chaining.
		 */
		public Builder baseUri(URI uri) {
			return this.baseUri(uri.toString());
		}

		/**
		 * Returns the value passed in {@link #baseUri(String)}.
		 * 
		 * @return The value passed in {@link #baseUri(String)}.
		 */
		public String getBaseUri() {
			return baseUri;
		}

		/**
		 * Set the base path that files with a relative {@code path} should resolve
		 * against. Files with an absolute path will ignore this field.
		 * 
		 * <p>
		 * You may use a placeholder value for this field.
		 * 
		 * <p>
		 * If this is not set, all files <em>must</em> have an absolute path.
		 * 
		 * @param path
		 *            The base path that files with a relative {@code path} should
		 *            resolve against.
		 * @return The builder for chaining.
		 */
		public Builder basePath(String path) {
			this.basePath = path;

			return this;
		}

		/**
		 * Set the base path that files with a relative {@code path} should resolve
		 * against. Files with an absolute path will ignore this field.
		 * 
		 * <p>
		 * This is equivalent to:
		 * 
		 * <pre>
		 * basePath(path.toString())
		 * </pre>
		 * 
		 * <p>
		 * If this is not set, all files <em>must</em> have an absolute path.
		 * 
		 * @param path
		 *            The base path that files with a relative {@code path} should
		 *            resolve against.
		 * @return The builder for chaining.
		 */
		public Builder basePath(Path path) {
			return basePath(path.toString());
		}

		/**
		 * Returns the value passed in {@link #basePath(String)}.
		 * 
		 * @return The value passed in {@link #basePath(String)}.
		 */
		public String getBasePath() {
			return basePath;
		}

		/**
		 * Set the private key to use for file signing. If not set, the files will not
		 * be signed.
		 * 
		 * @param key
		 *            the {@link PrivateKey} for file signing.
		 * @return The builder for chaining.
		 */
		public Builder signer(PrivateKey key) {
			this.signer = key;

			return this;
		}

		/**
		 * Returns the value passed in {@link #signer(PrivateKey)}.
		 * 
		 * @return The value passed in {@link #signer(PrivateKey)}.
		 */
		public PrivateKey getSigner() {
			return signer;
		}

		/**
		 * List a single file in the configuration. Files are listed using
		 * {@link FileMetadata#readFrom(Path)}. You can customize the individual file
		 * with the value returned from {@code readFrom()}.
		 * 
		 * <p>
		 * This method can be called repeatedly. It will add them all to a list.
		 * 
		 * @param reference
		 *            A file reference to list in the configuration.
		 * @return The builder for chaining.
		 */
		public Builder file(FileMetadata.Reference reference) {
			files.add(reference);

			return this;
		}

		/**
		 * List a collection of files in the configuration.
		 * 
		 * <p>
		 * This method can be called repeatedly. It will add them all to a single list.
		 * 
		 * @param refs
		 *            A collection of file references to list in the configuration.
		 * @return The builder for chaining.
		 */
		public Builder files(Collection<FileMetadata.Reference> refs) {
			files.addAll(refs);

			return this;
		}

		/**
		 * List a stream of {@link FileMetadata} instances in the configuration. Streams
		 * can be created using {@link FileMetadata#streamDirectory(Path)} and
		 * customized using {@code peek()} or {@code map()}.
		 * 
		 * <p>
		 * This method can be called repeatedly. It will add them all to a single list.
		 * 
		 * @param fileStream
		 *            A stream of file references to list in the configuration.
		 * @return The builder for chaining.
		 */
		public Builder files(Stream<FileMetadata.Reference> fileStream) {
			files.addAll(fileStream.collect(Collectors.toList()));

			return this;
		}

		/**
		 * Returns all files listed via {@code file()} or {@code files()}.
		 * 
		 * @return All files listed via {@code file()} or {@code files()}.
		 */
		public List<FileMetadata.Reference> getFiles() {
			return files;
		}

		/**
		 * List a single property with the given key and value. The value may contain a
		 * placeholder.
		 * 
		 * <p>
		 * This method can be called repeatedly. It will add them all to a single list.
		 * 
		 * 
		 * @param key
		 *            The key of the property.
		 * @param value
		 *            The value of the property.
		 * @return The builder for chaining.
		 */
		public Builder property(String key, String value) {
			return property(key, value, null);
		}

		/**
		 * List a single property with the given key and value that should only resolve
		 * for the given operating system. You may have more than one property with the
		 * same key if non of them have the same os.
		 * 
		 * <p>
		 * The value may contain a placeholder.
		 * 
		 * <p>
		 * This method can be called repeatedly. It will add them all to a single list.
		 * 
		 * 
		 * @param key
		 *            The key of the property.
		 * @param value
		 *            The value of the property.
		 * @param os
		 *            The operating system to limit this property.
		 * @return The builder for chaining.
		 */
		public Builder property(String key, String value, OS os) {
			properties.add(new Property(key, value, os));

			return this;
		}

		/**
		 * Lists a single {@link Property} in the configuration.
		 * 
		 * <p>
		 * This method can be called repeatedly. It will add them all to a single list.
		 * 
		 * @param p
		 *            The property to list.
		 * @return The builder for chaining.
		 */
		public Builder property(Property p) {
			properties.add(p);

			return this;
		}

		/**
		 * List a collection of properties in the configuration.
		 * 
		 * <p>
		 * This method can be called repeatedly. It will add them all to a single list.
		 * 
		 * @param props
		 *            The collection of properties to list.
		 * @return The builder for chaining.
		 */
		public Builder properties(Collection<Property> props) {
			properties.addAll(props);

			return this;
		}

		/**
		 * Returns all properties listed via {@code property()} or {@code properties()}.
		 * 
		 * @return All properties listed via {@code property()} or {@code properties()}.
		 */
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

		public Builder matchAndReplace(PlaceholderMatchType matcher) {
			this.matcher = matcher;

			return this;
		}

		public PlaceholderMatchType getMatchType() {
			return matcher;
		}

		public Configuration build() {
			PlaceholderMatchType matcher = this.matcher;
			if (matcher == null) {
				matcher = PlaceholderMatchType.WHOLE_WORD;
			}

			ConfigMapper mapper = new ConfigMapper();
			PropertyManager pm = new PropertyManager(properties, systemProperties);

			mapper.timestamp = Instant.now().toString();

			if (baseUri != null)
				mapper.baseUri = pm.implyPlaceholders(baseUri, matcher, true);

			if (basePath != null)
				mapper.basePath = pm.implyPlaceholders(basePath, matcher, true);

			if (updateHandler != null)
				mapper.updateHandler = pm.implyPlaceholders(updateHandler, matcher, false);

			if (launcher != null)
				mapper.launcher = pm.implyPlaceholders(launcher, matcher, false);

			if (!properties.isEmpty())
				mapper.properties.addAll(properties);

			if (!files.isEmpty()) {
				for (FileMetadata.Reference fileRef : files) {
					mapper.files.add(fileRef.getFileMapper(pm, baseUri, basePath, matcher, signer));
				}
			}

			return Configuration.parseImpl(mapper, pm);

		}

	}
}
