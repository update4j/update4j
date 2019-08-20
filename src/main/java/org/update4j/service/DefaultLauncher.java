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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.update4j.Configuration;
import org.update4j.LaunchContext;
import org.update4j.inject.InjectTarget;
import org.update4j.util.StringUtils;

public class DefaultLauncher implements Launcher {

	public static final String DOMAIN_PREFIX = "default.launcher";
	public static final String MAIN_CLASS_PROPERTY_KEY = DOMAIN_PREFIX + ".main.class";
	public static final String ARGUMENT_PROPERTY_KEY_PREFIX = DOMAIN_PREFIX + ".argument";
	public static final String SYSTEM_PROPERTY_KEY_PREFIX = DOMAIN_PREFIX + ".system";

	@InjectTarget(required = false)
	private List<String> args;

	@Override
	public long version() {
		return Long.MIN_VALUE;
	}

	public DefaultLauncher() {

	}

	public DefaultLauncher(List<String> args) {
		this.args = args;
	}

	@Override
	public void run(LaunchContext context) {
		Configuration config = context.getConfiguration();

		String mainClass = config.getResolvedProperty(MAIN_CLASS_PROPERTY_KEY);
		if (mainClass == null) {
			usage();

			throw new IllegalStateException("No main class property found at key '" + MAIN_CLASS_PROPERTY_KEY + "'.");
		}

		if (!StringUtils.isClassName(mainClass)) {
			throw new IllegalStateException(
							"Main class at key '" + MAIN_CLASS_PROPERTY_KEY + "' is not a valid Java class name.");
		}

		List<String> localArgs = new ArrayList<>();
		if (this.args != null)
			localArgs.addAll(this.args);

		// use TreeMap to sort by key
		Map<Integer, String> argMap = new TreeMap<>();
		context.getConfiguration().getResolvedProperties().forEach((k,v) -> {
			String pfx = ARGUMENT_PROPERTY_KEY_PREFIX + ".";
			// starts with but not equals, to filter missing <num> part
			if (k.startsWith(pfx) && !k.equals(pfx)) {
				int num = Integer.parseInt(k.substring(pfx.length()));
				argMap.put(num, v);
			}
		});
		
		localArgs.addAll(argMap.values());
		String[] argsArray = localArgs.toArray(new String[localArgs.size()]);

		context.getConfiguration().getResolvedProperties().forEach((k,v) -> {
			String pfx = SYSTEM_PROPERTY_KEY_PREFIX + ".";
			if (k.startsWith(pfx) && !k.equals(pfx)) {
				System.setProperty(k.substring(pfx.length()), v);
			}
		});

		// we are fully aware, so no need to warn
		// if NoClassDefFoundError arises for any other reason
		System.setProperty("update4j.suppress.warning.access", "true");

		try {
			Class<?> clazz = Class.forName(mainClass, true, context.getClassLoader());

			// first check for JavaFx start method
			Class<?> javafx = null;
			try {
				javafx = Class.forName("javafx.application.Application", true, context.getClassLoader());
			} catch (ClassNotFoundException e) {
				// no JavaFx present, skip.
			}

			if (javafx != null && javafx.isAssignableFrom(clazz)) {
				Method launch = javafx.getMethod("launch", Class.class, String[].class);
				launch.invoke(null, clazz, argsArray);
			} else {
				Method main = clazz.getMethod("main", String[].class);
				main.invoke(null, new Object[] { argsArray });
			}

		} catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException
						| NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
	}

	// @formatter:off
	private static void usage() {
		System.err.println("Customize the setup of the default launcher by setting properties in the config\n"
						+ "\taccording to the following table:\n\n" + table()

						+ "\tWhile the default behavior works for a majority of cases, you may even\n"
						+ "\tfurther customize the launch process by implementing your own Launcher\n"
						+ "\tand either register it as a service provider, or pass an instance directly\n"
						+ "\tto a call to Configuration.launch(). This allows you to leverage the dependency\n"
						+ "\tinjection feature by calling any overload of Configuration.launch() that accepts\n"
						+ "\tan Injectable.\n\n"
						+ "\tFor more details how to register service providers please refer to the Github wiki:\n"
						+ "\thttps://github.com/update4j/update4j/wiki/Documentation#dealing-with-providers\n");
	}

	private static String table() {
		return "\t\t+--------------------------------+------------------------------------+\n"
			 + "\t\t| default.launcher.main.class    | The main class of the business app |\n"
			 + "\t\t|                                | having a main method or subclassing|\n"
			 + "\t\t|                                | javafx.application.Application     |\n"
			 + "\t\t|                                |                                    |\n"
			 + "\t\t|                                | Required.                          |\n"
			 + "\t\t+--------------------------------+------------------------------------+\n"
			 + "\t\t| default.launcher.argument.<num>| Pass values in the args list,      |\n"
			 + "\t\t|                                | ordered by <num>. It will throw a  |\n"
			 + "\t\t|                                | NumberFormatException if <num> is  |\n"
			 + "\t\t|                                | not a valid integer.               |\n"
			 + "\t\t|                                |                                    |\n"
			 + "\t\t|                                | Arguments passed from the bootstrap|\n"
			 + "\t\t|                                | are always first in the list       |\n"
			 + "\t\t|                                | followed by these property values. |\n"
			 + "\t\t+--------------------------------+------------------------------------+\n"
			 + "\t\t| default.launcher.system.<key>  | Pass system properties with the    |\n"
			 + "\t\t|                                | provided values using the <key> as |\n"
			 + "\t\t|                                | the system property key.           |\n"
			 + "\t\t+--------------------------------+------------------------------------+\n\n";
	}

}
