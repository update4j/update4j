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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.update4j.OS;
import org.update4j.PlaceholderMatchType;
import org.update4j.Property;

public class PropertyManager {

	public static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)\\}");

	private List<Property> properties;
	private List<Property> unmodifiableProperties;

	private Map<String, String> resolvedProperties;
	private Map<String, String> unmodifiableResolvedProperties;

	public PropertyManager(List<Property> properties, Map<String, String> moreProperties,
					List<String> systemProperties) {

		this.properties = properties == null ? new ArrayList<>() : properties;
		this.unmodifiableProperties = Collections.unmodifiableList(this.properties);

		for (int i = 1; i < this.properties.size(); i++) {
			for (int j = 0; j < i; j++) {
				Property ip = properties.get(i);
				Property jp = properties.get(j);

				if (ip.getKey().equals(jp.getKey()) && ip.getOs() == jp.getOs()) {
					throw new IllegalArgumentException("Duplicate property: " + ip.getKey());
				}
			}
		}

		if (systemProperties != null) {
			for (int i = 1; i < systemProperties.size(); i++) {
				for (int j = 0; j < i; j++) {
					if (systemProperties.get(i).equals(systemProperties.get(j)))
						throw new IllegalArgumentException("Duplicate system property: " + systemProperties.get(i));
				}
			}
		}

		resolvedProperties = extractPropertiesForCurrentMachine(this.properties, systemProperties);

		if (moreProperties != null)
			resolvedProperties.putAll(moreProperties);

		resolvedProperties = resolveDependencies(resolvedProperties);
		unmodifiableResolvedProperties = Collections.unmodifiableMap(resolvedProperties);
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
		return unmodifiableProperties;
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
		return getProperties().stream().filter(p -> p.getKey().equals(key)).collect(Collectors.toList());
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
		return unmodifiableResolvedProperties;
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
		return resolvedProperties.get(key);
	}
	
	/**
	 * Returns a string where all placeholders are replaced with the real values.
	 * 
	 * <p>
	 * If it includes a reference to a foreign property that could not be resolved
	 * (as if that property refers to a system dependent system property), the
	 * placeholder will not be replaced.
	 * 
	 * @param str
	 *            The source string to try to resolve.
	 * @return The resolved string.
	 * @throws IllegalArgumentException
	 *             if the source string contains a placeholder that could not be
	 *             resolved.
	 */
	public String resolvePlaceholders(String str) {
		return resolvePlaceholders(str, false);
	}

	public String resolvePlaceholders(String str, boolean isPath) {
		return resolvePlaceholders(str, isPath, false);
	}

	/**
	 * ignoreForeignProperty will not throw an exception if the key is found in an
	 * unresolved foreign property.
	 */
	public String resolvePlaceholders(String str, boolean isPath, boolean ignoreForeignProperty) {
		if (str == null) {
			return null;
		}

		Matcher match = PLACEHOLDER.matcher(str);

		while (match.find()) {
			String key = match.group(1);
			String value = resolvedProperties.get(key);

			if (value == null) {
				Property prop = getProperties(key).stream().findAny().orElse(null);

				if (prop != null && prop.getOs() != null && prop.getOs() != OS.CURRENT && ignoreForeignProperty) {
					continue;
				}

				value = trySystemProperty(key);
				resolvedProperties.put(key, value);
			}

			str = str.replace(wrap(key), value);
		}

		if (isPath)
			str = str.replace("\\", "/");

		return str;
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
						.filter(e -> !e.getValue().isEmpty())
						.sorted((e1, e2) -> e2.getValue().length() - e1.getValue().length())
						.peek(e -> {
							if (isPath) {
								e.setValue(e.getValue().replace("\\", "/"));
							}
						})
						.collect(Collectors.toList());

		for (Map.Entry<String, String> e : resolved) {
			if (str.equals(e.getValue())) {
				return wrap(e.getKey());
			}
		}

		// should've matched in for-loop above
		if (matchType == PlaceholderMatchType.FULL_MATCH) {
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

	private static Map<String, String> extractPropertiesForCurrentMachine(Collection<? extends Property> properties,
					Collection<String> systemProperties) {

		Map<String, String> resolved = new HashMap<>();

		if (systemProperties != null) {
			for (String sysProp : systemProperties) {
				resolved.put(sysProp, trySystemProperty(sysProp, true));
			}
		}

		if (properties != null) {
			for (Property prop : properties) {
				// First resolve non os-specific, so the latter can override
				if (prop.getOs() != null)
					continue;

				resolved.put(prop.getKey(), prop.getValue());
			}

			for (Property prop : properties) {
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
	private static Map<String, String> resolveDependencies(Map<String, String> properties) {
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
	private static String trySystemProperty(String key, boolean systemInError) {
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

	private static String trySystemProperty(String key) {
		return trySystemProperty(key, false);
	}

	public static String wrap(String key) {
		return "${" + key + "}";
	}

	public static boolean containsPlaceholder(String str) {
		return PLACEHOLDER.matcher(str).find();
	}
}
