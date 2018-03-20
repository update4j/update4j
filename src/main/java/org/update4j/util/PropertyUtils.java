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
package org.update4j.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.update4j.OS;
import org.update4j.Property;

public class PropertyUtils {

	private PropertyUtils() {
	}

	public static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)\\}");

	public static Map<String, String> extractPropertiesForCurrentMachine(Collection<String> systemProperties,
					Collection<? extends Property> userProperties) {

		Map<String, String> resolved = new HashMap<>();

		if (systemProperties != null) {
			for (String sysProp : systemProperties) {
				resolved.put(sysProp, trySystemProperty(sysProp, true));
			}
		}

		if (userProperties != null) {
			for (Property prop : userProperties) {
				// First resolve non os-specific, so the latter can override
				if (prop.getOs() != null)
					continue;

				resolved.put(prop.getKey(), prop.getValue());
			}

			for (Property prop : userProperties) {
				// Overrides any non os-specific property
				if (prop.getOs() != OS.CURRENT)
					continue;

				resolved.put(prop.getKey(), prop.getValue());
			}
		}

		return resolved;
	}

	/*
	 * https://stackoverflow.com/a/1347594
	 */
	public static Map<String, String> resolveDependencies(Map<String, String> properties) {
		Map<String, String> noDeps = new HashMap<>();

		while (properties.size() > 0) {
			int noDepsSize = noDeps.size();
			List<Map.Entry<String, String>> found = new ArrayList<>();

			Iterator<Map.Entry<String, String>> iter = properties.entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry<String, String> entry = iter.next();
				if (!PLACEHOLDER.matcher(entry.getValue()).find()) {
					iter.remove();
					found.add(entry);
					noDeps.put(entry.getKey(), entry.getValue());
				}
			}

			if (noDepsSize == noDeps.size()) { // No changes

				boolean foundSystem = false;
				String key = null;

				for (String val : properties.values()) {

					Matcher match = PLACEHOLDER.matcher(val);
					match.find();
					key = match.group(1);

					if (!properties.containsKey(key)) {
						String sys = trySystemProperty(key);

						noDeps.put(key, sys);
						found.add(Map.entry(key, sys));

						foundSystem = true;
					}
				}

				if (!foundSystem) {
					throw new IllegalStateException("Cyclic property detected: " + key);
				}
			}

			for (Map.Entry<String, String> entry : properties.entrySet()) {
				for (Map.Entry<String, String> f : found) {
					entry.setValue(entry.getValue().replace(wrap(f.getKey()), f.getValue()));
				}
			}
		}

		return noDeps;
	}

	/*
	 * Resolves first system property, falls back to environment variable.
	 */
	public static String trySystemProperty(String key, boolean systemInError) {
		String value = System.getProperty(key, System.getenv(key));
		if (value != null) {
			if (PLACEHOLDER.matcher(value).find()) {
				throw new IllegalStateException("System properties must not contain placeholders.");
			}

			return value;
		} else {
			throw new IllegalArgumentException(
							"Could not resolve " + (systemInError ? "system " : "") + "property '" + key + "'");
		}
	}

	public static String trySystemProperty(String key) {
		return trySystemProperty(key, false);
	}

	public static String wrap(String key) {
		return "${" + key + "}";
	}
	
	public static boolean containsPlaceholder(String str) {
		return PLACEHOLDER.matcher(str).find();
	}

}
