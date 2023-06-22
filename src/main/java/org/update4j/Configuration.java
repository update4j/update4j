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
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.update4j.UpdateOptions.ArchiveUpdateOptions;
import org.update4j.inject.Injectable;
import org.update4j.inject.PostInject;
import org.update4j.mapper.ConfigMapper;
import org.update4j.mapper.FileMapper;
import org.update4j.service.Launcher;
import org.update4j.service.UpdateHandler;
import org.update4j.util.FileUtils;
import org.update4j.util.PropertyManager;
import org.update4j.util.StringUtils;

import static java.lang.System.Logger.Level.INFO;
import static java.lang.System.Logger.Level.WARNING;

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
 * A configuration (or config) is linked to an XML file, and this class provide
 * methods to read, write, generate and sync configurations. Once a
 * configuration has been created, it is immutable and cannot be modified. There
 * are methods to manipulate the XML elements and create new configurations from
 * them, but the original remains untouched.
 * 
 * <h2>Terminology</h2>
 * <p>
 * <ul>
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
 * <h3>1. Using the Builder API</h3>
 * <p>
 * {@link Configuration#builder()} is the entry point to the config builder API.
 * 
 * <p>
 * Here's a sample config created with this approach:
 * 
 * <pre>
 * Configuration config = Configuration.builder()
 *                 // resolve uri and path of each individual file against the base.
 *                 // if not present you must provider the absolute location to every individual file
 *                 // with the uri() and path() method
 *                 .baseUri("http://example.com/")
 * 
 *                 // reads actual value from client system property "user.home"
 *                 .basePath("${user.home}/myapp/")
 * 
 *                 // list all files from the given directory
 *                 .files(FileMetadata.streamDirectory("build/mylibs")
 *                                 // mark all jar files for classpath
 *                                 .peek(r -> r.classpath(r.getSource().toString().endsWith(".jar"))))
 * 
 *                 .file(FileMetadata.readFrom("otherDirectory/my-logo.png")
 * 
 *                                 //override http://example.com above
 *                                 .uri("https://s3.aws.com/some-location/img.png")
 * 
 *                                 // resolves base from basePath but
 *                                 // overrides my-logo.png from source
 *                                 .path("application-logo.png"))
 * 
 *                 // we're done!
 *                 .build();
 * </pre>
 * 
 * 
 * <h3>2. Synchronizing Existing Configuration</h3>
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
 * <pre>
 * &lt;configuration timestamp="2018-08-22T19:31:40.448450500Z"&gt;
 *     &lt;base uri="https://example.com/" path="${user.loc}"/&gt;
 *     &lt;properties&gt;
 *         &lt;property key="user.loc" value="${user.home}/Desktop/"/&gt;
 *     &lt;/properties&gt;
 *     &lt;files&gt;
 *         &lt;file path="file1.jar" size="1348" checksum="fd7adfb7"/&gt;
 *     &lt;/files&gt;
 * &lt;/configuration&gt;
 * </pre>
 * 
 * <p>
 * 
 * You can synchronize it as:
 * 
 * <pre>
 * Configuration config = Configuration.read(Files.newBufferedReader(Paths.get("config.xml")));
 * 
 * // read files from actual locations listed in config
 * // in our case ${user.home}/Desktop/file1.jar
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
 * <pre>
 * &lt;files&gt;
 *         &lt;file path="file1.jar" size="1348" checksum="fd7adfb7"/&gt;
 *         
 *         &lt;!-- The new file --&gt;
 *         &lt;file path="file2.jar" /&gt;
 * &lt;/files&gt;
 * </pre>
 * 
 * 
 * <h3>3. Manual XML Manipulation</h3>
 * <p>
 * You can access the XML DOM with the {@link ConfigMapper} class and load a
 * config using {@link Configuration#parse(ConfigMapper)} to obtain a new
 * configuration. You can also write a mapper without parsing &mdash;
 * essentially skipping all validations &mdash; by the
 * {@link ConfigMapper#write(Writer)}.
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
 *     config.write(out);
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
 *     config = Configuration.read(new InputStreamReader(in));
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
 * next restart. In the latter case &mdash; on Windows &mdash; you cannot update
 * existing files, since the JVM locks them upon launch; you can call any of the
 * {@code updateTemp()} overloads and complete the update on next restart via
 * {@link Update#finalizeUpdate(Path)}.
 * 
 * <p>
 * When update is called without explicitly passing an {@link UpdateHandler}
 * instance and {@link Configuration#getUpdateHandler()} returns {@code null},
 * the framework will try to locate one between the registered service providers
 * and will use the one with the highest {@code version()} number. If
 * {@link Configuration#getUpdateHandler()} returns a class name, it will load
 * that class instead.
 * 
 * <p>
 * For more info how to register providers please refer to the <a
 * href=https://github.com/update4j/update4j/wiki/Documentation#dealing-with-providers>Github
 * Wiki</a>.
 * 
 * <p>
 * Regular updating (not {@code updateTemp()}):
 * 
 * <pre>
 * // loads a registered provider or DefaultUpdateHandler if non are found.
 * config.update(); // returns a boolean if succeeded
 * 
 * // updates with given update handler
 * config.update(new MyUpdateHandler());
 * 
 * // update and inject fields to and from the handler
 * config.update(myInjector);
 * 
 * // update and validate against the public key
 * config.update(myPubKey);
 * </pre>
 * 
 * <p>
 * Or you can update to a temporary location and finalize on next restart.
 * Here's a sample lifecycle:
 * 
 * <pre>
 * public static void main(String[] args) throws IOException {
 *     // the temporary location
 *     Path temp = Paths.get("update");
 * 
 *     // first check if last run made a temp update
 *     if (Update.containsUpdate(temp)) {
 *         Update.finalizeUpdate(temp);
 *     }
 * 
 *     // some random method
 *     Configuration config = getConfig();
 *     // we don't want to hang, so we can update immediately
 *     new Thread(() -> config.launch()).start();
 * 
 *     // and *after* launch do the update
 *     if (config.requiresUpdate()) {
 *         config.updateTemp(temp);
 *     }
 * }
 * </pre>
 * 
 * <h3>Consistency</h3>
 * <p>
 * If even a single file failed to download or if any other exception arises,
 * all downloads before the exception is rolled back to its original state as
 * before the call for update.
 * 
 * <h3>Boot Modulepath Conflicts</h3>
 * <p>
 * Every jar file gets checked if it were a valid file in boot modulepath; such
 * as if it's a valid zip file, duplicate module name, split package, valid
 * automatic module name etc., no matter if the file was actually intended to be
 * present in the boot modulepath. This was put in place to prevent accidentally
 * making the file visible to the boot modulepath and completely breaking the
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
 * sign the configuration and files via the {@code signer()} method in the
 * Builder API, or {@link Configuration#sync(PrivateKey)}.
 * 
 * <p>
 * The config signature ensures that it has not been tampered, as changed URIs,
 * paths or properties. Changes that are not significant to the framework, as
 * whitespaces or element/attribute order do not matter. Changing element order
 * in lists (i.e. properties or files) do change the ordering in the
 * end-resulting list, and is part of the signature. The {@code timestamp} field
 * is never part of the signature. To verify the config itself, read it with the
 * {@link #read(Reader, PublicKey)} overload, or invoke
 * {@link #verifyConfiguration(PublicKey)}.
 * 
 * <p>
 * To verify files on update, use the {@link PublicKey} overload of
 * {@code update()} or {@code updateTemp()} and it will reject the download if
 * any file fails.
 * 
 * <h1>Launching</h1>
 * <p>
 * Launching loads files onto the dynamic classpath or modulepath (or does not
 * load it if not marked with either), depending on their configuration and
 * launches it by using either a passed {@link Launcher}, or by loading a
 * launcher provider.
 * 
 * <p>
 * Accessing business classes in the bootstrap depends on the classloader
 * configuration. Please consult the <a href=
 * "https://github.com/update4j/update4j/wiki/Documentation#classloading-model">
 * GitHub wiki</a> for a thorough walkthrough of possible options.
 * 
 * <p>
 * When launch is called without explicitly passing a {@link Launcher} instance
 * and {@link #getLauncher()} returns {@code null}, the framework will try to
 * locate one between the registered service providers and will use the one with
 * the highest {@code version()} number. If {@link #getLauncher()} returns a
 * class name, it will load that class instead.
 * 
 * <p>
 * If an explicit launcher instance was passed, the instance only has reflective
 * access to the Business Application by reflecting against
 * {@link LaunchContext#getClassLoader()} unless you used the
 * {@link DynamicClassLoader}.
 * 
 * 
 * <pre>
 * // launch with registered launcher or DefaultLauncher if non were found
 * config.launch();
 * 
 * // launch and inject fields to and from the launcher
 * // assume the caller implements Injectable
 * config.launch(this);
 * 
 * // launch with passed launcher, *only reflective access*
 * config.launch(new MyLauncher());
 * 
 * // using DynamicClassLoader as context class loader
 * ClassLoader loader = new DynamicClassLoader();
 * Thread.currentThread().setContextClassLoader(loader);
 * 
 * config.launch(new MyLauncher());
 * Class&lt?&gt; loader.loadClass("com.example.BusinessClass");
 * 
 * // starting the application with the flag -Djava.system.class.loader=org.update4j.DynamicClassLoader
 * BusinessClass business = new BusinessClass() // Boom, NoClassDefFoundError
 * 
 * config.launch();
 * BusinessClass business = new BusinessClass() // Works flawlessly
 * </pre>
 * 
 * @author Mordechai Meisels
 *
 */
public class Configuration {

    private static final System.Logger logger = System.getLogger(Configuration.class.getName());

    private Instant timestamp;
    private String signature;

    private URI baseUri;
    private Path basePath;
    private String updateHandler;
    private String launcher;

    private List<FileMetadata> unmodifiableFiles;
    private PropertyManager propertyManager;

    private ConfigMapper mapper;

    private Configuration() {
    }

    /**
     * Returns the timestamp this configuration was last updated using the
     * {@link Configuration.Builder} API or {@code sync()}. This is read from the
     * {@code timestamp} attribute in the <em>root</em> element. If the attribute is
     * missing this will return {@code null}.
     * <p>
     * It does not have any effect on the behavior of anything else; it is rather
     * just for reference purposes (i.e. "Last Updated: 2 Weeks Ago"), or for
     * clients willing to act according to this value.
     * 
     * 
     * @return The timestamp this configuration was last updated, or @{null}, if
     *         missing from the XML.
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the signature for this configuration. The signature ensures that the
     * config has not been tampered. Changes that are not significant to the
     * framework, as whitespaces or element/attribute order do not matter. Changing
     * element order in lists (i.e. properties or files) do change the ordering in
     * the end-resulting list, and is part of the signature.
     * 
     * <p>
     * The {@code timestamp} field is never part of the signature.
     * 
     * <p>
     * This field is read from the {@code signature} in the <em>root</em> node. If
     * the attribute is missing this will return {@code null}.
     * 
     * @return The signature for this configuration.
     */
    public String getSignature() {
        return signature;
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
     * classpath or modulepath.
     * 
     * Other than overriding the versioning resolution, it also relieves you from
     * having to advertise them as required by the {@link ServiceLoader} class.
     * Still, for modules you would want to add the {@code provides} directive,
     * since this would add the module in the module graph and make the class
     * visible to this framework.<br>
     * Please refer to <a
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
     * classpath or modulepath.
     * 
     * Other than overriding the versioning resolution, it also relieves you from
     * having to advertise them as required by the {@link ServiceLoader} class.
     * Still, for modules you would want to add the {@code provides} directive,
     * since this would add the module in the module graph and make the class
     * visible to this framework.<br>
     * Please refer to <a
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
     * @return The {@link Property} instances listed in the configuration file.
     */
    public List<Property> getProperties() {
        return propertyManager.getProperties();
    }

    /**
     * Returns a list of properties listed in the configuration file that have the
     * provided key. It might be more than one, if they have different operating
     * systems. The list will never contain 2 properties with the same value
     * returned by {@link Property#getOs()}.
     * 
     * <p>
     * The list might be empty, but never {@code null}.
     * 
     * 
     * @return The {@link Property} instances listed in the configuration file that
     *         contain the provided key.
     */
    public List<Property> getProperties(String key) {
        return propertyManager.getProperties(key);
    }

    /**
     * Returns an unmodifiable map of keys and values after resolving the
     * placeholders. It includes everything from dynamic properties to system
     * properties or environment variables. This will not include properties marked
     * for foreign operating systems.
     * 
     * @return A map of the keys and real values of the properties, after resolving
     *         the placeholders.
     */
    public Map<String, String> getResolvedProperties() {
        return propertyManager.getResolvedProperties();
    }

    /**
     * Returns the real value of the property with the given key, after resolving
     * the placeholders. It includes everything from dynamic properties to system
     * properties or environment variables.
     * 
     * @param key
     *            The key of the property.
     * @return The real value of the property after resolving the placeholders.
     */
    public String getResolvedProperty(String key) {
        return propertyManager.getResolvedProperty(key);
    }

    /**
     * Returns the dynamic properties passed in {@link #read(Reader, Map)}.
     * 
     * <p>
     * If dynamic properties were not passed, it will return an empty map.
     * 
     * @return Provided dynamic properties, or empty map.
     */
    public Map<String, String> getDynamicProperties() {
        return propertyManager.getDynamicProperties();
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
     * <pre>
     * String old = "C:/Users/User/Desktop";
     * String newString = config.implyPlaceholders(old, true);
     * 
     * // -> newString is "${user.home}/Desktop" even though the real system
     * // property value is "C:\\Users\\User" on Windows
     * </pre>
     * 
     * <p>
     * Additionally, if {@code isPath} is {@code true}, {@code user.home} and
     * {@code user.dir} will only be matched to the beginning of the string or just
     * after the {@code file:} URI scheme in the beginning of the string.
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
     * doesn't break a word (using regex {@code \b} word boundary).</li>
     * <li>{@code FULL_MATCH} &mdash; Will only replace if the complete string
     * matches with one placeholder.</li>
     * </ul>
     * 
     * <p>
     * If the given string is {@code null}, the same value will be returned.
     * 
     * @param str
     *            The string to attempt to replace with placeholders.
     * @param matchType
     *            The word-breaking policy to use when matching.
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
     * <pre>
     * String old = "C:/Users/User/Desktop";
     * String newString = config.implyPlaceholders(old, true);
     * 
     * // -> newString is "${user.home}/Desktop" even though the real system
     * // property value is "C:\\Users\\User" on Windows
     * </pre>
     * 
     * <p>
     * Additionally, if {@code isPath} is {@code true}, {@code user.home} and
     * {@code user.dir} will only be matched to the beginning of the string or just
     * after the {@code file:} URI scheme in the beginning of the string.
     * 
     * <p>
     * You can specify how matches should be found by passing the
     * {@link PlaceholderMatchType}.
     * <ul>
     * <li>{@code EVERY_OCCURENCE} &mdash; Will break words with placeholders if it
     * finds a match.</li>
     * <li>{@code WHOLE_WORD} &mdash; Will only replace with placeholders if the it
     * doesn't break a word (using regex {@code \b} word boundary).</li>
     * <li>{@code FULL_MATCH} &mdash; Will only replace if the complete string
     * matches with one placeholder.</li>
     * </ul>
     * 
     * <p>
     * If the given string is {@code null}, the same value will be returned.
     * 
     * @param str
     *            The string to attempt to replace with placeholders.
     * @param matchType
     *            The match policy to use.
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
     * <p>
     * This method is completely unaware of
     * {@link UpdateHandler#shouldCheckForUpdate(FileMetadata)}, i.e. it might
     * return {@code true} even if that method returns {@code false} for a
     * particular file.
     * 
     * @return If at-least one file requires an update.
     * @throws IOException
     *             If any {@code IOException} arises while reading the files.
     */
    public boolean requiresUpdate() throws IOException {
        for (FileMetadata file : getFiles()) {
            if (file.requiresUpdate())
                return true;
        }

        return false;
    }

    /**
     * Archive-based update: All files are saved in a zipped file passed to
     * {@link UpdateOptions#archive(Path)}. Once the update is complete you are free
     * to process the archive to your liking. When you wish to install the update,
     * call {@link Archive#install()}.
     * 
     * <p>
     * The update process starts by locating the class returned by
     * {@link UpdateOptions#updateHandler()} or -- if it returns {@code null} -- the
     * registered highest version {@link UpdateHandler} or
     * {@link DefaultUpdateHandler} if non were found.
     * 
     * <p>
     * If an {@link Injectable} was passed in the options, after loading the
     * {@link UpdateHandler} class, it will call:
     * 
     * <pre>
     * Injectable.injectBidirectional(injectable, updateHandler);
     * </pre>
     * 
     * to exchange fields to and from both instances. When injection is complete it
     * will call all methods of both instances, marked with {@link PostInject},
     * following the behavior documented in {@link Injectable} documentation.
     * 
     * <p>
     * If a {@link PublicKey} was passed to the options, it will use it to validate
     * signatures of each individual file. It will <em>not</em> validate the
     * config's own signature.
     * 
     * <p>
     * Any error that arises once the update handler was loaded just get's passed to
     * {@link UpdateHandler#failed(Throwable)}. An exception thrown in
     * {@link UpdateHandler#failed(Throwable)}, {@link UpdateHandler#succeeded()} or
     * {@link UpdateHandler#stop()} will be thrown back to the caller of this
     * method.
     * 
     * <p>
     * This method is intended to be used on the client machine only.
     * 
     * @param options
     */
    public UpdateResult update(ArchiveUpdateOptions options) {
        return ConfigImpl.doUpdate(this, options);
    }

    /**
     * Starts the update process by locating the class returned by
     * {@link #getUpdateHandler()} or -- if it returns {@code null} -- the
     * registered highest version {@link UpdateHandler} or
     * {@link DefaultUpdateHandler} if non were found.
     * 
     * <p>
     * Any error that arises once the update handler was loaded just get's passed to
     * {@link UpdateHandler#failed(Throwable)} and this method returns
     * {@code false}. An exception thrown in
     * {@link UpdateHandler#failed(Throwable)}, {@link UpdateHandler#succeeded()} or
     * {@link UpdateHandler#stop()} will be thrown back to the caller of this
     * method.
     * 
     * <p>
     * This method is intended to be used on the client machine only.
     * 
     * @return If no error was thrown in the whole process.
     * @deprecated Superseded with the new archive-based update mechanism.
     */
    @Deprecated
    public boolean update() {
        return update((PublicKey) null);
    }

    /**
     * Starts the update process by using the provided instance as the update
     * handler.
     * 
     * <p>
     * Any error that arises just get's passed to
     * {@link UpdateHandler#failed(Throwable)} and this method returns
     * {@code false}. An exception thrown in
     * {@link UpdateHandler#failed(Throwable)}, {@link UpdateHandler#succeeded()} or
     * {@link UpdateHandler#stop()} will be thrown back to the caller of this
     * method.
     * 
     * <p>
     * This method is intended to be used on the client machine only.
     * 
     * @param handler
     *            The {@link UpdateHandler} to use for process callbacks.
     * @return If no error was thrown in the whole process.
     * @deprecated Superseded with the new archive-based update mechanism.
     */
    @Deprecated
    public boolean update(UpdateHandler handler) {
        return update((PublicKey) null, handler);
    }

    /**
     * Starts the update process by locating the class returned by
     * {@link #getUpdateHandler()} or -- if it returns {@code null} -- the
     * registered highest version {@link UpdateHandler} or
     * {@link DefaultUpdateHandler} if non were found.
     * 
     * <p>
     * Immediately after loading the class, it will call:
     * 
     * <pre>
     * Injectable.injectBidirectional(injectable, updateHandler);
     * </pre>
     * 
     * to exchange fields to and from both instances. When injection is complete it
     * will call all methods of both instances, marked with {@link PostInject},
     * following the behavior documented in {@link Injectable} documentation.
     * 
     * <p>
     * Any error that arises once the update handler was loaded just get's passed to
     * {@link UpdateHandler#failed(Throwable)} and this method returns
     * {@code false}. An exception thrown in
     * {@link UpdateHandler#failed(Throwable)}, {@link UpdateHandler#succeeded()} or
     * {@link UpdateHandler#stop()} will be thrown back to the caller of this
     * method.
     * 
     * <p>
     * This method is intended to be used on the client machine only.
     * 
     * @param injectable
     *            The object to use for field exchange between the bootstrap and the
     *            update handler.
     * @return If no error was thrown in the whole process.
     * @deprecated Superseded with the new archive-based update mechanism.
     */
    @Deprecated
    public boolean update(Injectable injectable) {
        return update(null, injectable);
    }

    /**
     * Starts the update process by locating the class returned by
     * {@link #getUpdateHandler()} or -- if it returns {@code null} -- the
     * registered highest version {@link UpdateHandler} or
     * {@link DefaultUpdateHandler} if non were found.
     * 
     * <p>
     * It will use the provided {@link PublicKey} to validate signatures of each
     * individual file. It will <em>not</em> validate the config's own signature.
     * 
     * <p>
     * Any error that arises once the update handler was loaded just get's passed to
     * {@link UpdateHandler#failed(Throwable)} and this method returns
     * {@code false}. An exception thrown in
     * {@link UpdateHandler#failed(Throwable)}, {@link UpdateHandler#succeeded()} or
     * {@link UpdateHandler#stop()} will be thrown back to the caller of this
     * method.
     * 
     * <p>
     * This method is intended to be used on the client machine only.
     * 
     * @param key
     *            The {@link PublicKey} to validate the files' signatures.
     * @return If no error was thrown in the whole process.
     * @deprecated Superseded with the new archive-based update mechanism.
     */
    @Deprecated
    public boolean update(PublicKey key) {
        return update(key, (UpdateHandler) null);
    }

    /**
     * Starts the update process by locating the class returned by
     * {@link #getUpdateHandler()} or -- if it returns {@code null} -- the
     * registered highest version {@link UpdateHandler} or
     * {@link DefaultUpdateHandler} if non were found.
     * 
     * <p>
     * Immediately after loading the class, it will call:
     * 
     * <pre>
     * Injectable.injectBidirectional(injectable, updateHandler);
     * </pre>
     * 
     * to exchange fields to and from both instances. When injection is complete it
     * will call all methods of both instances, marked with {@link PostInject},
     * following the behavior documented in {@link Injectable} documentation.
     * 
     * <p>
     * It will use the provided {@link PublicKey} to validate signatures of each
     * individual file. It will <em>not</em> validate the config's own signature.
     * 
     * <p>
     * Any error that arises once the update handler was loaded just get's passed to
     * {@link UpdateHandler#failed(Throwable)} and this method returns
     * {@code false}. An exception thrown in
     * {@link UpdateHandler#failed(Throwable)}, {@link UpdateHandler#succeeded()} or
     * {@link UpdateHandler#stop()} will be thrown back to the caller of this
     * method.
     * 
     * <p>
     * This method is intended to be used on the client machine only.
     * 
     * @param key
     *            The {@link PublicKey} to validate the files' signatures.
     * @param injectable
     *            The object to use for field exchange between the bootstrap and the
     *            update handler.
     * @return If no error was thrown in the whole process.
     * @deprecated Superseded with the new archive-based update mechanism.
     */
    @Deprecated
    public boolean update(PublicKey key, Injectable injectable) {
        return ConfigImpl.doLegacyUpdate(this, null, key, injectable, null);
    }

    /**
     * Starts the update process by using the provided instance as the update
     * handler.
     * 
     * <p>
     * It will use the provided {@link PublicKey} to validate signatures of each
     * individual file. It will <em>not</em> validate the config's own signature.
     * 
     * <p>
     * Any error that arises once the update handler was loaded just get's passed to
     * {@link UpdateHandler#failed(Throwable)} and this method returns
     * {@code false}. An exception thrown in
     * {@link UpdateHandler#failed(Throwable)}, {@link UpdateHandler#succeeded()} or
     * {@link UpdateHandler#stop()} will be thrown back to the caller of this
     * method.
     * 
     * <p>
     * This method is intended to be used on the client machine only.
     * 
     * @param key
     *            The {@link PublicKey} to validate the files' signatures.
     * @param handler
     *            The {@link UpdateHandler} to use for process callbacks.
     * @return If no error was thrown in the whole process.
     * @deprecated Superseded with the new archive-based update mechanism.
     */
    @Deprecated
    public boolean update(PublicKey key, UpdateHandler handler) {
        return ConfigImpl.doLegacyUpdate(this, null, key, null, handler);
    }

    /**
     * Starts the update process by locating the class returned by
     * {@link #getUpdateHandler()} or -- if it returns {@code null} -- the
     * registered highest version {@link UpdateHandler} or
     * {@link DefaultUpdateHandler} if non were found.
     * 
     * <p>
     * It will download all files in the {@code tempDir} directory, which can later
     * be finalized by calling {@link Update#finalizeUpdate(Path)}.
     * 
     * <p>
     * Any error that arises once the update handler was loaded just get's passed to
     * {@link UpdateHandler#failed(Throwable)} and this method returns
     * {@code false}. An exception thrown in
     * {@link UpdateHandler#failed(Throwable)}, {@link UpdateHandler#succeeded()} or
     * {@link UpdateHandler#stop()} will be thrown back to the caller of this
     * method.
     * 
     * <p>
     * This method is intended to be used on the client machine only.
     * 
     * @param tempDir
     *            The location to temporarily store the downloaded files until
     *            {@link Update#finalizeUpdate(Path)} is called.
     * @return If no error was thrown in the whole process.
     * @deprecated Superseded with the new archive-based update mechanism.
     */
    @Deprecated
    public boolean updateTemp(Path tempDir) {
        return updateTemp(tempDir, (PublicKey) null);
    }

    /**
     * Starts the update process by locating the class returned by
     * {@link #getUpdateHandler()} or -- if it returns {@code null} -- the
     * registered highest version {@link UpdateHandler} or
     * {@link DefaultUpdateHandler} if non were found.
     * 
     * <p>
     * Immediately after loading the class, it will call:
     * 
     * <pre>
     * Injectable.injectBidirectional(injectable, updateHandler);
     * </pre>
     * 
     * to exchange fields to and from both instances. When injection is complete it
     * will call all methods of both instances, marked with {@link PostInject},
     * following the behavior documented in {@link Injectable} documentation.
     * 
     * <p>
     * It will download all files in the {@code tempDir} directory, which can later
     * be finalized by calling {@link Update#finalizeUpdate(Path)}.
     * 
     * <p>
     * Any error that arises once the update handler was loaded just get's passed to
     * {@link UpdateHandler#failed(Throwable)} and this method returns
     * {@code false}. An exception thrown in
     * {@link UpdateHandler#failed(Throwable)}, {@link UpdateHandler#succeeded()} or
     * {@link UpdateHandler#stop()} will be thrown back to the caller of this
     * method.
     * 
     * <p>
     * This method is intended to be used on the client machine only.
     * 
     * @param tempDir
     *            The location to temporarily store the downloaded files until
     *            {@link Update#finalizeUpdate(Path)} is called.
     * @param injectable
     *            The object to use for field exchange between the bootstrap and the
     *            update handler.
     * @return If no error was thrown in the whole process.
     * @deprecated Superseded with the new archive-based update mechanism.
     */
    @Deprecated
    public boolean updateTemp(Path tempDir, Injectable injectable) {
        return updateTemp(tempDir, (PublicKey) null, injectable);
    }

    /**
     * Starts the update process by using the provided instance as the update
     * handler.
     * 
     * <p>
     * It will download all files in the {@code tempDir} directory, which can later
     * be finalized by calling {@link Update#finalizeUpdate(Path)}.
     * 
     * <p>
     * Any error that arises just get's passed to
     * {@link UpdateHandler#failed(Throwable)} and this method returns
     * {@code false}. An exception thrown in
     * {@link UpdateHandler#failed(Throwable)}, {@link UpdateHandler#succeeded()} or
     * {@link UpdateHandler#stop()} will be thrown back to the caller of this
     * method.
     * 
     * <p>
     * This method is intended to be used on the client machine only.
     * 
     * @param tempDir
     *            The location to temporarily store the downloaded files until
     *            {@link Update#finalizeUpdate(Path)} is called.
     * @param handler
     *            The {@link UpdateHandler} to use for process callbacks.
     * @return If no error was thrown in the whole process.
     * @deprecated Superseded with the new archive-based update mechanism.
     */
    @Deprecated
    public boolean updateTemp(Path tempDir, UpdateHandler handler) {
        return updateTemp(tempDir, (PublicKey) null, handler);
    }

    /**
     * Starts the update process by locating the class returned by
     * {@link #getUpdateHandler()} or -- if it returns {@code null} -- the
     * registered highest version {@link UpdateHandler} or
     * {@link DefaultUpdateHandler} if non were found.
     * 
     * <p>
     * It will download all files in the {@code tempDir} directory, which can later
     * be finalized by calling {@link Update#finalizeUpdate(Path)}.
     * 
     * <p>
     * It will use the provided {@link PublicKey} to validate signatures of each
     * individual file. It will <em>not</em> validate the config's own signature.
     * 
     * <p>
     * Any error that arises once the update handler was loaded just get's passed to
     * {@link UpdateHandler#failed(Throwable)} and this method returns
     * {@code false}. An exception thrown in
     * {@link UpdateHandler#failed(Throwable)}, {@link UpdateHandler#succeeded()} or
     * {@link UpdateHandler#stop()} will be thrown back to the caller of this
     * method.
     * 
     * <p>
     * This method is intended to be used on the client machine only.
     * 
     * @param tempDir
     *            The location to temporarily store the downloaded files until
     *            {@link Update#finalizeUpdate(Path)} is called.
     * @param key
     *            The {@link PublicKey} to validate the files' signatures.
     * @return If no error was thrown in the whole process.
     * @deprecated Superseded with the new archive-based update mechanism.
     */
    @Deprecated
    public boolean updateTemp(Path tempDir, PublicKey key) {
        return updateTemp(tempDir, key, (UpdateHandler) null);
    }

    /**
     * Starts the update process by locating the class returned by
     * {@link #getUpdateHandler()} or -- if it returns {@code null} -- the
     * registered highest version {@link UpdateHandler} or
     * {@link DefaultUpdateHandler} if non were found.
     * 
     * <p>
     * Immediately after loading the class, it will call:
     * 
     * <pre>
     * Injectable.injectBidirectional(injectable, updateHandler);
     * </pre>
     * 
     * to exchange fields to and from both instances. When injection is complete it
     * will call all methods of both instances, marked with {@link PostInject},
     * following the behavior documented in {@link Injectable} documentation.
     * 
     * <p>
     * It will download all files in the {@code tempDir} directory, which can later
     * be finalized by calling {@link Update#finalizeUpdate(Path)}.
     * 
     * <p>
     * It will use the provided {@link PublicKey} to validate signatures of each
     * individual file. It will <em>not</em> validate the config's own signature.
     * 
     * <p>
     * Any error that arises once the update handler was loaded just get's passed to
     * {@link UpdateHandler#failed(Throwable)} and this method returns
     * {@code false}. An exception thrown in
     * {@link UpdateHandler#failed(Throwable)}, {@link UpdateHandler#succeeded()} or
     * {@link UpdateHandler#stop()} will be thrown back to the caller of this
     * method.
     * 
     * <p>
     * This method is intended to be used on the client machine only.
     * 
     * @param tempDir
     *            The location to temporarily store the downloaded files until
     *            {@link Update#finalizeUpdate(Path)} is called.
     * @param key
     *            The {@link PublicKey} to validate the files' signatures.
     * @param injectable
     *            The object to use for field exchange between the bootstrap and the
     *            update handler.
     * @return If no error was thrown in the whole process.
     * @deprecated Superseded with the new archive-based update mechanism.
     */
    @Deprecated
    public boolean updateTemp(Path tempDir, PublicKey key, Injectable injectable) {
        return ConfigImpl.doLegacyUpdate(this, Objects.requireNonNull(tempDir), key, injectable, null);
    }

    /**
     * Starts the update process by using the provided instance as the update
     * handler.
     * 
     * <p>
     * It will download all files in the {@code tempDir} directory, which can later
     * be finalized by calling {@link Update#finalizeUpdate(Path)}.
     * 
     * <p>
     * It will use the provided {@link PublicKey} to validate signatures of each
     * individual file. It will <em>not</em> validate the config's own signature.
     * 
     * <p>
     * Any error that arises just get's passed to
     * {@link UpdateHandler#failed(Throwable)} and this method returns
     * {@code false}. An exception thrown in
     * {@link UpdateHandler#failed(Throwable)}, {@link UpdateHandler#succeeded()} or
     * {@link UpdateHandler#stop()} will be thrown back to the caller of this
     * method.
     * 
     * <p>
     * This method is intended to be used on the client machine only.
     * 
     * @param tempDir
     *            The location to temporarily store the downloaded files until
     *            {@link Update#finalizeUpdate(Path)} is called.
     * @param key
     *            The {@link PublicKey} to validate the files' signatures.
     * @param handler
     *            The {@link UpdateHandler} to use for process callbacks.
     * @return If no error was thrown in the whole process.
     * @deprecated Superseded with the new archive-based update mechanism.
     */
    @Deprecated
    public boolean updateTemp(Path tempDir, PublicKey key, UpdateHandler handler) {
        return ConfigImpl.doLegacyUpdate(this, Objects.requireNonNull(tempDir), key, null, handler);
    }

    /**
     * Launches the business application by loading all files marked with the
     * {@code classpath} or {@code modulepath} attributes, on their respective
     * paths, dynamically.
     * 
     * <p>
     * It will then locate the class returned by {@link #getLauncher()} or -- if it
     * returns {@code null} -- the registered highest version {@link Launcher} or
     * {@link DefaultLauncher} if non were found.
     * 
     * <p>
     * It will then call {@link Launcher#run(LaunchContext)} on a new thread, and
     * block the caller of this method until {@code run()} returns. New threads
     * spawned by the {@code run()} method will not block.
     * 
     * <p>
     * This method is intended to be used on the client machine only.
     * 
     */
    public void launch() {
        launch((Launcher) null);
    }

    /**
     * Launches the business application by loading all files marked with the
     * {@code classpath} or {@code modulepath} attributes, on their respective
     * paths, dynamically.
     * 
     * <p>
     * It will then locate the class returned by {@link #getLauncher()} or -- if it
     * returns {@code null} -- the registered highest version {@link Launcher} or
     * {@link DefaultLauncher} if non were found.
     * 
     * <p>
     * Immediately after loading the class, it will call:
     * 
     * <pre>
     * Injectable.injectBidirectional(injectable, launcher);
     * </pre>
     * 
     * to exchange fields to and from both instances. When injection is complete it
     * will call all methods of both instances, marked with {@link PostInject},
     * following the behavior documented in {@link Injectable} documentation.
     * 
     * <p>
     * It will then call {@link Launcher#run(LaunchContext)} on a new thread, and
     * block the caller of this method until {@code run()} returns. New threads
     * spawned by the {@code run()} method will not block.
     * 
     * <p>
     * This method is intended to be used on the client machine only.
     * 
     * @param injectable
     *            The object to use for field exchange between the bootstrap and the
     *            launcher.
     */
    public void launch(Injectable injectable) {
        ConfigImpl.doLaunch(this, injectable, null);
    }

    /**
     * Launches the business application by loading all files marked with the
     * {@code classpath} or {@code modulepath} attributes, on their respective
     * paths, dynamically.
     * 
     * <p>
     * It will then call {@link Launcher#run(LaunchContext)} on a new thread, and
     * block the caller of this method until {@code run()} returns. New threads
     * spawned by the {@code run()} method will not block.
     * 
     * <p>
     * This method is intended to be used on the client machine only.
     * 
     * @param launcher
     *            The launcher to use as the business application entry point
     */
    public void launch(Launcher launcher) {
        ConfigImpl.doLaunch(this, null, launcher);
    }

    /**
     * Convenience method to delete files only present in {@code oldConfig} and
     * clean up app directory.
     * 
     * <p>
     * <b>Caution:</b> This method does not guarantee all files are actually
     * removed. Many things can go wrong and new updates should <em>never</em> rely
     * on this operation. Don't release new bootstrap modules with existing module
     * or package names (by marking {@code ignoreBootConflict} to {@code true}) even
     * if the old "should" be deleted here. For service providers, increment the
     * {@code version()} to let update4j know it should select the new, even if the
     * old is deleted here.
     * 
     * 
     * <p>
     * A file in the old configuration is considered "old" if that file:
     * 
     * <ul>
     * <li>Exists.</li>
     * <li>Is not present in the current configuration. It will query the underlying
     * operating system to check equality instead of comparing path names.</li>
     * <li>If the file's checksum matches the checksum listed in the old config.
     * This is an extra - optional - layer of safety to prevent unwanted files from
     * being deleted. You can turn off this check by calling
     * {@link #deleteOldFiles(Configuration, boolean, int)} instead.
     * </ul>
     * 
     * <p>
     * Files that are not marked with either {@code classpath} or {@code modulepath}
     * in the config, will be assumed to run in the bootstrap; therefore will not be
     * deleted immediately. Instead, they will be queued to be deleted when the JVM
     * shuts down by spawning a new system-dependent process with 5 seconds delay
     * (you can change the delay by calling
     * {@link #deleteOldFiles(Configuration, boolean, int)} instead. Files that are
     * marked with {@code classpath} or {@code modulepath} will try to be deleted
     * immediately. If it fails (e.g. you called this method from the business
     * application and the files are locked by operating system), it will queue them
     * together with the bootstrap files.
     * 
     * <p>
     * Please note: Long running shutdown hooks may keep files locked thus
     * preventing them from being deleted. Call
     * {@link #deleteOldFiles(Configuration, boolean, int)} and increase the
     * {@code secondsDelay} to ensure it runs after all shutdown hooks completed.
     * 
     * <p>
     * You <em>must</em> not call this method if:
     * 
     * <pre>
     * this.requiresUpdate() == true
     * </pre>
     * 
     * in other words, you must first update the current config, or if using
     * {@code updateTemp()} you must first call {@link Update#finalizeUpdate(Path)}.
     * If you return {@code false} in
     * {@link UpdateHandler#shouldCheckForUpdate(FileMetadata)} for a particular
     * file, you cannot use this method out of the box. You can hand-modify the
     * config to strip those files by removing them with
     * {@link #generateXmlMapper()}. Consult the <em>Manual XML Manipulation</em>
     * section in this class JavaDoc.
     * 
     * 
     * @param oldConfig
     *            The old configuration.
     * @throws IllegalStateException
     *             If this method is called but the current configuration is not
     *             up-to-date.
     * @throws IOException
     *             If checking if current config is up-to-date, checking file
     *             equality, or calculating checksum failed.
     */
    public void deleteOldFiles(Configuration oldConfig) throws IOException {
        deleteOldFiles(oldConfig, true, 5);
    }

    /**
     * Convenience method to delete files only present in {@code oldConfig} and
     * clean up app directory.
     * 
     * <p>
     * <b>Caution:</b> This method does not guarantee all files are actually
     * removed. Many things can go wrong and new updates should <em>never</em> rely
     * on this operation. Don't release new bootstrap modules with existing module
     * or package names (by marking {@code ignoreBootConflict} to {@code true}) even
     * if the old "should" be deleted here. For service providers, increment the
     * {@code version()} to let update4j know it should select the new, even if the
     * old is deleted here.
     * 
     * <p>
     * A file in the old configuration is considered "old" if that file:
     * 
     * <ul>
     * <li>Exists.</li>
     * <li>Is not present in the current configuration. It will query the underlying
     * operating system to check equality instead of comparing path names.</li>
     * <li>If {@code matchChecksum} is {@code true} &mdash; if the file's checksum
     * matches the checksum listed in the old config. This is an extra - optional -
     * layer of safety to prevent unwanted files from being deleted.
     * </ul>
     * 
     * <p>
     * Files that are not marked with either {@code classpath} or {@code modulepath}
     * in the config, will be assumed to run in the bootstrap; therefore will not be
     * deleted immediately. Instead, they will be queued to be deleted when the JVM
     * shuts down by spawning a new system-dependent process with
     * {@code secondsDelay} seconds delay. Files that are marked with
     * {@code classpath} or {@code modulepath} will try to be deleted immediately.
     * If it fails (e.g. you called this method from the business application and
     * the files are locked by operating system), it will queue them together with
     * the bootstrap files.
     * 
     * <p>
     * Please note: Long running shutdown hooks may keep files locked thus
     * preventing them from being deleted. Increase the {@code secondsDelay} to
     * ensure it runs after all shutdown hooks completed. {@code secondsDelay} will
     * never be less than 1; smaller values will be adjusted.
     * 
     * <p>
     * You <em>must</em> not call this method if:
     * 
     * <pre>
     * this.requiresUpdate() == true
     * </pre>
     * 
     * in other words, you must first update the current config, or if using
     * {@code updateTemp()} you must first call {@link Update#finalizeUpdate(Path)}.
     * If you return {@code false} in
     * {@link UpdateHandler#shouldCheckForUpdate(FileMetadata)} for a particular
     * file, you cannot use this method out of the box. You can hand-modify the
     * config to strip those files by removing them with
     * {@link #generateXmlMapper()}. Consult the <em>Manual XML Manipulation</em>
     * section in this class JavaDoc.
     * 
     * 
     * @param oldConfig
     *            The old configuration.
     * @param matchChecksum
     *            Whether checksums should be checked and delete only if matching.
     * @param secondsDelay
     *            Second to delay deletion after JVM shut down. If less the 1, it
     *            will be adjusted to 1.
     * @throws IllegalStateException
     *             If this method is called but the current configuration is not
     *             up-to-date.
     * @throws IOException
     *             If checking if current config is up-to-date, checking file
     *             equality, or calculating checksum failed.
     */
    public void deleteOldFiles(Configuration oldConfig, boolean matchChecksum, int secondsDelay) throws IOException {
        if (requiresUpdate()) {
            throw new IllegalStateException("Current configuration is not up-to-date, refusing to delete.");
        }

        List<FileMetadata> oldFiles = getOldFiles(oldConfig, matchChecksum);
        if (oldFiles.isEmpty())
            return;

        List<Path> delayedDelete = new ArrayList<>();
        for (FileMetadata file : oldFiles) {
            if (file.isClasspath() || file.isModulepath()) {
                try {
                    Files.deleteIfExists(file.getPath());
                } catch (IOException e) {
                    e.printStackTrace();
                    delayedDelete.add(file.getPath());
                }
            } else {
                delayedDelete.add(file.getPath());
            }
        }

        if (!delayedDelete.isEmpty())
            FileUtils.delayedDelete(delayedDelete, secondsDelay);
    }

    /**
     * Returns a list of files of old files present in {@code oldConfig} but not in
     * the current.
     * 
     * 
     * <p>
     * A file in the old configuration is considered "old" if that file:
     * 
     * <ul>
     * <li>Exists.</li>
     * <li>Is not present in the current configuration. It will query the underlying
     * operating system to check equality instead of comparing path names.</li>
     * <li>If {@code matchChecksum} is {@code true} &mdash; if the file's checksum
     * matches the checksum listed in the old config.
     * </ul>
     * 
     * <p>
     * Old files are assumed safe to be removed with
     * {@link #deleteOldFiles(Configuration, boolean, int)}.
     * 
     * 
     * @param oldConfig
     *            The old configuration.
     * @param matchChecksum
     *            Whether checksums should be matching in order to consider it old.
     * @return A list of old files.
     * @throws IOException
     *             If checking file equality, or calculating checksum failed.
     */
    public List<FileMetadata> getOldFiles(Configuration oldConfig, boolean matchChecksum) throws IOException {
        List<FileMetadata> oldFiles = new ArrayList<>();

        outer: for (FileMetadata file : oldConfig.getFiles()) {
            if (!Files.exists(file.getPath())) {
                continue;
            }
            
            if(getFiles().stream().anyMatch(f -> (f.appliesToCurrentPlatform()) && f.getPath().equals(file.getPath()))) {
                continue;
            }

            // at this point this path isn't present in the new config, let's rule out symlinks
            for (FileMetadata newFile : getFiles()) {
                if (newFile.appliesToCurrentPlatform()) {
                    if (Files.isSameFile(newFile.getPath(), file.getPath())) {
                        continue outer;
                    }
                }
            }

            if (matchChecksum) {
                if (file.requiresUpdate())
                    continue;
            }

            oldFiles.add(file);
        }

        return oldFiles;
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
        return read(reader, (Map<String, String>) null);
    }

    /**
     * Reads and parses a configuration XML, and add the provided properties.
     * 
     * @param reader
     *            The {@code Reader} for reading the XML.
     * @param dynamicProperties
     *            Unlisted properties to override listed properties or to map
     *            unmapped placeholders.
     * @return A {@code Configuration} as parsed from the given XML.
     * @throws IOException
     *             Any exception that arises while reading.
     */
    public static Configuration read(Reader reader, Map<String, String> dynamicProperties) throws IOException {
        return doRead(reader, dynamicProperties);
    }

    /**
     * Reads and parses a configuration XML, then verifies the configuration
     * signature against the public key.
     * 
     * @param reader
     *            The {@code Reader} for reading the XML.
     * @param dynamicProperties
     *            Unlisted properties to override listed properties or to map
     *            unmapped placeholders.
     * @return A {@code
     * Configuration} as parsed from the given XML.
     * @throws IOException
     *             Any exception that arises while reading.
     * @throws SecurityException
     *             If the configuration does not have a signature, or if
     *             verification failed.
     */
    public static Configuration read(Reader reader, PublicKey key) throws IOException {
        return read(reader, key, null);
    }

    /**
     * Reads and parses a configuration XML and add more properties, then verifies
     * the configuration signature against the public key.
     * 
     * @param reader
     *            The {@code Reader} for reading the XML.
     * @param key
     *            The public key to verify the config's signature against.
     * @param dynamicProperties
     *            Unlisted properties to override listed properties or to map
     *            unmapped placeholders.
     * @return A {@code
     * Configuration} as parsed from the given XML.
     * @throws IOException
     *             Any exception that arises while reading.
     * @throws SecurityException
     *             If the configuration does not have a signature, or if
     *             verification failed.
     */
    public static Configuration read(Reader reader, PublicKey key, Map<String, String> dynamicProperties)
                    throws IOException {
        Configuration config = doRead(reader, dynamicProperties);
        config.verifyConfiguration(key);

        return config;
    }

    private static Configuration doRead(Reader reader, Map<String, String> dynamicProperties) throws IOException {
        ConfigMapper configMapper = ConfigMapper.read(reader);

        return parseNoCopy(configMapper, dynamicProperties);
    }

    /**
     * Parses a configuration from the given XML mapper.
     * 
     * @param mapper
     *            The mapper to parse.
     * @return A {@code Configuration} as parsed from the mapper.
     */

    public static Configuration parse(ConfigMapper mapper) {
        return parse(mapper, null);
    }

    /**
     * Parses a configuration from the given XML mapper, and add the provided
     * properties.
     * 
     * @param mapper
     *            The mapper to parse.
     * @param dynamicProperties
     *            Unlisted properties to override listed properties or to map
     *            unmapped placeholders.
     * @return A {@code Configuration} as parsed from the mapper.
     */

    public static Configuration parse(ConfigMapper mapper, Map<String, String> dynamicProperties) {
        return parseNoCopy(new ConfigMapper(mapper), dynamicProperties);
    }

    private static Configuration parseNoCopy(ConfigMapper mapper, Map<String, String> dynamicProperties) {
        PropertyManager manager = new PropertyManager(mapper.properties, dynamicProperties, null);

        return parseNoCopy(mapper, manager);
    }

    private static Configuration parseNoCopy(ConfigMapper configMapper, PropertyManager propertyManager) {
        Configuration config = new Configuration();
        config.propertyManager = propertyManager;

        if (configMapper.timestamp != null)
            config.timestamp = Instant.parse(configMapper.timestamp);

        config.signature = configMapper.signature;

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
            FileMetadata.Builder fileBuilder = FileMetadata.builder()
                            .baseUri(config.getBaseUri())
                            .basePath(config.getBasePath());

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
            if (fm.arch != null)
                fileBuilder.arch(fm.arch);

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
                // if any path is null (by referencing foreign property), ignore
                if ((prevFile.getPath() != null && file.getPath() != null)) {
                    // files do not have cascading os properties, so if
                    // at least one is null, OR both are non-null but same os
                    boolean osOverlap = prevFile.getOs() == null || file.getOs() == null
                                    || prevFile.getOs() == file.getOs();
                    // and files do not have cascading arch properties, so if
                    // at least one is null, OR both are non-null but same architecture
                    boolean archOverlap = prevFile.getArch() == null || file.getArch() == null
                                    || Objects.equals(prevFile.getArch(), file.getArch());
                    // and have same paths, throw exception
                    boolean pathOverlap = prevFile.getPath().equals(file.getPath());
                    if(osOverlap && archOverlap && pathOverlap) {
                        throw new IllegalStateException("2 files resolve to same 'path': " + file.getPath());
                    }
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
     * This method is intended to be used on the development/build machine only to
     * draft a new release when changes are made to files but it's still the same
     * files.
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
     * This method is intended to be used on the development/build machine only to
     * draft a new release when changes are made to files but it's still the same
     * files.
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
     * This method is intended to be used on the development/build machine only to
     * draft a new release when changes are made to files but it's still the same
     * files.
     * 
     * <p>
     * If you want to change the files you should generally use the Builder API. If
     * you want, you can manually add or remove files in the config file; for new
     * files, just put the {@code path} and call sync to automatically fill the
     * rest.
     * 
     * @param signer
     *            The {@link PrivateKey} to use for config and file signing.
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
     * This method is intended to be used on the development/build machine only to
     * draft a new release when changes are made to files but it's still the same
     * files.
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
     *            The {@link PrivateKey} to use for config and file signing.
     * @return A new {@code Configuration} with synced file metadata.
     * @throws IOException
     *             If any exception arises while reading file metadata
     */
    public Configuration sync(Path overrideBasePath, PrivateKey signer) throws IOException {
        ConfigMapper newMapper = generateXmlMapper();

        boolean changed = false;
        for (int i = 0; i < getFiles().size(); i++) {

            FileMetadata fm = getFiles().get(i);
            Path path;
            if (overrideBasePath == null || getBasePath().relativize(fm.getPath()).isAbsolute()) {
                path = fm.getPath();
            } else {
                path = overrideBasePath.resolve(getBasePath().relativize(fm.getPath()));
            }

            if (Files.notExists(path)) {
                logger.log(WARNING, "File '" + path + "' is missing; skipped.");
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
                logger.log(INFO, "Synced '" + path.getFileName() + "'.");
                changed = true;
            }

        }

        if (changed) {
            newMapper.timestamp = Instant.now().toString();
        }

        if (signer == null) {
            newMapper.signature = null;
        } else {
            newMapper.signature = newMapper.sign(signer);
        }

        return parseNoCopy(newMapper, propertyManager);
    }

    /**
     * Verifies this config against this public key and throws a
     * {@code SecurityException} if the config doesn't have a signature or if
     * verification failed.
     * 
     * <p>
     * This process does <em>not</em> check individual file signatures.
     * 
     * @param key
     *            The public key to check against.
     */
    public void verifyConfiguration(PublicKey key) {
        mapper.verifySignature(key);
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
     * Returns an XML string exactly as {@link #write(Writer)} would output.
     * 
     * @return An XML string exactly as {@link #write(Writer)} would output.
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
        if (getTimestamp() == null) {
            if (otherConfig.getTimestamp() != null)
                return false;
        } else {
            if (!getTimestamp().equals(otherConfig.getTimestamp())) {
                return false;
            }
        }

        return toString().equals(other.toString());
    }

    /**
     * The entry point to the Builder API.
     * 
     * <p>
     * This should <em>only</em> be used on the development/build machine when
     * drafting a new release. It should not be used to load a config on the client
     * side.
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
     * placeholder values that may refer to dynamic properties, listed properties,
     * system properties or system environment variables.
     * 
     * <p>
     * To refer to a property {@code my.prop} with the value {@code Hello}:
     * 
     * <pre>
     * "${my.prop} World!" -> "Hello World"
     * </pre>
     * 
     * <p>
     * Or, on Windows:
     * 
     * <pre>
     * "${LOCALAPPDATA}/My App" -> "C:/Users/&lt;user-name&gt;/AppData/Local/My App"
     * </pre>
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
        private Map<String, String> dynamicProperties;

        private PrivateKey signer;
        private PlaceholderMatchType matcher;

        private Builder() {
            files = new ArrayList<>();
            properties = new ArrayList<>();
            systemProperties = new ArrayList<>();
            dynamicProperties = new HashMap<>();

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
            return baseUri(uri == null ? null : uri.toString());

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
            return basePath(path == null ? null : path.toString());

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
         * Set the private key to use for configuration and file signing. If not set,
         * they will not be signed.
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
         * Convenience method to load the private key from a Java Keystore at the given
         * path with the given keypair alias, using the keystore and alias passwords.
         * Once loaded, it will forward the private key to {@link #signer(PrivateKey)}.
         * 
         * <p>
         * It wraps all checked exceptions in a {@code RuntimeException} to keep the
         * chaining clean.
         * 
         * @param path
         *            The location of the keystore.
         * @param keystorePass
         *            The password of the keystore.
         * @param alias
         *            The alias of the keypair.
         * @param aliasPass
         *            The alias password, or {@code null}.
         * @return The builder for chaining.
         */
        public Builder signer(Path path, char[] keystorePass, String alias, char[] aliasPass) {
            try (InputStream in = Files.newInputStream(path)) {
                KeyStore jks = KeyStore.getInstance("JKS");
                jks.load(in, keystorePass);

                PrivateKey key = (PrivateKey) jks.getKey(alias, aliasPass);
                return signer(key);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Convenience method to load the private key from a Java Keystore at the given
         * path string with the given keypair alias, using the keystore and alias
         * passwords. Once loaded, it will forward the private key to
         * {@link #signer(PrivateKey)}.
         * 
         * <p>
         * This method is equivalent to calling:
         * 
         * <pre>
         * signer(Paths.get(path), keystorePass, alias, aliasPass);
         * </pre>
         * 
         * <p>
         * It wraps all checked exceptions in a {@code RuntimeException} to keep the
         * chaining clean.
         * 
         * @param path
         *            The location of the keystore.
         * @param keystorePass
         *            The password of the keystore.
         * @param alias
         *            The alias of the keypair.
         * @param aliasPass
         *            The alias password, or {@code null}.
         * @return The builder for chaining.
         */
        public Builder signer(String path, char[] keystorePass, String alias, char[] aliasPass) {
            return signer(Paths.get(path), keystorePass, alias, aliasPass);
        }

        /**
         * Convenience method to load the private key from the Java Keystore at the
         * default keystore location (<code>${user.home}/.keystore</code>) with the
         * given keypair alias, using the keystore and alias passwords. Once loaded, it
         * will forward the private key to {@link #signer(PrivateKey)}.
         * 
         * <p>
         * It wraps all checked exceptions in a {@code RuntimeException} to keep the
         * chaining clean.
         * 
         * @param keystorePass
         *            The password of the keystore.
         * @param alias
         *            The alias of the keypair.
         * @param aliasPass
         *            The alias password, or {@code null}.
         * @return The builder for chaining.
         */
        public Builder signer(char[] keystorePass, String alias, char[] aliasPass) {
            return signer(Paths.get(System.getProperty("user.home"), ".keystore"), keystorePass, alias, aliasPass);
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
         * The value may contain placeholders.
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
         * Changes affects the actual list.
         * 
         * @return All properties listed via {@code property()} or {@code properties()}.
         */
        public List<Property> getProperties() {
            return properties;
        }

        /**
         * Register a <em>dynamic</em> property to the builder. A dynamic property
         * doesn't get listed in the config, but will replace unmapped placeholders when
         * the config is built. The {@link Configuration#read(Reader, Map)} and
         * {@link Configuration#parse(ConfigMapper, Map)} can be used on the client side
         * to map those properties.
         * 
         * <p>
         * A dynamic property has higher precedence than a listed property, thus can be
         * used to override the value of listed properties.
         * 
         * <p>
         * The value may contain placeholders.
         * 
         * <p>
         * This method can be called repeatedly, it will add them all to a single map.
         * The key or value must not be {@code null}.
         * 
         * 
         * @param key
         *            The key of the dynamic property.
         * @param value
         *            The value of the dynamic property.
         * @return The builder for chaining.
         */
        public Builder dynamicProperty(String key, String value) {
            dynamicProperties.put(key, value);

            return this;
        }

        /**
         * Register a map of <em>dynamic</em> properties to the builder. A dynamic
         * property doesn't get listed in the config, but will replace unmapped
         * placeholders when the config is built. The
         * {@link Configuration#read(Reader, Map)} and
         * {@link Configuration#parse(ConfigMapper, Map)} can be used on the client side
         * to map those properties.
         * 
         * <p>
         * A dynamic property has higher precedence than a listed property, thus can be
         * used to override the value of listed properties.
         * 
         * <p>
         * The values may contain placeholders.
         * 
         * <p>
         * This method can be called repeatedly, it will add them all to a single map.
         * The key or value must not be {@code null}.
         * 
         * 
         * @param dynamics
         *            A map of dynamic properties.
         * @return The builder for chaining.
         */
        public Builder dynamicProperties(Map<String, String> dynamics) {
            dynamicProperties.putAll(dynamics);

            return this;
        }

        /**
         * Returns the map that collects the dynamic properties. Changes will affect the
         * actual map.
         * 
         * @return The map of collected dynamic properties.
         */
        public Map<String, String> getDynamicProperties() {
            return dynamicProperties;
        }

        /**
         * Hint the builder to replace the value of the given system property if a
         * proper match is found.
         * 
         * <p>
         * If the system property is referenced as a placeholder anywhere else in the
         * builder, this is not needed.
         * 
         * @param str
         *            The system property key.
         * @return The builder for chaining.
         */
        public Builder resolveSystemProperty(String str) {
            systemProperties.add(str);

            return this;
        }

        /**
         * Hint the builder to replace the value of the given system properties if a
         * proper match is found.
         * 
         * <p>
         * If these system properties are referenced as placeholders anywhere else in
         * the builder, this is not needed.
         * 
         * @param p
         *            A collection of system property keys.
         * @return The builder for chaining.
         */
        public Builder resolveSystemProperties(Collection<String> p) {
            systemProperties.addAll(p);

            return this;
        }

        /**
         * Returns the listed system property keys to hint the builder to look for a
         * string that could be matched with the system property's value.
         * 
         * <p>
         * This starts by containing the keys {@code user.home} and {@code user.dir},
         * you could remove them here to prevent from replacing those strings. Changes
         * will affect the actual list.
         * 
         * @return The list of system property keys to resolve for matching.
         */
        public List<String> getSystemPropertiesToResolve() {
            return systemProperties;
        }

        /**
         * List the given class as the update handler when
         * {@link Configuration#update()} is called over this config.
         * 
         * <p>
         * When explicitly listing a class in the config, it will not load the highest
         * version of the update handler. It also relieves you from having to advertise
         * them as required by the {@link ServiceLoader} class. Still, for modules you
         * would want to add the {@code provides} directive, since this would add the
         * module in the module graph and make the class visible to this framework.
         * 
         * @param clazz
         *            The update handler class name.
         * 
         * @return The builder for chaining.
         */
        public Builder updateHandler(Class<? extends UpdateHandler> clazz) {
            return updateHandler(clazz.getCanonicalName());
        }

        /**
         * List the given class as the update handler when
         * {@link Configuration#update()} is called over this config.
         * 
         * <p>
         * When explicitly listing a class in the config, it will not load the highest
         * version of the update handler. It also relieves you from having to advertise
         * them as required by the {@link ServiceLoader} class. Still, for modules you
         * would want to add the {@code provides} directive, since this would add the
         * module in the module graph and make the class visible to this framework.
         * 
         * <p>
         * This value may contain placeholders.
         * 
         * @param clazz
         *            The update handler class name.
         * 
         * @return The builder for chaining.
         */
        public Builder updateHandler(String className) {
            this.updateHandler = className;

            return this;
        }

        /**
         * Returns the class name passed in {@link #updateHandler(String)}.
         * 
         * @return The class name passed in {@link #updateHandler(String)}.
         */
        public String getUpdateHandler() {
            return updateHandler;
        }

        /**
         * List the given class as the launcher when {@link Configuration#launch()} is
         * called over this config.
         * 
         * <p>
         * When explicitly listing a class in the config, it will not load the highest
         * version of the launcher. It also relieves you from having to advertise them
         * as required by the {@link ServiceLoader} class. Still, for modules you would
         * want to add the {@code provides} directive, since this would add the module
         * in the module graph and make the class visible to this framework.
         * 
         * @param clazz
         *            The update handler class name.
         * 
         * @return The builder for chaining.
         */
        public Builder launcher(Class<? extends Launcher> clazz) {
            return launcher(clazz.getCanonicalName());
        }

        /**
         * List the given class as the launcher when {@link Configuration#launch()} is
         * called over this config.
         * 
         * <p>
         * When explicitly listing a class in the config, it will not load the highest
         * version of the launcher. It also relieves you from having to advertise them
         * as required by the {@link ServiceLoader} class. Still, for modules you would
         * want to add the {@code provides} directive, since this would add the module
         * in the module graph and make the class visible to this framework.
         * 
         * <p>
         * This value may contain placeholders.
         * 
         * @param clazz
         *            The update handler class name.
         * 
         * @return The builder for chaining.
         */
        public Builder launcher(String className) {
            this.launcher = className;

            return this;
        }

        /**
         * Returns the class name passed in {@link #updateHandler(String)}.
         * 
         * @return The class name passed in {@link #updateHandler(String)}.
         */
        public String getLauncher() {
            return launcher;
        }

        /**
         * Attempt to replace strings with listed or system property placeholders
         * according to the given policy. By default, or if you use {@code null}, it
         * will use {@link PlaceholderMatchType#WHOLE_WORD}.
         * 
         * @param matcher
         *            The match type to be used when implying placeholders.
         * @return The builder for chaining.
         */
        public Builder matchAndReplace(PlaceholderMatchType matcher) {
            this.matcher = matcher;

            return this;
        }

        /**
         * Returns the policy passed in {@link #matchAndReplace(PlaceholderMatchType)}.
         * It will never return {@code null} but instead
         * {@link PlaceholderMatchType#WHOLE_WORD}.
         * 
         * @return The match policy.
         */
        public PlaceholderMatchType getMatchType() {
            return matcher == null ? PlaceholderMatchType.WHOLE_WORD : matcher;
        }

        /**
         * Collects all information passed to the builder, replaces matches with
         * placeholder according to the {@link #getMatchType()} policy and validates all
         * values.
         * 
         * @return A built Configuration according to the passed information.
         */
        public Configuration build() {
            PlaceholderMatchType matcher = getMatchType();

            for (Map.Entry<String, String> e : dynamicProperties.entrySet()) {
                Objects.requireNonNull(e.getKey());
                Objects.requireNonNull(e.getValue());
            }

            ConfigMapper mapper = new ConfigMapper();
            PropertyManager pm = new PropertyManager(properties, dynamicProperties, systemProperties);

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

            if (getSigner() != null) {
                mapper.signature = mapper.sign(getSigner());
            }

            return Configuration.parseNoCopy(mapper, pm);
        }

    }
}
