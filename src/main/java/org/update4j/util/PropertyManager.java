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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.update4j.PlaceholderMatchType;
import org.update4j.Property;

public class PropertyManager {

	private List<Property> properties;
	private List<Property> unmodifiableProperties;

	private Map<String, String> resolvedProperties;
	private Map<String, String> unmodifiableResolvedProperties;

	public PropertyManager(List<Property> properties, List<String> systemProperties) {
		this.properties = properties == null ? new ArrayList<>() : properties;
		this.unmodifiableProperties = Collections.unmodifiableList(this.properties);
		
		List<String> sysProperties = systemProperties == null ? new ArrayList<>() : systemProperties;

		resolvedProperties = PropertyUtils.extractPropertiesForCurrentMachine(sysProperties, this.properties);
		resolvedProperties = PropertyUtils.resolveDependencies(resolvedProperties);
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
	public List<Property> getUserProperties() {
		return unmodifiableProperties;
	}

	/**
	 * Returns the {@link Property} with the corresponding key, or {@code null} if
	 * missing. If there are more than one property with the given key (if they are
	 * platform specific), only one will be returned.
	 * 
	 * @param key
	 *            The key of the property.
	 * @return The {@link Property} with the given key.
	 */
	public Property getUserProperty(String key) {
		return PropertyUtils.getUserProperty(properties, key);
	}

	/**
	 * Returns a list of properties with the corresponding key, or empty if non are
	 * found. There might be more than one property with the given key, if they are
	 * platform specific.
	 * 
	 * @param key
	 *            The key of the property.
	 * @return A list of properties with the given key.
	 */
	public List<Property> getUserProperties(String key) {
		return PropertyUtils.getUserProperties(properties, key);
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
	public String getUserPropertyForCurrent(String key) {
		return PropertyUtils.getUserPropertyForCurrent(properties, key);
	}

	/**
	 * Returns an unmodifiable map of keys and values after resolving the
	 * placeholders. This will not include properties marked for foreign operating
	 * systems.
	 * 
	 * @return A map of the keys and real values of the properties, after resolving
	 *         the placeholders.
	 */
	public Map<String, String> getResolvedProperties() {
		return unmodifiableResolvedProperties;
	}

	/**
	 * Returns the real value of the property with the given key, after resolving
	 * the placeholders.
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

	/*
	 * ignoreForeignProperty will not throw an exception if the key is found in an
	 * unresolved foreign property.
	 */
	public String resolvePlaceholders(String str, boolean isPath, boolean ignoreForeignProperty) {
		return PropertyUtils.resolvePlaceholders(resolvedProperties, properties, str, isPath, ignoreForeignProperty);
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
		return PropertyUtils.implyPlaceholders(resolvedProperties, str, matchType, isPath);
	}

}
