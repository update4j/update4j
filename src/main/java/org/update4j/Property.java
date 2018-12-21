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

import java.util.Objects;

/**
 * This class is a simple POJO that represents a property in the configuration
 * file.
 * 
 * @author Mordechai Meisels
 *
 */
public class Property {

	private String key;
	private String value;
	private OS os;

	/**
	 * Constructs a new property with the provided key, value, and operating system
	 * values.
	 * 
	 * <p>
	 * The key and value must not be {@code null} and the key must not contain the
	 * any of the characters <code>$</code>, <code>{</code> or <code>}</code>.
	 * 
	 * @param key
	 *            The property key.
	 * @param value
	 *            The property value.
	 * @param os
	 *            The operating system of this property.
	 */
	public Property(String key, String value, OS os) {
		if (key.isEmpty())
			throw new IllegalArgumentException("Key must not be empty.");

		if (key.contains("$")) {
			throw new IllegalArgumentException("Key contains illegal character '$': " + key);
		}

		if (key.contains("{")) {
			throw new IllegalArgumentException("Key contains illegal character '{': " + key);
		}

		if (key.contains("}")) {
			throw new IllegalArgumentException("Key contains illegal character '}': " + key);
		}

		this.key = key;
		this.value = Objects.requireNonNull(value);
		this.os = os;
	}

	/**
	 * Constructs a new property with the provided key and value.
	 * 
	 * <p>
	 * The key and value must not be {@code null} and the key must not contain the
	 * any of the characters <code>$</code>, <code>{</code> or <code>}</code>.
	 * 
	 * @param key
	 *            The property key.
	 * @param value
	 *            The property value.
	 */
	public Property(String key, String value) {
		this(key, value, null);
	}

	/**
	 * Returns the property key, never {@code null}.
	 * 
	 * @return The property key.
	 */
	public String getKey() {
		return key;
	}

	/**
	 * Returns the property value, never {@code null}.
	 * 
	 * @return The property value.
	 */
	public String getValue() {
		return value;
	}

	/**
	 * Returns the property operating system.
	 * 
	 * @return The property operating system.
	 */
	public OS getOs() {
		return os;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((key == null) ? 0 : key.hashCode());
		result = prime * result + ((os == null) ? 0 : os.hashCode());
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Property other = (Property) obj;
		if (key == null) {
			if (other.key != null)
				return false;
		} else if (!key.equals(other.key))
			return false;
		if (os != other.os)
			return false;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		return true;
	}
}
