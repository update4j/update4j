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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.update4j.service.Delegate;
import org.update4j.service.Service;

/**
 * This class consists of convenience methods and the module's main method to
 * locate and start the bootstrap application in <a href=
 * "https://github.com/update4j/update4j/wiki/Documentation#dealing-with-providers">Delegate
 * Mode</a>.
 * 
 * @author Mordechai Meisels
 *
 */
public class Bootstrap {

	/**
	 * The version of the current build of the framework.
	 */
	public static final String VERSION = "1.3.3";

	/**
	 * The main method to start the bootstrap application in Delegate Mode from
	 * command-line.
	 * 
	 * <p>
	 * Here's how you should run the application as a module:
	 * 
	 * <pre>
	 * $ java --module-path . --module org.update4j
	 * </pre>
	 * 
	 * Or in shorthand:
	 * 
	 * <pre>
	 * $ java -p . -m org.update4j
	 * </pre>
	 *
	 * For more info consult <a
	 * href=https://github.com/update4j/update4j/wiki/Documentation#starting-the-application>Starting
	 * the Application</a>
	 * 
	 * <p>
	 * By default it will try to locate the highest versioned provider of
	 * {@link Delegate} (specified by {@link Service#version()}) currently present
	 * in the classpath or modulepath. You may use an explicit class by passing the
	 * class name using the {@code --delegate} flag as the first option:
	 * 
	 * <pre>
	 * $ java --module-path . --module org.update4j --delegate com.example.MyDelegate
	 * </pre>
	 * 
	 * Both {@code --delegate} and class name that follows will be removed from the
	 * argument list passed over to the delegate.
	 * 
	 * <p>
	 * The class name should be the <i>Canonical Class Name</i> i.e. the String
	 * returned when calling {@link Class#getCanonicalName()}. If the system cannot
	 * locate the passed class it fails.
	 * 
	 */
	public static void main(String[] args) throws Throwable {
		String classname = null;
		List<String> argsList = List.of(args);

		if (args.length > 0) {

			String firstArg = args[0].trim();
			if (firstArg.equals("--delegate")) {
				if (args.length == 1) {
					throw new IllegalArgumentException("Missing class name for delegate option.");
				}

				classname = args[1].trim();
				argsList = argsList.subList(2, argsList.size());
			} else if (firstArg.matches("--delegate(?:\\s*=\\s*|\\s+)")) {
				Pattern pattern = Pattern.compile("--delegate(?:\\s*=\\s*|\\s+)(.+)");
				Matcher match = pattern.matcher(firstArg);

				if (!match.find()) {
					throw new IllegalArgumentException("Missing class name for delegate option.");
				}

				classname = match.group(1);
				argsList = argsList.subList(1, argsList.size());
			}

		}

		start(classname, argsList);
	}

	/**
	 * Starts the bootstrap by locating the highest versioned provider of
	 * {@link Delegate} (specified by {@link Service#version()}) currently present
	 * in the classpath or modulepath.
	 * 
	 * @throws Throwable
	 *             Any throwable thrown in the bootstrap.
	 */
	public static void start() throws Throwable {
		start((String) null);
	}

	/**
	 * Starts the bootstrap by locating the given class.
	 * 
	 * @throws Throwable
	 *             Any throwable thrown in the bootstrap.
	 */
	public static void start(String classname) throws Throwable {
		start(classname, List.of());
	}

	/**
	 * Starts the bootstrap running the given {@link Delegate}.
	 * 
	 * @throws Throwable
	 *             Any throwable thrown in the bootstrap.
	 */
	public static void start(Delegate delegate) throws Throwable {
		start(delegate, List.of());
	}

	/**
	 * Starts the bootstrap by locating the highest versioned provider of
	 * {@link Delegate} (specified by {@link Service#version()}) currently present
	 * in the classpath or modulepath, with the given list as command-line
	 * arguments.
	 * 
	 * @throws Throwable
	 *             Any throwable thrown in the bootstrap.
	 */
	public static void start(List<String> args) throws Throwable {
		start((String) null, args);
	}

	/**
	 * Starts the bootstrap by locating the given class, with the given list as
	 * command-line arguments.
	 * 
	 * @throws Throwable
	 *             Any throwable thrown in the bootstrap.
	 */
	public static void start(String classname, List<String> args) throws Throwable {
		start(Service.loadService(Delegate.class, classname), args);
	}

	/**
	 * Starts the bootstrap running the given {@link Delegate}, with the given list
	 * as command-line arguments.
	 * 
	 * @throws Throwable
	 *             Any throwable thrown in the bootstrap.
	 */
	public static void start(Delegate delegate, List<String> args) throws Throwable {
		delegate.main(args);
	}
}