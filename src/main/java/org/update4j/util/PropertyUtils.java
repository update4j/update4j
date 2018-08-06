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
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.update4j.PlaceholderMatchType;
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

	/**
	 * ignoreForeignProperty will not throw an exception if the key is found in an
	 * unresolved foreign property.
	 */
	public static String resolvePlaceholders(Map<String, String> resolvedProperties,
					Collection<? extends Property> properties, String str, boolean isPath,
					boolean ignoreForeignProperty) {
		if (str == null) {
			return null;
		}

		Matcher match = PLACEHOLDER.matcher(str);

		while (match.find()) {
			String key = match.group(1);
			String value = resolvedProperties.get(key);

			if (value == null) {
				Property p = getUserProperty(properties, key);
				if (p != null && p.getOs() != null && p.getOs() != OS.CURRENT && ignoreForeignProperty) {
					continue;
				}

				value = trySystemProperty(key);
				resolvedProperties.put(key, value);
			}

			str = str.replace(wrap(key), value);
		}
		
		if(isPath)
			str = str.replace("\\", "/");

		return str;
	}

	public static Property getUserProperty(Collection<? extends Property> properties, String key) {
		return properties.stream().filter(p -> key.equals(p.getKey())).findAny().orElse(null);
	}

	public static List<Property> getUserProperties(Collection<? extends Property> properties, String key) {
		return properties.stream().filter(p -> key.equals(p.getKey())).collect(Collectors.toList());
	}

	public static String getUserPropertyForCurrent(Collection<? extends Property> properties, String key) {
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

	public static String implyPlaceholders(Map<String, String> resolvedProperties, String str,
					PlaceholderMatchType matchType, boolean isPath) {
		if (str == null) {
			return null;
		}

		Objects.requireNonNull(matchType);

		if (isPath) {
			str = str.replace("\\", "/");
		}

		if (matchType == PlaceholderMatchType.NONE) {
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

		if (matchType == PlaceholderMatchType.FULL_MATCH) {
			for (Map.Entry<String, String> e : resolved) {
				if (str.equals(e.getValue())) {
					return wrap(e.getKey());
				}
			}

			return str;
		}

		/*
		 * https://stackoverflow.com/a/34464459/1751640
		 * 
		 * This regex will not replace characters inside an existing placeholder.
		 */
		if (matchType == PlaceholderMatchType.EVERY_OCCURRENCE) {
			for (Map.Entry<String, String> e : resolved) {
				String pattern = "(?<!\\$\\{[^{}]{0,500})" + Pattern.quote(e.getValue());

				str = str.replaceAll(pattern, Matcher.quoteReplacement(wrap(e.getKey())));
			}

			return str;
		}

		if (matchType == PlaceholderMatchType.WHOLE_WORD) {
			for (Map.Entry<String, String> e : resolved) {
				String pattern = "(?<!\\$\\{[^{}]{0,500})\\b" + Pattern.quote(e.getValue()) + "\\b";
				str = str.replaceAll(pattern, Matcher.quoteReplacement(wrap(e.getKey())));
			}

			return str;
		}

		// In case we rename this enum, lets stay safe the IDE will automatically fix this
		throw new UnsupportedOperationException("Unknown " + PlaceholderMatchType.class.getSimpleName());
	}

	public static String wrap(String key) {
		return "${" + key + "}";
	}

	public static boolean containsPlaceholder(String str) {
		return PLACEHOLDER.matcher(str).find();
	}

}
