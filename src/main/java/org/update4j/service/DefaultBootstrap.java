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
package org.update4j.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.update4j.Configuration;
import org.update4j.SingleInstanceManager;
import org.update4j.Update;
import org.update4j.Update4j;

public class DefaultBootstrap implements Delegate {

	private String remote;
	private String local;
	private String cert;

	private boolean syncLocal;
	private boolean launchFirst;
	private boolean stopOnUpdateError;
	private boolean singleInstance;

	private static final String PATTERN = "(?:\\s*=)?\\s*(.+)";

	@Override
	public long version() {
		return Long.MIN_VALUE;
	}

	@Override
	public void main(List<String> args) throws Throwable {
		if (args.isEmpty()) {
			welcome();
			return;
		}

		for (String arg : args) {
			arg = arg.trim();

			// let's try first those who don't need regex for performance
			if (arg.equals("--syncLocal")) {
				validateNotSet(syncLocal, "syncLocal");
				syncLocal = true;
				continue;
			} else if (arg.equals("--launchFirst")) {
				validateNotSet(launchFirst, "launchFirst");
				launchFirst = true;
				continue;
			} else if (arg.equals("--stopOnUpdateError")) {
				validateNotSet(stopOnUpdateError, "stopOnUpdateError");
				stopOnUpdateError = true;
				continue;
			} else if (arg.equals("--singleInstance")) {
				validateNotSet(singleInstance, "singleInstance");
				singleInstance = true;
				continue;
			}

			Matcher m = Pattern.compile("--remote" + PATTERN).matcher(arg);
			if (m.matches()) {
				validateNotSet(remote, "remote");
				remote = m.group(1);
				continue;
			}
			m = Pattern.compile("--local" + PATTERN).matcher(arg);
			if (m.matches()) {
				validateNotSet(local, "local");
				local = m.group(1);
				continue;
			}
			m = Pattern.compile("--cert" + PATTERN).matcher(arg);
			if (m.matches()) {
				validateNotSet(cert, "cert");
				cert = m.group(1);
				continue;
			}
		}

		if (remote == null && local == null) {
			throw new IllegalArgumentException("One of --remote or --local must be supplied.");
		}

		if (launchFirst && local == null) {
			throw new IllegalArgumentException("--launchFirst requires a local configuration.");
		}

		if (syncLocal && remote == null) {
			throw new IllegalArgumentException("--syncLocal requires a remote configuration.");
		}

		if (syncLocal && local == null) {
			throw new IllegalArgumentException("--syncLocal requires a local configuration.");
		}

		if (singleInstance) {
			SingleInstanceManager.execute();
		}

		if (launchFirst) {
			launchFirst(args);
		} else {
			updateFirst(args);
		}
	}

	private void validateNotSet(boolean val, String command) {
		if (val)
			throw new IllegalArgumentException("Duplicate --" + command + " command.");
	}

	private void validateNotSet(String val, String command) {
		if (val != null)
			throw new IllegalArgumentException("Duplicate --" + command + " command.");
	}

	private void updateFirst(List<String> args) throws Throwable {
		Configuration remoteConfig = null;
		Configuration localConfig = null;

		if (remote != null) {
			try (Reader in = new InputStreamReader(new URL(remote).openStream())) {
				remoteConfig = Configuration.read(in);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (local != null) {
			try (Reader in = Files.newBufferedReader(Paths.get(local))) {
				localConfig = Configuration.read(in);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (remoteConfig == null && localConfig == null) {
			return;
		}

		if (syncLocal) {
			if (remoteConfig != null && !remoteConfig.equals(localConfig))
				try (Writer out = Files.newBufferedWriter(Paths.get(local))) {
					remoteConfig.write(out);
				} catch (IOException e) {
					e.printStackTrace();
				}
		}

		Configuration config = remoteConfig != null ? remoteConfig : localConfig;

		if (config.requiresUpdate()) {
			PublicKey pk = null;

			if (cert != null) {
				CertificateFactory cf = CertificateFactory.getInstance("X.509");
				try (InputStream in = Files.newInputStream(Paths.get(cert))) {
					pk = cf.generateCertificate(in).getPublicKey();
				}
			}

			boolean success = config.update(pk);
			if (!success && stopOnUpdateError) {
				return;
			}
		}

		config.launch(args);
	}

	private void launchFirst(List<String> args) throws Throwable {
		Path tempDir = Paths.get("update");
		if (Update.containsUpdate(tempDir)) {
			Update.finalizeUpdate(tempDir);
		}

		Configuration localConfig = null;
		try (Reader in = Files.newBufferedReader(Paths.get(local))) {
			localConfig = Configuration.read(in);
		} catch (Exception e) {
			e.printStackTrace();
		}

		boolean localNotReady = localConfig == null || localConfig.requiresUpdate();

		if (!localNotReady) {
			Configuration finalConfig = localConfig;
			Thread localApp = new Thread(() -> finalConfig.launch(args));
			localApp.run();
		}

		Configuration remoteConfig = null;
		if (remote != null) {
			try (Reader in = new InputStreamReader(new URL(remote).openStream())) {
				remoteConfig = Configuration.read(in);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (remoteConfig != null && !remoteConfig.equals(localConfig)) {
			try (Writer out = Files.newBufferedWriter(Paths.get(local))) {
				remoteConfig.write(out);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		if (localNotReady) {
			Configuration config = remoteConfig != null ? remoteConfig : localConfig;

			if (config != null) {
				PublicKey pk = null;

				if (cert != null) {
					CertificateFactory cf = CertificateFactory.getInstance("X.509");
					try (InputStream in = Files.newInputStream(Paths.get(cert))) {
						pk = cf.generateCertificate(in).getPublicKey();
					}
				}

				boolean success = config.update(pk);
				if (!success && stopOnUpdateError) {
					return;
				}

				config.launch(args);
			}
		} else if (remoteConfig != null) {
			if (remoteConfig.requiresUpdate()) {
				PublicKey pk = null;

				if (cert != null) {
					CertificateFactory cf = CertificateFactory.getInstance("X.509");
					try (InputStream in = Files.newInputStream(Paths.get(cert))) {
						pk = cf.generateCertificate(in).getPublicKey();
					}
				}

				remoteConfig.updateTemp(tempDir, pk);
			}
		}

	}

	// @formatter:off 
	private static void welcome() {

		System.out.println(getLogo() + "\tWelcome to the update4j framework.\n\n"
						+ "\tYou started the framework with its default settings, which does\n"
						+ "\tthe update and launch logic for you without complex setup. All you need is to\n"
						+ "\tspecify some settings via command line arguments.\n\n"
						+ "\tBefore you start, you first need to create a \"configuration\" file that contains\n"
						+ "\tall details required to run. You can create one by using Configuration.builder()\n"
						+ "\tBuilder API. You can sync an existing configuration when files are changed\n"
						+ "\tusing one of the Configuration.sync() methods.\n\n"
						+ "\tFor more details how to create a configuration please refer to the Javadoc:\n"
						+ "\thttp://docs.update4j.org/javadoc/update4j/org/update4j/Configuration.html\n\n"
						+ "\tWhile the default bootstrap works perfectly for a majority of cases, you might\n"
						+ "\tfurther customize the update and launch life-cycle to the last detail by\n"
						+ "\tusing the Configuration.update() and Configuration.launch() methods.\n\n"
						+ "\tIf you choose to implement your own bootstrap, there are 2 ways to do it:\n\n"
						+ "\t\t- Standard Mode: Start the bootstrap application using your own main method.\n"
						+ "\t\t  You will not be able to update the bootstrap application (as code cannot update itself),\n"
						+ "\t\t  only the business application will be updatable.\n\n"
						+ "\t\t- Delegate Mode: Move your main method into an implementation of Delegate\n"
						+ "\t\t  and start the framework just as you did now, i.e. calling \"update4j's\" main method.\n"
						+ "\t\t  This allows you to update the bootstrap application by releasing a newer version\n"
						+ "\t\t  with a higher version() number and make it visible to the JVM boot classpath\n"
						+ "\t\t  or modulepath by placing it in the right directory.\n"
						+ "\t\t  It it recommended not to use this feature before you can get everything\n"
						+ "\t\t  to run smoothly in Standard Mode, as this adds an extra layer of complexity.\n\n"
						+ "\tFor more details about implementing the bootstrap, please refer to the Github wiki:\n"
						+ "\thttps://github.com/update4j/update4j/wiki/Documentation#lifecycle\n"
						+ "\tFor more details how to register service providers please refer to the Github wiki:\n"
						+ "\thttps://github.com/update4j/update4j/wiki/Documentation#dealing-with-providers\n\n");

		usage();
	}

	private static String getLogo() {

		
		return
						
		   "\n"
		 + "\t                 _       _          ___ _ \n"
		 + "\t                | |     | |        /   (_)\n"
		 + "\t _   _ _ __   __| | __ _| |_ ___  / /| |_ \n"
		 + "\t| | | | '_ \\ / _` |/ _` | __/ _ \\/ /_| | |\n"
		 + "\t| |_| | |_) | (_| | (_| | ||  __/\\___  | |\n"
	     +"\t \\__,_| .__/ \\__,_|\\__,_|\\__\\___|    |_/ |\n"
		 + "\t      | |                             _/ |\n"
		 + "\t      |_|                            |__/ \n\n\n"

		;

	}

	private static void usage() {

		System.err.println("To start in modulepath:\n\n" 
						+ "\tjava -p update4j-" + Update4j.version() + ".jar -m org.update4j [commands...]\n"
						+ "\tjava -p . -m org.update4j [commands...]\n\n"
						+ "To start in classpath:\n\n" 
						+ "\tjava -jar update4j-" + Update4j.version() + ".jar [commands...]\n"
						+ "\tjava -cp update4j-" + Update4j.version() + ".jar org.update4j.Bootstrap [commands...]\n"
						+ "\tjava -cp * org.update4j.Bootstrap [commands...]\n\n" 
						+ "Available commands:\n\n"
						+ "\t--remote=[url] - The remote (or if using file:/// scheme - local) location of the\n"
						+ "\t\tconfiguration file. If it fails to download or command is missing, it will\n"
						+ "\t\tfall back to local.\n\n"
						+ "\t--local=[path] - The path of a local configuration to use if the remote failed to download\n"
						+ "\t\tor was not passed. If both remote and local fail, startup fails.\n\n"
						+ "\t--syncLocal - Sync the local configuration with the remote if it downloaded successfully.\n"
						+ "\t\tUseful to still allow launching without Internet connection. Default will not sync unless\n"
						+ "\t\t--launchFirst was specified.\n\n"
						+ "\t--cert=[path] - A path to an X.509 certificate file to use to verify signatures. If missing,\n"
						+ "\t\tno signature verification will be performed.\n\n"
						+ "\t--launchFirst - If specified, it will first launch the local application then silently\n"
						+ "\t\tdownload the update; the update will be available only on next restart. It will still\n"
						+ "\t\tdownload the remote and update first if the local config requires an update\n"
						+ "\t\t(e.g. files were deleted). Must have a local configuration.\n"
						+ "\t\tIf not specified it will update before launch and hang the application until done.\n\n"
						+ "\t--stopOnUpdateError - Will stop the launch if an error occurred while downloading an update.\n"
						+ "\t\tThis does not include if remote failed to download and it used local as a fallback.\n"
						+ "\t\tIf --launchFirst was used, this only applies if the local config requires an update\n"
						+ "\t\tand failed.\n\n"
						+ "\t--singleInstance - Run the application as a single instance. Any subsequent attempts\n"
						+ "\t\tto run will just exit. You can better control this feature by directly using the\n"
						+ "\t\tSingleInstanceManager class.\n");
		
	}
}
