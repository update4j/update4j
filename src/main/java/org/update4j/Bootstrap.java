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
import java.util.Map;
import org.update4j.service.Delegate;
import org.update4j.service.Service;
import org.update4j.util.ArgUtils;

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
	public static final String VERSION = "1.3.0";

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
	 * in the classpath or modulepath. You may override this behavior by passing the
	 * delegate class name using the {@code --delegate} option:
	 * 
	 * <pre>
	 * $ java --module-path . --module org.update4j --delegate=com.example.MyDelegate
	 * </pre>
	 * 
	 * The class name should be the <i>Canonical Class Name</i> i.e. the String
	 * returned when calling {@link Class#getCanonicalName()}.
	 * 
	 * 
	 * <p>
	 * If the system cannot locate the passed class it will fall back to the
	 * default, i.e. the highest version.
	 * 
	 */
	public static void main(String[] args) throws Throwable {
		String override = null;
		
		List<String> argsList = List.of(args);
		List<String> bootArgs = ArgUtils.beforeSeparator(argsList);
		Map<String, String> parsed = ArgUtils.parseArgs(bootArgs);
		
		for (Map.Entry<String, String> e : parsed.entrySet()) {

			if("delegate".equals(e.getKey())) {
				ArgUtils.validateHasValue(e);
				override = e.getValue();
				
				break;
			}

		}

		start(override, argsList);
	}

	/**
	 * Starts the bootstrap by locating the highest versioned provider of
	 * {@link Delegate} (specified by {@link Service#version()}) currently present
	 * in the classpath or modulepath.
	 * 
	 * @throws Throwable Any throwable thrown in the bootstrap.
	 */
	public static void start() throws Throwable {
		start((String) null);
	}

	/**
	 * Starts the bootstrap by locating the class between the list of advertised
	 * providers of {@link Delegate} currently present in the classpath or
	 * modulepath.
	 * 
	 * <p>
	 * If the system cannot locate any registered provider with the given name, the
	 * highest versioned provider (specified by {@link Service#version()}) will be
	 * used instead.
	 * 
	 * @throws Throwable Any throwable thrown in the bootstrap.
	 */
	public static void start(String override) throws Throwable {
		start(override, List.of());
	}

	/**
	 * Starts the bootstrap running the given {@link Delegate}.
	 * 
	 * @throws Throwable Any throwable thrown in the bootstrap.
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
	 * @throws Throwable Any throwable thrown in the bootstrap.
	 */
	public static void start(List<String> args) throws Throwable {
		start((String) null, args);
	}

	/**
	 * Starts the bootstrap by locating the class between the list of advertised
	 * providers of {@link Delegate} currently present in the classpath or
	 * modulepath, with the given list as command-line arguments.
	 * 
	 * <p>
	 * If the system cannot locate any registered provider with the given name, the
	 * highest versioned provider (specified by {@link Service#version()}) will be
	 * used instead.
	 * 
	 * @throws Throwable Any throwable thrown in the bootstrap.
	 */
	public static void start(String override, List<String> args) throws Throwable {
		start(Service.loadService(Delegate.class, override), args);
	}

	/**
	 * Starts the bootstrap running the given {@link Delegate}, with the given list
	 * as command-line arguments.
	 * 
	 * @throws Throwable Any throwable thrown in the bootstrap.
	 */
	public static void start(Delegate delegate, List<String> args) throws Throwable {
		delegate.main(args);
	}
}